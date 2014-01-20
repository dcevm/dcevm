/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/verifier.hpp"
#include "code/codeCache.hpp"
#include "interpreter/oopMapCache.hpp"
#include "interpreter/rewriter.hpp"
#include "memory/gcLocker.hpp"
#include "memory/universe.inline.hpp"
#include "oops/fieldStreams.hpp"
#include "oops/klassVtable.hpp"
#include "prims/jvmtiImpl.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "prims/methodComparator.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/relocator.hpp"
#include "utilities/bitMap.inline.hpp"
#include "prims/jvmtiClassFileReconstituter.hpp"
#include "compiler/compileBroker.hpp"
#include "oops/instanceMirrorKlass.hpp"


objArrayOop VM_RedefineClasses::_old_methods = NULL;
objArrayOop VM_RedefineClasses::_new_methods = NULL;
int*        VM_RedefineClasses::_matching_old_methods = NULL;
int*        VM_RedefineClasses::_matching_new_methods = NULL;
int*        VM_RedefineClasses::_deleted_methods      = NULL;
int*        VM_RedefineClasses::_added_methods        = NULL;
int         VM_RedefineClasses::_matching_methods_length = 0;
int         VM_RedefineClasses::_deleted_methods_length  = 0;
int         VM_RedefineClasses::_added_methods_length    = 0;
GrowableArray<instanceKlassHandle>* VM_RedefineClasses::_affected_klasses = NULL;


// Holds the revision number of the current class redefinition
int    VM_RedefineClasses::_revision_number = -1;

VM_RedefineClasses::VM_RedefineClasses(jint class_count, const jvmtiClassDefinition *class_defs, JvmtiClassLoadKind class_load_kind)
   : VM_GC_Operation(Universe::heap()->total_full_collections(), GCCause::_heap_inspection) {
  RC_TIMER_START(_timer_total);
  _class_count = class_count;
  _class_defs = class_defs;
  _class_load_kind = class_load_kind;
  _result = JVMTI_ERROR_NONE;
}

VM_RedefineClasses::~VM_RedefineClasses() {
  unlock_threads();
  RC_TIMER_STOP(_timer_total);

  if (TimeRedefineClasses) {
    tty->print_cr("");
    tty->print_cr("Timing Prologue:             %d", _timer_prologue.milliseconds());
    tty->print_cr("Timing Class Loading:        %d", _timer_class_loading.milliseconds());
    tty->print_cr("Timing Waiting for Lock:     %d", _timer_wait_for_locks.milliseconds());
    tty->print_cr("Timing Class Linking:        %d", _timer_class_linking.milliseconds());
    tty->print_cr("Timing Prepare Redefinition: %d", _timer_prepare_redefinition.milliseconds());
    tty->print_cr("Timing Heap Iteration:       %d", _timer_heap_iteration.milliseconds());
    tty->print_cr("Timing Redefinition GC:      %d", _timer_redefinition.milliseconds());
    tty->print_cr("Timing Epilogue:             %d", _timer_vm_op_epilogue.milliseconds());
    tty->print_cr("------------------------------------------------------------------");
    tty->print_cr("Total Time:                  %d", _timer_total.milliseconds());
    tty->print_cr("");
  }
}

void VM_RedefineClasses::swap_all_method_annotations(int i, int j, instanceKlassHandle scratch_class) {
  typeArrayOop save;

  save = scratch_class->get_method_annotations_of(i);
  scratch_class->set_method_annotations_of(i, scratch_class->get_method_annotations_of(j));
  scratch_class->set_method_annotations_of(j, save);

  save = scratch_class->get_method_parameter_annotations_of(i);
  scratch_class->set_method_parameter_annotations_of(i, scratch_class->get_method_parameter_annotations_of(j));
  scratch_class->set_method_parameter_annotations_of(j, save);

  save = scratch_class->get_method_default_annotations_of(i);
  scratch_class->set_method_default_annotations_of(i, scratch_class->get_method_default_annotations_of(j));
  scratch_class->set_method_default_annotations_of(j, save);
}

void VM_RedefineClasses::add_affected_klasses( klassOop klass )
{
  assert(!_affected_klasses->contains(klass), "must not occur more than once!");
  assert(klass->klass_part()->new_version() == NULL, "Only last version is valid entry in system dictionary");

  Klass* k = klass->klass_part();

  if (k->check_redefinition_flag(Klass::MarkedAsAffected)) {
    _affected_klasses->append(klass);
    return;
  }

  for (juint i = 0; i < k->super_depth(); i++) {
    klassOop primary_oop = k->primary_super_of_depth(i);
    // super_depth returns "8" for interfaces, but they don't have primaries other than Object.
    if (primary_oop == NULL) break;
    Klass* primary = Klass::cast(primary_oop);
    if (primary->check_redefinition_flag(Klass::MarkedAsAffected)) {
      TRACE_RC3("Found affected class: %s", k->name()->as_C_string());
      k->set_redefinition_flag(Klass::MarkedAsAffected);
      _affected_klasses->append(klass);
      return;
    }
  }

  // Check secondary supers
  int cnt = k->secondary_supers()->length();
  for (int i = 0; i < cnt; i++) {
    Klass* secondary = Klass::cast((klassOop) k->secondary_supers()->obj_at(i));
    if (secondary->check_redefinition_flag(Klass::MarkedAsAffected)) {
      TRACE_RC3("Found affected class: %s", k->name()->as_C_string());
      k->set_redefinition_flag(Klass::MarkedAsAffected);
      _affected_klasses->append(klass);
      return;
    }
  }
}


// Searches for all affected classes and performs a sorting such that a supertype is always before a subtype.
jvmtiError VM_RedefineClasses::find_sorted_affected_classes() {

  assert(_affected_klasses, "");
  for (int i = 0; i < _class_count; i++) {
    oop mirror = JNIHandles::resolve_non_null(_class_defs[i].klass);
    instanceKlassHandle klass_handle(Thread::current(), java_lang_Class::as_klassOop(mirror));
    klass_handle->set_redefinition_flag(Klass::MarkedAsAffected);
    assert(klass_handle->new_version() == NULL, "Must be new class");
  }

  // Find classes not directly redefined, but affected by a redefinition (because one of its supertypes is redefined)
  SystemDictionary::classes_do(VM_RedefineClasses::add_affected_klasses);
  TRACE_RC1("%d classes affected", _affected_klasses->length());

  // Sort the affected klasses such that a supertype is always on a smaller array index than its subtype.
  jvmtiError result = do_topological_class_sorting(_class_defs, _class_count, Thread::current());
  IF_TRACE_RC2 {
    TRACE_RC2("Redefine order: ");  
    for (int i = 0; i < _affected_klasses->length(); i++) {
      TRACE_RC2("%s", _affected_klasses->at(i)->name()->as_C_string());
    }
  }

  return result;
}

// Searches for the class bytes of the given class and returns them as a byte array.
jvmtiError VM_RedefineClasses::find_class_bytes(instanceKlassHandle the_class, const unsigned char **class_bytes, jint *class_byte_count, jboolean *not_changed) {

  *not_changed = false;

  // Search for the index in the redefinition array that corresponds to the current class
  int j;
  for (j=0; j<_class_count; j++) {
    oop mirror = JNIHandles::resolve_non_null(_class_defs[j].klass);
    klassOop the_class_oop = java_lang_Class::as_klassOop(mirror);
    if (the_class_oop == the_class()) {
      break;
    }
  }

  if (j == _class_count) {

    *not_changed = true;

    // Redefine with same bytecodes. This is a class that is only indirectly affected by redefinition,
    // so the user did not specify a different bytecode for that class.

    if (the_class->get_cached_class_file_bytes() == NULL) {
      // not cached, we need to reconstitute the class file from VM representation
      constantPoolHandle  constants(Thread::current(), the_class->constants());
      ObjectLocker ol(constants, Thread::current());    // lock constant pool while we query it

      JvmtiClassFileReconstituter reconstituter(the_class);
      if (reconstituter.get_error() != JVMTI_ERROR_NONE) {
        return reconstituter.get_error();
      }

      *class_byte_count = (jint)reconstituter.class_file_size();
      *class_bytes      = (unsigned char*)reconstituter.class_file_bytes();

      TRACE_RC3("Reconstituted class bytes");

    } else {

      // it is cached, get it from the cache
      *class_byte_count = the_class->get_cached_class_file_len();
      *class_bytes      = the_class->get_cached_class_file_bytes();


      TRACE_RC3("Retrieved cached class bytes");
    }

  } else {

    // Redefine with bytecodes at index j
    *class_bytes = _class_defs[j].class_bytes;
    *class_byte_count = _class_defs[j].class_byte_count;
  }

  return JVMTI_ERROR_NONE;
}

// Prologue of the VM operation, called on the Java thread in parallel to normal program execution
bool VM_RedefineClasses::doit_prologue() {

  _revision_number++;
  TRACE_RC1("Redefinition with revision number %d started!", _revision_number);
  lock_threads();

  assert(Thread::current()->is_Java_thread(), "must be Java thread");
  RC_TIMER_START(_timer_prologue);

  if (!check_arguments()) {
    RC_TIMER_STOP(_timer_prologue);
    return false;
  }

  // We first load new class versions in the prologue, because somewhere down the
  // call chain it is required that the current thread is a Java thread.
  _new_classes = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<instanceKlassHandle>(5, true);

  assert(_affected_klasses == NULL, "");
  _affected_klasses = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<instanceKlassHandle>(_class_count, true);


  _result = load_new_class_versions(Thread::current());

  TRACE_RC1("Loaded new class versions!");
  if (_result != JVMTI_ERROR_NONE) {
    TRACE_RC1("error occured: %d!", _result);
    delete _new_classes;
    _new_classes = NULL;
    delete _affected_klasses;
    _affected_klasses = NULL;
    RC_TIMER_STOP(_timer_prologue);
    return false;
  }

  TRACE_RC2("nearly finished");
  VM_GC_Operation::doit_prologue();
  RC_TIMER_STOP(_timer_prologue);
  TRACE_RC2("doit_prologue finished!");
  return true;
}

// Checks basic properties of the arguments of the redefinition command.
jvmtiError VM_RedefineClasses::check_arguments_error() {
  if (_class_defs == NULL) return JVMTI_ERROR_NULL_POINTER;
  for (int i = 0; i < _class_count; i++) {
    if (_class_defs[i].klass == NULL) return JVMTI_ERROR_INVALID_CLASS;
    if (_class_defs[i].class_byte_count == 0) return JVMTI_ERROR_INVALID_CLASS_FORMAT;
    if (_class_defs[i].class_bytes == NULL) return JVMTI_ERROR_NULL_POINTER;
  }
  return JVMTI_ERROR_NONE;
}

// Returns false and sets an result error code if the redefinition should be aborted.
bool VM_RedefineClasses::check_arguments() {
  jvmtiError error = check_arguments_error();
  if (error != JVMTI_ERROR_NONE || _class_count == 0) {
    _result = error;
    return false;
  }
  return true;
}

jvmtiError VM_RedefineClasses::check_exception() const {
  Thread* THREAD = Thread::current();
  if (HAS_PENDING_EXCEPTION) {

    Symbol* ex_name = PENDING_EXCEPTION->klass()->klass_part()->name();
    TRACE_RC1("parse_stream exception: '%s'", ex_name->as_C_string());      
    if (TraceRedefineClasses >= 1) {
      java_lang_Throwable::print(PENDING_EXCEPTION, tty);
      tty->print_cr("");
    }
    CLEAR_PENDING_EXCEPTION;

    if (ex_name == vmSymbols::java_lang_UnsupportedClassVersionError()) {
      return JVMTI_ERROR_UNSUPPORTED_VERSION;
    } else if (ex_name == vmSymbols::java_lang_ClassFormatError()) {
      return JVMTI_ERROR_INVALID_CLASS_FORMAT;
    } else if (ex_name == vmSymbols::java_lang_ClassCircularityError()) {
      return JVMTI_ERROR_CIRCULAR_CLASS_DEFINITION;
    } else if (ex_name == vmSymbols::java_lang_NoClassDefFoundError()) {
      // The message will be "XXX (wrong name: YYY)"
      return JVMTI_ERROR_NAMES_DONT_MATCH;
    } else if (ex_name == vmSymbols::java_lang_OutOfMemoryError()) {
      return JVMTI_ERROR_OUT_OF_MEMORY;
    } else {
      // Just in case more exceptions can be thrown..
      return JVMTI_ERROR_FAILS_VERIFICATION;
    }
  }

  return JVMTI_ERROR_NONE;
}

// Loads all new class versions and stores the instanceKlass handles in an array.
jvmtiError VM_RedefineClasses::load_new_class_versions(TRAPS) {

  ResourceMark rm(THREAD);

  TRACE_RC1("===================================================================");
  TRACE_RC1("redefinition started by thread \"%s\"", THREAD->name());
  TRACE_RC1("load new class versions (%d)", _class_count);

  // Retrieve an array of all classes that need to be redefined
  jvmtiError err = find_sorted_affected_classes();
  if (err != JVMTI_ERROR_NONE) {
    TRACE_RC1("Error finding sorted affected classes: %d", (int)err);
    return err;
  }


  JvmtiThreadState *state = JvmtiThreadState::state_for(JavaThread::current());

  _max_redefinition_flags = Klass::NoRedefinition;
  jvmtiError result = JVMTI_ERROR_NONE;

  for (int i=0; i<_affected_klasses->length(); i++) {
    TRACE_RC2("Processing affected class %d of %d", i+1, _affected_klasses->length());

    instanceKlassHandle the_class = _affected_klasses->at(i);
    TRACE_RC2("name=%s", the_class->name()->as_C_string());

    the_class->link_class(THREAD);
    result = check_exception();
    if (result != JVMTI_ERROR_NONE) break;

    // Find new class bytes
    const unsigned char* class_bytes;
    jint class_byte_count;
    jvmtiError error;
    jboolean not_changed;
    if ((error = find_class_bytes(the_class, &class_bytes, &class_byte_count, &not_changed)) != JVMTI_ERROR_NONE) {
      TRACE_RC1("Error finding class bytes: %d", (int)error);
      result = error;
      break;
    }
    assert(class_bytes != NULL && class_byte_count != 0, "Class bytes defined at this point!");


    // Set redefined class handle in JvmtiThreadState class.
    // This redefined class is sent to agent event handler for class file
    // load hook event.
    state->set_class_being_redefined(&the_class, _class_load_kind);

    TRACE_RC2("Before resolving from stream");

    RC_TIMER_STOP(_timer_prologue);
    RC_TIMER_START(_timer_class_loading);


    // Parse the stream.
    Handle the_class_loader(THREAD, the_class->class_loader());
    Handle protection_domain(THREAD, the_class->protection_domain());
    ClassFileStream st((u1*) class_bytes, class_byte_count, (char *)"__VM_RedefineClasses__");
    instanceKlassHandle new_class(THREAD, SystemDictionary::resolve_from_stream(the_class->name(),
      the_class_loader,
      protection_domain,
      &st,
      true,
      the_class,
      THREAD));

    RC_TIMER_STOP(_timer_class_loading);
    RC_TIMER_START(_timer_prologue);

    TRACE_RC2("After resolving class from stream!");
    // Clear class_being_redefined just to be sure.
    state->clear_class_being_redefined();

    result = check_exception();
    if (result != JVMTI_ERROR_NONE) break;

    not_changed = false;

#ifdef ASSERT

    assert(new_class() != NULL, "Class could not be loaded!");
    assert(new_class() != the_class(), "must be different");
    assert(new_class->new_version() == NULL && new_class->old_version() != NULL, "");


    objArrayOop k_interfaces = new_class->local_interfaces();
    for (int j=0; j<k_interfaces->length(); j++) {
      assert(((klassOop)k_interfaces->obj_at(j))->klass_part()->is_newest_version(), "just checking");
    }

    if (!THREAD->is_Compiler_thread()) {

      TRACE_RC2("name=%s loader="INTPTR_FORMAT" protection_domain="INTPTR_FORMAT, the_class->name()->as_C_string(), the_class->class_loader(), the_class->protection_domain());
      // If we are on the compiler thread, we must not try to resolve a class.
      klassOop systemLookup = SystemDictionary::resolve_or_null(the_class->name(), the_class->class_loader(), the_class->protection_domain(), THREAD);
      
      if (systemLookup != NULL) {
        assert(systemLookup == new_class->old_version(), "Old class must be in system dictionary!");
      

        Klass *subklass = new_class()->klass_part()->subklass();
        while (subklass != NULL) {
          assert(subklass->new_version() == NULL, "Most recent version of class!");
          subklass = subklass->next_sibling();
        }
      } else {
        // This can happen for reflection generated classes.. ?
        CLEAR_PENDING_EXCEPTION;
      }
    }

#endif

    IF_TRACE_RC1 {
      if (new_class->layout_helper() != the_class->layout_helper()) {
        TRACE_RC1("Instance size change for class %s: new=%d old=%d", new_class->name()->as_C_string(), new_class->layout_helper(), the_class->layout_helper());
      }
    }

    // Set the new version of the class
    new_class->set_revision_number(_revision_number);
    new_class->set_redefinition_index(i);
    the_class->set_new_version(new_class());
    _new_classes->append(new_class);

    assert(new_class->new_version() == NULL, "");

    int redefinition_flags = Klass::NoRedefinition;

    if (not_changed) {
      redefinition_flags = Klass::NoRedefinition;
    } else if (AllowAdvancedClassRedefinition) {
      redefinition_flags = calculate_redefinition_flags(new_class);
      if (redefinition_flags >= Klass::RemoveSuperType) {
        TRACE_RC1("Remove super type is not allowed");
        result = JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED;
        break;
      }
    } else {
      jvmtiError allowed = check_redefinition_allowed(new_class);
      if (allowed != JVMTI_ERROR_NONE) {
        TRACE_RC1("Error redefinition not allowed!");
        result = allowed;
        break;
      }
      redefinition_flags = Klass::ModifyClass;
    }

    if (new_class->super() != NULL) {
      redefinition_flags = redefinition_flags | new_class->super()->klass_part()->redefinition_flags();
    }

    for (int j=0; j<new_class->local_interfaces()->length(); j++) {
      redefinition_flags = redefinition_flags | ((klassOop)new_class->local_interfaces()->obj_at(j))->klass_part()->redefinition_flags();
    }

    new_class->set_redefinition_flags(redefinition_flags);

    _max_redefinition_flags = _max_redefinition_flags | redefinition_flags;

    if ((redefinition_flags & Klass::ModifyInstances) != 0) {
      // TODO: Check if watch access flags of static fields are updated correctly.
      calculate_instance_update_information(_new_classes->at(i)());
    } else {
      // Fields were not changed, transfer special flags only
      assert(new_class->layout_helper() >> 1 == new_class->old_version()->klass_part()->layout_helper() >> 1, "must be equal");
      assert(new_class->fields()->length() == ((instanceKlass*)new_class->old_version()->klass_part())->fields()->length(), "must be equal");
      
      JavaFieldStream old_fs(the_class);
      JavaFieldStream new_fs(new_class);
      for (; !old_fs.done() && !new_fs.done(); old_fs.next(), new_fs.next()) {
        AccessFlags flags = new_fs.access_flags();
        flags.set_is_field_modification_watched(old_fs.access_flags().is_field_modification_watched());
        flags.set_is_field_access_watched(old_fs.access_flags().is_field_access_watched());
        new_fs.set_access_flags(flags);
      }
    }

    IF_TRACE_RC3 {
      if (new_class->super() != NULL) {
        TRACE_RC3("Super class is %s", new_class->super()->klass_part()->name()->as_C_string());
      }
    }

#ifdef ASSERT
    assert(new_class->super() == NULL || new_class->super()->klass_part()->new_version() == NULL, "Super klass must be newest version!");

    the_class->vtable()->verify(tty);
    new_class->vtable()->verify(tty);
#endif

    TRACE_RC2("Verification done!");

    if (i == _affected_klasses->length() - 1) {

      // This was the last class processed => check if additional classes have been loaded in the meantime

      for (int j=0; j<_affected_klasses->length(); j++) {

        klassOop initial_klass = _affected_klasses->at(j)();
        Klass *initial_subklass = initial_klass->klass_part()->subklass();
        Klass *cur_klass = initial_subklass;
        while(cur_klass != NULL) {

          if(cur_klass->oop_is_instance() && cur_klass->is_newest_version() && !cur_klass->is_redefining()) {
            instanceKlassHandle handle(THREAD, cur_klass->as_klassOop());
            if (!_affected_klasses->contains(handle)) {

              int k = i + 1;
              for (; k<_affected_klasses->length(); k++) {
                if (_affected_klasses->at(k)->is_subtype_of(cur_klass->as_klassOop())) {
                  break;
                }
              }
              _affected_klasses->insert_before(k, handle);
              TRACE_RC2("Adding newly loaded class to affected classes: %s", cur_klass->name()->as_C_string());
            }
          }

          cur_klass = cur_klass->next_sibling();
        }
      }

      int new_count = _affected_klasses->length() - 1 - i;
      if (new_count != 0) {

        TRACE_RC1("Found new number of affected classes: %d", new_count);
      }
    }
  }

  if (result != JVMTI_ERROR_NONE) {
    rollback();
    return result;
  }

  RC_TIMER_STOP(_timer_prologue);
  RC_TIMER_START(_timer_class_linking);
  // Link and verify new classes _after_ all classes have been updated in the system dictionary!
  for (int i=0; i<_affected_klasses->length(); i++) {
    instanceKlassHandle the_class = _affected_klasses->at(i);
    instanceKlassHandle new_class(the_class->new_version());

    TRACE_RC2("Linking class %d/%d %s", i, _affected_klasses->length(), the_class->name()->as_C_string());
    new_class->link_class(THREAD);

    result = check_exception();
    if (result != JVMTI_ERROR_NONE) break;
  }
  RC_TIMER_STOP(_timer_class_linking);
  RC_TIMER_START(_timer_prologue);

  if (result != JVMTI_ERROR_NONE) {
    rollback();
    return result;
  }

  TRACE_RC2("All classes loaded!");

#ifdef ASSERT
  for (int i=0; i<_affected_klasses->length(); i++) {
    instanceKlassHandle the_class = _affected_klasses->at(i);
    assert(the_class->new_version() != NULL, "Must have been redefined");
    instanceKlassHandle new_version = instanceKlassHandle(THREAD, the_class->new_version());
    assert(new_version->new_version() == NULL, "Must be newest version");

    if (!(new_version->super() == NULL || new_version->super()->klass_part()->new_version() == NULL)) {
      new_version()->print();
      new_version->super()->print();
    }
    assert(new_version->super() == NULL || new_version->super()->klass_part()->new_version() == NULL, "Super class must be newest version");
  }

  SystemDictionary::classes_do(check_class, THREAD);

#endif

  TRACE_RC1("Finished verification!");
  return JVMTI_ERROR_NONE;
}

void VM_RedefineClasses::lock_threads() {

  RC_TIMER_START(_timer_wait_for_locks);


  JavaThread *javaThread = Threads::first();
  while (javaThread != NULL) {
    if (javaThread->is_Compiler_thread() && javaThread != Thread::current()) {
      CompilerThread *compilerThread = (CompilerThread *)javaThread;
      compilerThread->set_should_bailout(true);
    }
    javaThread = javaThread->next();
  }

  int cnt = 0;
  javaThread = Threads::first();
  while (javaThread != NULL) {
    if (javaThread->is_Compiler_thread() && javaThread != Thread::current()) {
      CompilerThread *compilerThread = (CompilerThread *)javaThread;
      compilerThread->compilation_mutex()->lock();
      cnt++;
    }
    javaThread = javaThread->next();
  }

  TRACE_RC2("Locked %d compiler threads", cnt);

  cnt = 0;
  javaThread = Threads::first();
  while (javaThread != NULL) {
    if (javaThread != Thread::current()) {
      javaThread->redefine_classes_mutex()->lock();
      cnt++;
    }
    javaThread = javaThread->next();
  }


  TRACE_RC2("Locked %d threads", cnt);

  RC_TIMER_STOP(_timer_wait_for_locks);
}

void VM_RedefineClasses::unlock_threads() {

  int cnt = 0;
  JavaThread *javaThread = Threads::first();
  Thread *thread = Thread::current();
  while (javaThread != NULL) {
    if (javaThread->is_Compiler_thread() && javaThread != Thread::current()) {
      CompilerThread *compilerThread = (CompilerThread *)javaThread;
      if (compilerThread->compilation_mutex()->owned_by_self()) {
        compilerThread->compilation_mutex()->unlock();
        cnt++;
      }
    }
    javaThread = javaThread->next();
  }

  TRACE_RC2("Unlocked %d compiler threads", cnt);

  cnt = 0;
  javaThread = Threads::first();
  while (javaThread != NULL) {
    if (javaThread != Thread::current()) {
      if (javaThread->redefine_classes_mutex()->owned_by_self()) {
        javaThread->redefine_classes_mutex()->unlock();
        cnt++;
      }
    }
    javaThread = javaThread->next();
  }

  TRACE_RC2("Unlocked %d threads", cnt);
}

jvmtiError VM_RedefineClasses::check_redefinition_allowed(instanceKlassHandle scratch_class) {


  
  // Compatibility mode => check for unsupported modification


  assert(scratch_class->old_version() != NULL, "must have old version");
  instanceKlassHandle the_class(scratch_class->old_version());

  int i;

  // Check superclasses, or rather their names, since superclasses themselves can be
  // requested to replace.
  // Check for NULL superclass first since this might be java.lang.Object
  if (the_class->super() != scratch_class->super() &&
    (the_class->super() == NULL || scratch_class->super() == NULL ||
    Klass::cast(the_class->super())->name() !=
    Klass::cast(scratch_class->super())->name())) {
      return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED;
  }

  // Check if the number, names and order of directly implemented interfaces are the same.
  // I think in principle we should just check if the sets of names of directly implemented
  // interfaces are the same, i.e. the order of declaration (which, however, if changed in the
  // .java file, also changes in .class file) should not matter. However, comparing sets is
  // technically a bit more difficult, and, more importantly, I am not sure at present that the
  // order of interfaces does not matter on the implementation level, i.e. that the VM does not
  // rely on it somewhere.
  objArrayOop k_interfaces = the_class->local_interfaces();
  objArrayOop k_new_interfaces = scratch_class->local_interfaces();
  int n_intfs = k_interfaces->length();
  if (n_intfs != k_new_interfaces->length()) {
    return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED;
  }
  for (i = 0; i < n_intfs; i++) {
    if (Klass::cast((klassOop) k_interfaces->obj_at(i))->name() !=
      Klass::cast((klassOop) k_new_interfaces->obj_at(i))->name()) {
        return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED;
    }
  }

  // Check whether class is in the error init state.
  if (the_class->is_in_error_state()) {
    // TBD #5057930: special error code is needed in 1.6
    return JVMTI_ERROR_INVALID_CLASS;
  }

  // Check whether class modifiers are the same.
  jushort old_flags = (jushort) the_class->access_flags().get_flags();
  jushort new_flags = (jushort) scratch_class->access_flags().get_flags();
  if (old_flags != new_flags) {
    return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED;
  }

  // Check if the number, names, types and order of fields declared in these classes
  // are the same.
  JavaFieldStream old_fs(the_class);
  JavaFieldStream new_fs(scratch_class);
  for (; !old_fs.done() && !new_fs.done(); old_fs.next(), new_fs.next()) {
    // access
    old_flags = old_fs.access_flags().as_short();
    new_flags = new_fs.access_flags().as_short();
    if ((old_flags ^ new_flags) & JVM_RECOGNIZED_FIELD_MODIFIERS) {
      return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED;
    }
    // offset
    if (old_fs.offset() != new_fs.offset()) {
      return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED;
    }
    // name and signature
    Symbol* name_sym1 = the_class->constants()->symbol_at(old_fs.name_index());
    Symbol* sig_sym1 = the_class->constants()->symbol_at(old_fs.signature_index());
    Symbol* name_sym2 = scratch_class->constants()->symbol_at(new_fs.name_index());
    Symbol* sig_sym2 = scratch_class->constants()->symbol_at(new_fs.signature_index());
    if (name_sym1 != name_sym2 || sig_sym1 != sig_sym2) {
      return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED;
    }
  }

  // If both streams aren't done then we have a differing number of
  // fields.
  if (!old_fs.done() || !new_fs.done()) {
    return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED;
  }

  // Do a parallel walk through the old and new methods. Detect
  // cases where they match (exist in both), have been added in
  // the new methods, or have been deleted (exist only in the
  // old methods).  The class file parser places methods in order
  // by method name, but does not order overloaded methods by
  // signature.  In order to determine what fate befell the methods,
  // this code places the overloaded new methods that have matching
  // old methods in the same order as the old methods and places
  // new overloaded methods at the end of overloaded methods of
  // that name. The code for this order normalization is adapted
  // from the algorithm used in instanceKlass::find_method().
  // Since we are swapping out of order entries as we find them,
  // we only have to search forward through the overloaded methods.
  // Methods which are added and have the same name as an existing
  // method (but different signature) will be put at the end of
  // the methods with that name, and the name mismatch code will
  // handle them.
  objArrayHandle k_old_methods(the_class->methods());
  objArrayHandle k_new_methods(scratch_class->methods());
  int n_old_methods = k_old_methods->length();
  int n_new_methods = k_new_methods->length();

  int ni = 0;
  int oi = 0;
  while (true) {
    methodOop k_old_method;
    methodOop k_new_method;
    enum { matched, added, deleted, undetermined } method_was = undetermined;

    if (oi >= n_old_methods) {
      if (ni >= n_new_methods) {
        break; // we've looked at everything, done
      }
      // New method at the end
      k_new_method = (methodOop) k_new_methods->obj_at(ni);
      method_was = added;
    } else if (ni >= n_new_methods) {
      // Old method, at the end, is deleted
      k_old_method = (methodOop) k_old_methods->obj_at(oi);
      method_was = deleted;
    } else {
      // There are more methods in both the old and new lists
      k_old_method = (methodOop) k_old_methods->obj_at(oi);
      k_new_method = (methodOop) k_new_methods->obj_at(ni);
      if (k_old_method->name() != k_new_method->name()) {
        // Methods are sorted by method name, so a mismatch means added
        // or deleted
        if (k_old_method->name()->fast_compare(k_new_method->name()) > 0) {
          method_was = added;
        } else {
          method_was = deleted;
        }
      } else if (k_old_method->signature() == k_new_method->signature()) {
        // Both the name and signature match
        method_was = matched;
      } else {
        // The name matches, but the signature doesn't, which means we have to
        // search forward through the new overloaded methods.
        int nj;  // outside the loop for post-loop check
        for (nj = ni + 1; nj < n_new_methods; nj++) {
          methodOop m = (methodOop)k_new_methods->obj_at(nj);
          if (k_old_method->name() != m->name()) {
            // reached another method name so no more overloaded methods
            method_was = deleted;
            break;
          }
          if (k_old_method->signature() == m->signature()) {
            // found a match so swap the methods
            k_new_methods->obj_at_put(ni, m);
            k_new_methods->obj_at_put(nj, k_new_method);
            k_new_method = m;
            method_was = matched;
            break;
          }
        }

        if (nj >= n_new_methods) {
          // reached the end without a match; so method was deleted
          method_was = deleted;
        }
      }
    }

    switch (method_was) {
    case matched:
      // methods match, be sure modifiers do too
      old_flags = (jushort) k_old_method->access_flags().get_flags();
      new_flags = (jushort) k_new_method->access_flags().get_flags();
      if ((old_flags ^ new_flags) & ~(JVM_ACC_NATIVE)) {
        return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED;
      }
      {
        u2 new_num = k_new_method->method_idnum();
        u2 old_num = k_old_method->method_idnum();
        if (new_num != old_num) {
          methodOop idnum_owner = scratch_class->method_with_idnum(old_num);
          if (idnum_owner != NULL) {
            // There is already a method assigned this idnum -- switch them
            idnum_owner->set_method_idnum(new_num);
          }
          k_new_method->set_method_idnum(old_num);
        }
      }
      // advance to next pair of methods
      ++oi;
      ++ni;
      break;
    case added:
      // method added, see if it is OK
      new_flags = (jushort) k_new_method->access_flags().get_flags();
      if ((new_flags & JVM_ACC_PRIVATE) == 0
        // hack: private should be treated as final, but alas
        || (new_flags & (JVM_ACC_FINAL|JVM_ACC_STATIC)) == 0
        ) {
          // new methods must be private
          return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED;
      }
      {
        u2 num = the_class->next_method_idnum();
        if (num == constMethodOopDesc::UNSET_IDNUM) {
          // cannot add any more methods
          return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED;
        }
        u2 new_num = k_new_method->method_idnum();
        methodOop idnum_owner = scratch_class->method_with_idnum(num);
        if (idnum_owner != NULL) {
          // There is already a method assigned this idnum -- switch them
          idnum_owner->set_method_idnum(new_num);
        }
        k_new_method->set_method_idnum(num);
      }
      ++ni; // advance to next new method
      break;
    case deleted:
      // method deleted, see if it is OK
      old_flags = (jushort) k_old_method->access_flags().get_flags();
      if ((old_flags & JVM_ACC_PRIVATE) == 0
        // hack: private should be treated as final, but alas
        || (old_flags & (JVM_ACC_FINAL|JVM_ACC_STATIC)) == 0
        ) {
          // deleted methods must be private
          return JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED;
      }
      ++oi; // advance to next old method
      break;
    default:
      ShouldNotReachHere();
    }
  }

  return JVMTI_ERROR_NONE;
}

int VM_RedefineClasses::calculate_redefinition_flags(instanceKlassHandle new_class) {

  int result = Klass::NoRedefinition;



  TRACE_RC2("Comparing different class versions of class %s", new_class->name()->as_C_string());

  assert(new_class->old_version() != NULL, "must have old version");
  instanceKlassHandle the_class(new_class->old_version());

  // Check whether class is in the error init state.
  if (the_class->is_in_error_state()) {
    // TBD #5057930: special error code is needed in 1.6
    //result = Klass::union_redefinition_level(result, Klass::Invalid);
  }

  int i;

  //////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Check superclasses
  assert(new_class->super() == NULL || new_class->super()->klass_part()->is_newest_version(), "");
  if (the_class->super() != new_class->super()) {
    // Super class changed

    klassOop cur_klass = the_class->super();
    while (cur_klass != NULL) {
      if (!new_class->is_subclass_of(cur_klass->klass_part()->newest_version())) {
        TRACE_RC2("Removed super class %s", cur_klass->klass_part()->name()->as_C_string());
        result = result | Klass::RemoveSuperType | Klass::ModifyInstances | Klass::ModifyClass;

        if (!cur_klass->klass_part()->has_subtype_changed()) {
          TRACE_RC2("Subtype changed of class %s", cur_klass->klass_part()->name()->as_C_string());
          cur_klass->klass_part()->set_subtype_changed(true);
        }
      }

      cur_klass = cur_klass->klass_part()->super();
    }

    cur_klass = new_class->super();
    while (cur_klass != NULL) {
      if (!the_class->is_subclass_of(cur_klass->klass_part()->old_version())) {
        TRACE_RC2("Added super class %s", cur_klass->klass_part()->name()->as_C_string());
        result = result | Klass::ModifyClass | Klass::ModifyInstances;
      }
      cur_klass = cur_klass->klass_part()->super();
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Check interfaces

  // Interfaces removed?
  objArrayOop old_interfaces = the_class->transitive_interfaces();
  for (i = 0; i<old_interfaces->length(); i++) {
    instanceKlassHandle old_interface((klassOop)old_interfaces->obj_at(i));
    if (!new_class->implements_interface_any_version(old_interface())) {
      result = result | Klass::RemoveSuperType | Klass::ModifyClass;
      TRACE_RC2("Removed interface %s", old_interface->name()->as_C_string());
      
      if (!old_interface->has_subtype_changed()) {
        TRACE_RC2("Subtype changed of interface %s", old_interface->name()->as_C_string());
        old_interface->set_subtype_changed(true);
      }
    }
  }

  // Interfaces added?
  objArrayOop new_interfaces = new_class->transitive_interfaces();
  for (i = 0; i<new_interfaces->length(); i++) {
    if (!the_class->implements_interface_any_version((klassOop)new_interfaces->obj_at(i))) {
      result = result | Klass::ModifyClass;
      TRACE_RC2("Added interface %s", ((klassOop)new_interfaces->obj_at(i))->klass_part()->name()->as_C_string());
    }
  }


  // Check whether class modifiers are the same.
  jushort old_flags = (jushort) the_class->access_flags().get_flags();
  jushort new_flags = (jushort) new_class->access_flags().get_flags();
  if (old_flags != new_flags) {
    // TODO (tw): Can this have any effects?
  }
  
  // Check if the number, names, types and order of fields declared in these classes
  // are the same.
  JavaFieldStream old_fs(the_class);
  JavaFieldStream new_fs(new_class);
  for (; !old_fs.done() && !new_fs.done(); old_fs.next(), new_fs.next()) {
    // access
    old_flags = old_fs.access_flags().as_short();
    new_flags = new_fs.access_flags().as_short();
    if ((old_flags ^ new_flags) & JVM_RECOGNIZED_FIELD_MODIFIERS) {
      // TODO (tw) can this have any effect?
    }
    // offset
    if (old_fs.offset() != new_fs.offset()) {
      result = result | Klass::ModifyInstances;
    }
    // name and signature
    Symbol* name_sym1 = the_class->constants()->symbol_at(old_fs.name_index());
    Symbol* sig_sym1 = the_class->constants()->symbol_at(old_fs.signature_index());
    Symbol* name_sym2 = new_class->constants()->symbol_at(new_fs.name_index());
    Symbol* sig_sym2 = new_class->constants()->symbol_at(new_fs.signature_index());
    if (name_sym1 != name_sym2 || sig_sym1 != sig_sym2) {
      result = result | Klass::ModifyInstances;
    }
  }

  // If both streams aren't done then we have a differing number of
  // fields.
  if (!old_fs.done() || !new_fs.done()) {
      result = result | Klass::ModifyInstances;
  }

  // Do a parallel walk through the old and new methods. Detect
  // cases where they match (exist in both), have been added in
  // the new methods, or have been deleted (exist only in the
  // old methods).  The class file parser places methods in order
  // by method name, but does not order overloaded methods by
  // signature.  In order to determine what fate befell the methods,
  // this code places the overloaded new methods that have matching
  // old methods in the same order as the old methods and places
  // new overloaded methods at the end of overloaded methods of
  // that name. The code for this order normalization is adapted
  // from the algorithm used in instanceKlass::find_method().
  // Since we are swapping out of order entries as we find them,
  // we only have to search forward through the overloaded methods.
  // Methods which are added and have the same name as an existing
  // method (but different signature) will be put at the end of
  // the methods with that name, and the name mismatch code will
  // handle them.
  objArrayHandle k_old_methods(the_class->methods());
  objArrayHandle k_new_methods(new_class->methods());
  int n_old_methods = k_old_methods->length();
  int n_new_methods = k_new_methods->length();

  int ni = 0;
  int oi = 0;
  while (true) {
    methodOop k_old_method;
    methodOop k_new_method;
    enum { matched, added, deleted, undetermined } method_was = undetermined;

    if (oi >= n_old_methods) {
      if (ni >= n_new_methods) {
        break; // we've looked at everything, done
      }
      // New method at the end
      k_new_method = (methodOop) k_new_methods->obj_at(ni);
      method_was = added;
    } else if (ni >= n_new_methods) {
      // Old method, at the end, is deleted
      k_old_method = (methodOop) k_old_methods->obj_at(oi);
      method_was = deleted;
    } else {
      // There are more methods in both the old and new lists
      k_old_method = (methodOop) k_old_methods->obj_at(oi);
      k_new_method = (methodOop) k_new_methods->obj_at(ni);
      if (k_old_method->name() != k_new_method->name()) {
        // Methods are sorted by method name, so a mismatch means added
        // or deleted
        if (k_old_method->name()->fast_compare(k_new_method->name()) > 0) {
          method_was = added;
        } else {
          method_was = deleted;
        }
      } else if (k_old_method->signature() == k_new_method->signature()) {
        // Both the name and signature match
        method_was = matched;
      } else {
        // The name matches, but the signature doesn't, which means we have to
        // search forward through the new overloaded methods.
        int nj;  // outside the loop for post-loop check
        for (nj = ni + 1; nj < n_new_methods; nj++) {
          methodOop m = (methodOop)k_new_methods->obj_at(nj);
          if (k_old_method->name() != m->name()) {
            // reached another method name so no more overloaded methods
            method_was = deleted;
            break;
          }
          if (k_old_method->signature() == m->signature()) {
            // found a match so swap the methods
            k_new_methods->obj_at_put(ni, m);
            k_new_methods->obj_at_put(nj, k_new_method);
            k_new_method = m;
            method_was = matched;
            break;
          }
        }

        if (nj >= n_new_methods) {
          // reached the end without a match; so method was deleted
          method_was = deleted;
        }
      }
    }

    switch (method_was) {
  case matched:
    // methods match, be sure modifiers do too
    old_flags = (jushort) k_old_method->access_flags().get_flags();
    new_flags = (jushort) k_new_method->access_flags().get_flags();
    if ((old_flags ^ new_flags) & ~(JVM_ACC_NATIVE)) {
      // (tw) Can this have any effects? Probably yes on vtables?
      result = result | Klass::ModifyClass;
    }
    {
      u2 new_num = k_new_method->method_idnum();
      u2 old_num = k_old_method->method_idnum();
      if (new_num != old_num) {
        methodOop idnum_owner = new_class->method_with_idnum(old_num);
        if (idnum_owner != NULL) {
          // There is already a method assigned this idnum -- switch them
          idnum_owner->set_method_idnum(new_num);
        }
        k_new_method->set_method_idnum(old_num);
        TRACE_RC2("swapping idnum of new and old method %d / %d!", new_num, old_num);        
        swap_all_method_annotations(old_num, new_num, new_class);
      }
    }
    TRACE_RC3("Method matched: new: %s [%d] == old: %s [%d]",
      k_new_method->name_and_sig_as_C_string(), ni,
      k_old_method->name_and_sig_as_C_string(), oi);
    // advance to next pair of methods
    ++oi;
    ++ni;
    break;
  case added:
    // method added, see if it is OK
    new_flags = (jushort) k_new_method->access_flags().get_flags();
    if ((new_flags & JVM_ACC_PRIVATE) == 0
      // hack: private should be treated as final, but alas
      || (new_flags & (JVM_ACC_FINAL|JVM_ACC_STATIC)) == 0
      ) {
        // new methods must be private
        result = result | Klass::ModifyClass;
    }
    {
      u2 num = the_class->next_method_idnum();
      if (num == constMethodOopDesc::UNSET_IDNUM) {
        // cannot add any more methods
        result = result | Klass::ModifyClass;
      }
      u2 new_num = k_new_method->method_idnum();
      methodOop idnum_owner = new_class->method_with_idnum(num);
      if (idnum_owner != NULL) {
        // There is already a method assigned this idnum -- switch them
        idnum_owner->set_method_idnum(new_num);
      }
      k_new_method->set_method_idnum(num);
      swap_all_method_annotations(new_num, num, new_class);
    }
    TRACE_RC1("Method added: new: %s [%d]",
      k_new_method->name_and_sig_as_C_string(), ni);
    ++ni; // advance to next new method
    break;
  case deleted:
    // method deleted, see if it is OK
    old_flags = (jushort) k_old_method->access_flags().get_flags();
    if ((old_flags & JVM_ACC_PRIVATE) == 0
      // hack: private should be treated as final, but alas
      || (old_flags & (JVM_ACC_FINAL|JVM_ACC_STATIC)) == 0
      ) {
        // deleted methods must be private
        result = result | Klass::ModifyClass;
    }
    TRACE_RC1("Method deleted: old: %s [%d]",
      k_old_method->name_and_sig_as_C_string(), oi);
    ++oi; // advance to next old method
    break;
  default:
    ShouldNotReachHere();
    }
  }

  if (new_class()->size() != new_class->old_version()->size()) {
    result |= Klass::ModifyClassSize;
  }

  if (new_class->size_helper() != ((instanceKlass*)(new_class->old_version()->klass_part()))->size_helper()) {
    result |= Klass::ModifyInstanceSize;
  }

  // (tw) Check method bodies to be able to return NoChange?
  return result;
}

void VM_RedefineClasses::calculate_instance_update_information(klassOop new_version) {

  class CalculateFieldUpdates : public FieldClosure {

  private:
    instanceKlass* _old_ik;
    GrowableArray<int> _update_info;
    int _position;
    bool _copy_backwards;

  public:

    bool does_copy_backwards() {
      return _copy_backwards;
    }

    CalculateFieldUpdates(instanceKlass* old_ik) :
        _old_ik(old_ik), _position(instanceOopDesc::base_offset_in_bytes()), _copy_backwards(false) {
      _update_info.append(_position);
      _update_info.append(0);
    }

    GrowableArray<int> &finish() {
      _update_info.append(0);
      return _update_info;
    }

    void do_field(fieldDescriptor* fd) {
      int alignment = fd->offset() - _position;
      if (alignment > 0) {
        // This field was aligned, so we need to make sure that we fill the gap
        fill(alignment);
      }

      assert(_position == fd->offset(), "must be correct offset!");

      fieldDescriptor old_fd;
      if (_old_ik->find_field(fd->name(), fd->signature(), false, &old_fd) != NULL) {
        // Found field in the old class, copy
        copy(old_fd.offset(), type2aelembytes(fd->field_type()));

        if (old_fd.offset() < fd->offset()) {
          _copy_backwards = true;
        }

        // Transfer special flags
        fd->set_is_field_modification_watched(old_fd.is_field_modification_watched());
        fd->set_is_field_access_watched(old_fd.is_field_access_watched());
      } else {
        // New field, fill
        fill(type2aelembytes(fd->field_type()));
      }
    }

  private:

    void fill(int size) {
      if (_update_info.length() > 0 && _update_info.at(_update_info.length() - 1) < 0) {
        (*_update_info.adr_at(_update_info.length() - 1)) -= size;
      } else {
        _update_info.append(-size);
      }
      _position += size;
    }

    void copy(int offset, int size) {
      int prev_end = -1;
      if (_update_info.length() > 0 && _update_info.at(_update_info.length() - 1) > 0) {
        prev_end = _update_info.at(_update_info.length() - 2) + _update_info.at(_update_info.length() - 1);
      }

      if (prev_end == offset) {
        (*_update_info.adr_at(_update_info.length() - 2)) += size;
      } else {
        _update_info.append(size);
        _update_info.append(offset);
      }

      _position += size;
    }
  };

  instanceKlass* ik = instanceKlass::cast(new_version);
  instanceKlass* old_ik = instanceKlass::cast(new_version->klass_part()->old_version());
  CalculateFieldUpdates cl(old_ik);
  ik->do_nonstatic_fields(&cl);

  GrowableArray<int> result = cl.finish();
  ik->store_update_information(result);
  ik->set_copying_backwards(cl.does_copy_backwards());

  IF_TRACE_RC2 {
    TRACE_RC2("Instance update information for %s:", new_version->klass_part()->name()->as_C_string());
    if (cl.does_copy_backwards()) {
      TRACE_RC2("\tDoes copy backwards!");
    }
    for (int i=0; i<result.length(); i++) {
      int curNum = result.at(i);
      if (curNum < 0) {
        TRACE_RC2("\t%d CLEAN", curNum);
      } else if (curNum > 0) {
        TRACE_RC2("\t%d COPY from %d", curNum, result.at(i + 1));
        i++;
      } else {
        TRACE_RC2("\tEND");
      }
    }
  }
}

void VM_RedefineClasses::rollback() {
  TRACE_RC1("Rolling back redefinition!");
  SystemDictionary::rollback_redefinition();

  TRACE_RC1("After rolling back system dictionary!");
  for (int i=0; i<_new_classes->length(); i++) {
    SystemDictionary::remove_from_hierarchy(_new_classes->at(i));
  }

  for (int i=0; i<_new_classes->length(); i++) {
    instanceKlassHandle new_class = _new_classes->at(i);
    new_class->set_redefining(false);
    new_class->old_version()->klass_part()->set_new_version(NULL);
    new_class->set_old_version(NULL);
  }

}

void VM_RedefineClasses::swap_marks(oop first, oop second) {
  markOop first_mark = first->mark();
  markOop second_mark = second->mark();
  first->set_mark(second_mark);
  second->set_mark(first_mark);
}


class FieldCopier : public FieldClosure {
  public:
  void do_field(fieldDescriptor* fd) {
    instanceKlass* cur = instanceKlass::cast(fd->field_holder());
    oop cur_oop = cur->java_mirror();

    instanceKlass* old = instanceKlass::cast(cur->old_version());
    oop old_oop = old->java_mirror();

    fieldDescriptor result;
    bool found = old->find_local_field(fd->name(), fd->signature(), &result);
    if (found && result.is_static()) {
      TRACE_RC3("Copying static field value for field %s old_offset=%d new_offset=%d",
        fd->name()->as_C_string(), result.offset(), fd->offset());
      memcpy(cur_oop->obj_field_addr<HeapWord>(fd->offset()),
             old_oop->obj_field_addr<HeapWord>(result.offset()),
             type2aelembytes(fd->field_type()));

      // Static fields may have references to java.lang.Class
      if (fd->field_type() == T_OBJECT) {
         oop oop = cur_oop->obj_field(fd->offset());
         if (oop != NULL && oop->is_instanceMirror()) {
            klassOop klass = java_lang_Class::as_klassOop(oop);
            if (klass != NULL && klass->klass_part()->oop_is_instance()) {
              assert(oop == instanceKlass::cast(klass)->java_mirror(), "just checking");
              if (klass->klass_part()->new_version() != NULL) {
              oop = instanceKlass::cast(klass->klass_part()->new_version())->java_mirror();

              cur_oop->obj_field_put(fd->offset(), oop);
            }
          }
        }
      }
    }
  }
};

void VM_RedefineClasses::mark_as_scavengable(nmethod* nm) {
  if (!nm->on_scavenge_root_list()) {
    CodeCache::add_scavenge_root_nmethod(nm);
  }
}

struct StoreBarrier {
  template <class T> static void oop_store(T* p, oop v) { ::oop_store(p, v); }
};

struct StoreNoBarrier {
  template <class T> static void oop_store(T* p, oop v) { oopDesc::encode_store_heap_oop_not_null(p, v); }
};

template <class S>
class ChangePointersOopClosure : public OopClosureNoHeader {
  // Forward pointers to instanceKlass and mirror class to new versions
  template <class T>
  inline void do_oop_work(T* p) {
    oop oop = oopDesc::load_decode_heap_oop(p);
    if (oop == NULL) {
      return;
    }
    if (oop->is_instanceKlass()) {
      klassOop klass = (klassOop) oop;
      if (klass->klass_part()->new_version() != NULL) {
        oop = klass->klass_part()->new_version();
        S::oop_store(p, oop);
      }
    } else if (oop->is_instanceMirror()) {
      klassOop klass = java_lang_Class::as_klassOop(oop);
      if (klass != NULL && klass->klass_part()->oop_is_instance()) {
        assert(oop == instanceKlass::cast(klass)->java_mirror(), "just checking");
        if (klass->klass_part()->new_version() != NULL) {
          oop = instanceKlass::cast(klass->klass_part()->new_version())->java_mirror();
          S::oop_store(p, oop);
        }
      }
    }
  }

  virtual void do_oop(oop* o) {
    do_oop_work(o);
  }

  virtual void do_oop(narrowOop* o) {
    do_oop_work(o);
  }
};

void VM_RedefineClasses::doit() {
  Thread *thread = Thread::current();
  
  TRACE_RC1("Entering doit!");

  assert((_max_redefinition_flags & Klass::RemoveSuperType) == 0, "removing super types not allowed");

  if (UseSharedSpaces) {
    // Sharing is enabled so we remap the shared readonly space to
    // shared readwrite, private just in case we need to redefine
    // a shared class. We do the remap during the doit() phase of
    // the safepoint to be safer.
    if (!CompactingPermGenGen::remap_shared_readonly_as_readwrite()) {
      TRACE_RC1("failed to remap shared readonly space to readwrite, private");
      _result = JVMTI_ERROR_INTERNAL;
      return;
    }
  }
  
  RC_TIMER_START(_timer_prepare_redefinition);
  for (int i = 0; i < _new_classes->length(); i++) {
    redefine_single_class(_new_classes->at(i), thread);
  }
  
  // Deoptimize all compiled code that depends on this class
  flush_dependent_code(instanceKlassHandle(Thread::current(), (klassOop)NULL), Thread::current());

  // Adjust constantpool caches and vtables for all classes
  // that reference methods of the evolved class.
  SystemDictionary::classes_do(adjust_cpool_cache, Thread::current());

  RC_TIMER_STOP(_timer_prepare_redefinition);
  RC_TIMER_START(_timer_heap_iteration);

    class ChangePointersObjectClosure : public ObjectClosure {

    private:

      OopClosureNoHeader *_closure;
      bool _needs_instance_update;
      oop _tmp_obj;
      int _tmp_obj_size;

    public:
      ChangePointersObjectClosure(OopClosureNoHeader *closure) : _closure(closure), _needs_instance_update(false), _tmp_obj(NULL), _tmp_obj_size(0) {}

      bool needs_instance_update() {
        return _needs_instance_update;
      }

      void copy_to_tmp(oop o) {
        int size = o->size();
        if (_tmp_obj_size < size) {
          _tmp_obj_size = size;
          _tmp_obj = (oop)resource_allocate_bytes(size * HeapWordSize);
        }
        Copy::aligned_disjoint_words((HeapWord*)o, (HeapWord*)_tmp_obj, size);
      }

      virtual void do_object(oop obj) {
        if (obj->is_instanceKlass()) return;
        if (obj->is_instanceMirror()) {
          // static fields may have references to old java.lang.Class instances, update them
          // at the same time, we don't want to update other oops in the java.lang.Class
          // Causes SIGSEGV?
          //instanceMirrorKlass::oop_fields_iterate(obj, _closure);
        } else {
          obj->oop_iterate(_closure);
        }

        if (obj->blueprint()->new_version() != NULL) {
          Klass* new_klass = obj->blueprint()->new_version()->klass_part();
          if (obj->is_perm()) {
            _needs_instance_update = true;
          } else if(new_klass->update_information() != NULL) {
            int size_diff = obj->size() - obj->size_given_klass(new_klass);

            // Either new size is bigger or gap is to small to be filled
            if (size_diff < 0 || (size_diff > 0 && (size_t) size_diff < CollectedHeap::min_fill_size())) {
              // We need an instance update => set back to old klass
              _needs_instance_update = true;
            } else {
              oop src = obj;
              if (new_klass->is_copying_backwards()) {
                copy_to_tmp(obj);
                src = _tmp_obj;
              }
              src->set_klass_no_check(obj->blueprint()->new_version());
              MarkSweep::update_fields(obj, src, new_klass->update_information());

              if (size_diff > 0) {
                HeapWord* dead_space = ((HeapWord *)obj) + obj->size();
                CollectedHeap::fill_with_object(dead_space, size_diff);
              }
            }
          } else {
            obj->set_klass_no_check(obj->blueprint()->new_version());
          }
        }
      }
    };
    
    ChangePointersOopClosure<StoreNoBarrier> oopClosureNoBarrier;
    ChangePointersOopClosure<StoreBarrier> oopClosure;
    ChangePointersObjectClosure objectClosure(&oopClosure);

    {
      // Since we may update oops inside nmethod's code blob to point to java.lang.Class in new generation, we need to
      // make sure such references are properly recognized by GC. For that, If ScavengeRootsInCode is true, we need to
      // mark such nmethod's as "scavengable".
      // For now, mark all nmethod's as scavengable that are not scavengable already
      if (ScavengeRootsInCode) {
        CodeCache::nmethods_do(mark_as_scavengable);
      }

      SharedHeap::heap()->gc_prologue(true);
      Universe::heap()->object_iterate(&objectClosure);
      Universe::root_oops_do(&oopClosureNoBarrier);
      SharedHeap::heap()->gc_epilogue(false);
    }


    for (int i=0; i<_new_classes->length(); i++) {
      klassOop cur_oop = _new_classes->at(i)();
      instanceKlass* cur = instanceKlass::cast(cur_oop);
      klassOop old_oop = cur->old_version();
      instanceKlass* old = instanceKlass::cast(old_oop);

      // Swap marks to have same hashcodes
      swap_marks(cur_oop, old_oop);
      swap_marks(cur->java_mirror(), old->java_mirror());

      // Revert pool holder for old version of klass (it was updated by one of ours closure!)
      old->constants()->set_pool_holder(old_oop);


      if (old->array_klasses() != NULL) {
        // Transfer the array classes, otherwise we might get cast exceptions when casting array types.
        assert(cur->array_klasses() == NULL, "just checking");
        cur->set_array_klasses(old->array_klasses());
      }

      // Initialize the new class! Special static initialization that does not execute the
      // static constructor but copies static field values from the old class if name
      // and signature of a static field match.
      FieldCopier copier;
      cur->do_local_static_fields(&copier); // TODO (tw): What about internal static fields??
      old->set_java_mirror(cur->java_mirror());

      // Transfer init state
      instanceKlass::ClassState state = old->init_state();
      if (state > instanceKlass::linked) {
        cur->set_init_state(state);
      }
    }

  RC_TIMER_STOP(_timer_heap_iteration);
  RC_TIMER_START(_timer_redefinition);
  if (objectClosure.needs_instance_update()){

    // Do a full garbage collection to update the instance sizes accordingly
    TRACE_RC1("Before performing full GC!");
    Universe::set_redefining_gc_run(true);
    notify_gc_begin(true);
    Universe::heap()->collect_as_vm_thread(GCCause::_heap_inspection);
    notify_gc_end();
    Universe::set_redefining_gc_run(false);
    TRACE_RC1("GC done!");
  }

  // Unmark klassOops as "redefining"
  for (int i=0; i<_new_classes->length(); i++) {
    klassOop cur_klass = _new_classes->at(i)();
    instanceKlass* cur = (instanceKlass*)cur_klass->klass_part();
    cur->set_redefining(false);
    cur->clear_update_information();
  }

  // Disable any dependent concurrent compilations
  SystemDictionary::notice_modification();

  // Set flag indicating that some invariants are no longer true.
  // See jvmtiExport.hpp for detailed explanation.
  JvmtiExport::set_has_redefined_a_class();

  // Clean up caches in the compiler interface and compiler threads
  ciObjectFactory::resort_shared_ci_objects();

#ifdef ASSERT

  // Universe::verify();
  // JNIHandles::verify();

  SystemDictionary::classes_do(check_class, thread);
#endif

  RC_TIMER_STOP(_timer_redefinition);

  if (TraceRedefineClasses > 0) {
    tty->flush();
  }
}

void VM_RedefineClasses::doit_epilogue() {

  RC_TIMER_START(_timer_vm_op_epilogue);

  //unlock_threads();

  ResourceMark mark;

  VM_GC_Operation::doit_epilogue();
  TRACE_RC1("GC Operation epilogue finished! ");

  // Free the array of scratch classes
  delete _new_classes;
  _new_classes = NULL;

  // Free the array of affected classes
  delete _affected_klasses;
  _affected_klasses = NULL;

  TRACE_RC1("Redefinition finished!");  

  RC_TIMER_STOP(_timer_vm_op_epilogue);
}

bool VM_RedefineClasses::is_modifiable_class(oop klass_mirror) {
  // classes for primitives cannot be redefined
  if (java_lang_Class::is_primitive(klass_mirror)) {
    return false;
  }
  klassOop the_class_oop = java_lang_Class::as_klassOop(klass_mirror);
  // classes for arrays cannot be redefined
  if (the_class_oop == NULL || !Klass::cast(the_class_oop)->oop_is_instance()) {
    return false;
  }
  return true;
}

#ifdef ASSERT

void VM_RedefineClasses::verify_classes(klassOop k_oop_latest, oop initiating_loader, TRAPS) {
  klassOop k_oop = k_oop_latest;
  while (k_oop != NULL) {

    instanceKlassHandle k_handle(THREAD, k_oop);
    Verifier::verify(k_handle, Verifier::ThrowException, true, true, THREAD);
    k_oop = k_oop->klass_part()->old_version();
  }
}

#endif

// Rewrite faster byte-codes back to their slower equivalent. Undoes rewriting happening in templateTable_xxx.cpp
// The reason is that once we zero cpool caches, we need to re-resolve all entries again. Faster bytecodes do not
// do that, they assume that cache entry is resolved already.
void VM_RedefineClasses::unpatch_bytecode(methodOop method) {
  RawBytecodeStream bcs(method);
  Bytecodes::Code code;
  Bytecodes::Code java_code;
  while (!bcs.is_last_bytecode()) {
    code = bcs.raw_next();
    address bcp = bcs.bcp();

    if (code == Bytecodes::_breakpoint) {
      int bci = method->bci_from(bcp);
      code = method->orig_bytecode_at(bci);
      java_code = Bytecodes::java_code(code);
      if (code != java_code &&
           (java_code == Bytecodes::_getfield ||
            java_code == Bytecodes::_putfield ||
            java_code == Bytecodes::_aload_0)) {
        // Let breakpoint table handling unpatch bytecode
        method->set_orig_bytecode_at(bci, java_code);
      }
    } else {
      java_code = Bytecodes::java_code(code);
      if (code != java_code &&
           (java_code == Bytecodes::_getfield ||
            java_code == Bytecodes::_putfield ||
            java_code == Bytecodes::_aload_0)) {
        *bcp = java_code;
      }
    }

    // Additionally, we need to unpatch bytecode at bcp+1 for fast_xaccess (which would be fast field access)
    if (code == Bytecodes::_fast_iaccess_0 || code == Bytecodes::_fast_aaccess_0 || code == Bytecodes::_fast_faccess_0) {
      Bytecodes::Code code2 = Bytecodes::code_or_bp_at(bcp + 1);
      assert(code2 == Bytecodes::_fast_igetfield ||
             code2 == Bytecodes::_fast_agetfield ||
             code2 == Bytecodes::_fast_fgetfield, "");
      *(bcp + 1) = Bytecodes::java_code(code2);
    }
  }
}

// Unevolving classes may point to old methods directly
// from their constant pool caches, itables, and/or vtables. We
// use the SystemDictionary::classes_do() facility and this helper
// to fix up these pointers. Additional field offsets and vtable indices
// in the constant pool cache entries are fixed.
//
// Note: We currently don't support updating the vtable in
// arrayKlassOops. See Open Issues in jvmtiRedefineClasses.hpp.
void VM_RedefineClasses::adjust_cpool_cache(klassOop k_oop_latest, oop initiating_loader, TRAPS) {
  klassOop k_oop = k_oop_latest;
  while (k_oop != NULL) {
    Klass *k = k_oop->klass_part();
    if (k->oop_is_instance()) {
      HandleMark hm(THREAD);
      instanceKlass *ik = (instanceKlass *) k;

      constantPoolHandle other_cp;
      constantPoolCacheOop cp_cache;

      other_cp = constantPoolHandle(ik->constants());

      for (int i=0; i<other_cp->length(); i++) {
        if (other_cp->tag_at(i).is_klass()) {
          klassOop klass = other_cp->klass_at(i, THREAD);
          if (klass->klass_part()->new_version() != NULL) {

            // (tw) TODO: check why/if this is necessary
            other_cp->klass_at_put(i, klass->klass_part()->new_version());
          }
          klass = other_cp->klass_at(i, THREAD);
          assert(klass->klass_part()->new_version() == NULL, "Must be new klass!");
        }
      }

      cp_cache = other_cp->cache();

      if (cp_cache != NULL) {
        cp_cache->adjust_entries();
      }

      // If bytecode rewriting is enabled, we also need to unpatch bytecode to force resolution of zeroed entries
      if (RewriteBytecodes) {
        ik->methods_do(unpatch_bytecode);
      }
    }
    k_oop = k_oop->klass_part()->old_version();
  }
}

void VM_RedefineClasses::update_jmethod_ids() {
  for (int j = 0; j < _matching_methods_length; ++j) {
    methodOop old_method = (methodOop)_old_methods->obj_at(_matching_old_methods[j]);
    TRACE_RC3("matching method %s", old_method->name_and_sig_as_C_string());
    
    jmethodID jmid = old_method->find_jmethod_id_or_null();
    if (old_method->new_version() != NULL && jmid == NULL) {
       // (tw) Have to create jmethodID in this case
       jmid = old_method->jmethod_id();
    }

    if (jmid != NULL) {
      // There is a jmethodID, change it to point to the new method
      methodHandle new_method_h((methodOop)_new_methods->obj_at(_matching_new_methods[j]));
      if (old_method->new_version() == NULL) {
        methodHandle old_method_h((methodOop)_old_methods->obj_at(_matching_old_methods[j]));
        jmethodID new_jmethod_id = JNIHandles::make_jmethod_id(old_method_h);
        bool result = instanceKlass::cast(old_method_h->method_holder())->update_jmethod_id(old_method_h(), new_jmethod_id);
        //TRACE_RC3("Changed jmethodID for old method assigned to %d / result=%d", new_jmethod_id, result);
        //TRACE_RC3("jmethodID new method: %d jmethodID old method: %d", new_method_h->jmethod_id(), old_method->jmethod_id());
      } else {
        jmethodID mid = new_method_h->jmethod_id();
        bool result = instanceKlass::cast(new_method_h->method_holder())->update_jmethod_id(new_method_h(), jmid);
        //TRACE_RC3("Changed jmethodID for new method assigned to %d / result=%d", jmid, result);
      }
      JNIHandles::change_method_associated_with_jmethod_id(jmid, new_method_h);
      //TRACE_RC3("changing method associated with jmethod id %d to %s", (int)jmid, new_method_h->name()->as_C_string());
      assert(JNIHandles::resolve_jmethod_id(jmid) == (methodOop)_new_methods->obj_at(_matching_new_methods[j]), "should be replaced");
      jmethodID mid = ((methodOop)_new_methods->obj_at(_matching_new_methods[j]))->jmethod_id();
      assert(JNIHandles::resolve_non_null((jobject)mid) == new_method_h(), "must match!");

      //TRACE_RC3("jmethodID new method: %d jmethodID old method: %d", new_method_h->jmethod_id(), old_method->jmethod_id());
    }
  }
}


// Deoptimize all compiled code that depends on this class.
//
// If the can_redefine_classes capability is obtained in the onload
// phase then the compiler has recorded all dependencies from startup.
// In that case we need only deoptimize and throw away all compiled code
// that depends on the class.
//
// If can_redefine_classes is obtained sometime after the onload
// phase then the dependency information may be incomplete. In that case
// the first call to RedefineClasses causes all compiled code to be
// thrown away. As can_redefine_classes has been obtained then
// all future compilations will record dependencies so second and
// subsequent calls to RedefineClasses need only throw away code
// that depends on the class.
//
void VM_RedefineClasses::flush_dependent_code(instanceKlassHandle k_h, TRAPS) {
  assert_locked_or_safepoint(Compile_lock);

  // All dependencies have been recorded from startup or this is a second or
  // subsequent use of RedefineClasses

  // For now deopt all
  // (tw) TODO: Improve the dependency system such that we can safely deopt only a subset of the methods
  if (0 && JvmtiExport::all_dependencies_are_recorded()) {
    Universe::flush_evol_dependents_on(k_h);
  } else {
    CodeCache::mark_all_nmethods_for_deoptimization();

    ResourceMark rm(THREAD);
    DeoptimizationMarker dm;

    // Deoptimize all activations depending on marked nmethods
    Deoptimization::deoptimize_dependents();

    // Make the dependent methods not entrant (in VM_Deoptimize they are made zombies)
    CodeCache::make_marked_nmethods_not_entrant();

    // From now on we know that the dependency information is complete
    JvmtiExport::set_all_dependencies_are_recorded(true);
  }
}

void VM_RedefineClasses::compute_added_deleted_matching_methods() {
  methodOop old_method;
  methodOop new_method;

  _matching_old_methods = NEW_RESOURCE_ARRAY(int, _old_methods->length());
  _matching_new_methods = NEW_RESOURCE_ARRAY(int, _old_methods->length());
  _added_methods        = NEW_RESOURCE_ARRAY(int, _new_methods->length());
  _deleted_methods      = NEW_RESOURCE_ARRAY(int, _old_methods->length());

  _matching_methods_length = 0;
  _deleted_methods_length  = 0;
  _added_methods_length    = 0;

  int nj = 0;
  int oj = 0;
  while (true) {
    if (oj >= _old_methods->length()) {
      if (nj >= _new_methods->length()) {
        break; // we've looked at everything, done
      }
      // New method at the end
      new_method = (methodOop) _new_methods->obj_at(nj);
      _added_methods[_added_methods_length++] = nj;
      ++nj;
    } else if (nj >= _new_methods->length()) {
      // Old method, at the end, is deleted
      old_method = (methodOop) _old_methods->obj_at(oj);
      _deleted_methods[_deleted_methods_length++] = oj;
      ++oj;
    } else {
      old_method = (methodOop) _old_methods->obj_at(oj);
      new_method = (methodOop) _new_methods->obj_at(nj);
      if (old_method->name() == new_method->name()) {
        if (old_method->signature() == new_method->signature()) {
          _matching_old_methods[_matching_methods_length  ] = oj;//old_method;
          _matching_new_methods[_matching_methods_length++] = nj;//new_method;
          ++nj;
          ++oj;
        } else {
          // added overloaded have already been moved to the end,
          // so this is a deleted overloaded method
          _deleted_methods[_deleted_methods_length++] = oj;//old_method;
          ++oj;
        }
      } else { // names don't match
        if (old_method->name()->fast_compare(new_method->name()) > 0) {
          // new method
          _added_methods[_added_methods_length++] = nj;//new_method;
          ++nj;
        } else {
          // deleted method
          _deleted_methods[_deleted_methods_length++] = oj;//old_method;
          ++oj;
        }
      }
    }
  }
  assert(_matching_methods_length + _deleted_methods_length == _old_methods->length(), "sanity");
  assert(_matching_methods_length + _added_methods_length == _new_methods->length(), "sanity");
  TRACE_RC3("Matching methods = %d / deleted methods = %d / added methods = %d", _matching_methods_length, _deleted_methods_length, _added_methods_length);
}



// Install the redefinition of a class:
//    - house keeping (flushing breakpoints and caches, deoptimizing
//      dependent compiled code)
//    - adjusting constant pool caches and vtables in other classes
void VM_RedefineClasses::redefine_single_class(instanceKlassHandle the_new_class, TRAPS) {

  ResourceMark rm(THREAD);

  assert(the_new_class->old_version() != NULL, "Must not be null");
  assert(the_new_class->old_version()->klass_part()->new_version() == the_new_class(), "Must equal");

  instanceKlassHandle the_old_class = instanceKlassHandle(THREAD, the_new_class->old_version());

#ifndef JVMTI_KERNEL
  // Remove all breakpoints in methods of this class
  JvmtiBreakpoints& jvmti_breakpoints = JvmtiCurrentBreakpoints::get_jvmti_breakpoints();
  jvmti_breakpoints.clearall_in_class_at_safepoint(the_old_class());
#endif // !JVMTI_KERNEL

  if (the_old_class() == Universe::reflect_invoke_cache()->klass()) {
    // We are redefining java.lang.reflect.Method. Method.invoke() is
    // cached and users of the cache care about each active version of
    // the method so we have to track this previous version.
    // Do this before methods get switched
    Universe::reflect_invoke_cache()->add_previous_version(
      the_old_class->method_with_idnum(Universe::reflect_invoke_cache()->method_idnum()));
  }

  _old_methods = the_old_class->methods();
  _new_methods = the_new_class->methods();
  compute_added_deleted_matching_methods();

  // track which methods are EMCP for add_previous_version() call below
  
  // (tw) TODO: Check if we need the concept of EMCP?
   BitMap emcp_methods(_old_methods->length());
  int emcp_method_count = 0;
  emcp_methods.clear();  // clears 0..(length() - 1)
  
  // We need to mark methods as old!!
  check_methods_and_mark_as_obsolete(&emcp_methods, &emcp_method_count);
  update_jmethod_ids();

  // TODO:
  transfer_old_native_function_registrations(the_old_class);



#ifdef ASSERT

//  klassOop systemLookup1 = SystemDictionary::resolve_or_null(the_old_class->name(), the_old_class->class_loader(), the_old_class->protection_domain(), THREAD);
//  assert(systemLookup1 == the_new_class(), "New class must be in system dictionary!");

  //JNIHandles::verify();

//  klassOop systemLookup = SystemDictionary::resolve_or_null(the_old_class->name(), the_old_class->class_loader(), the_old_class->protection_domain(), THREAD);

//  assert(systemLookup == the_new_class(), "New class must be in system dictionary!");
  assert(the_new_class->old_version() != NULL, "Must not be null");
  assert(the_new_class->old_version()->klass_part()->new_version() == the_new_class(), "Must equal");

  for (int i=0; i<the_new_class->methods()->length(); i++) {
    assert(((methodOop)the_new_class->methods()->obj_at(i))->method_holder() == the_new_class(), "method holder must match!");
  }

  _old_methods->verify();
  _new_methods->verify();

  the_new_class->vtable()->verify(tty);
  the_old_class->vtable()->verify(tty);

#endif

  // increment the classRedefinedCount field in the_class and in any
  // direct and indirect subclasses of the_class
  increment_class_counter((instanceKlass *)the_old_class()->klass_part(), THREAD);

}


void VM_RedefineClasses::check_methods_and_mark_as_obsolete(BitMap *emcp_methods, int * emcp_method_count_p) {
    TRACE_RC3("Checking matching methods for EMCP");
    *emcp_method_count_p = 0;
    int obsolete_count = 0;
    int old_index = 0;
    for (int j = 0; j < _matching_methods_length; ++j, ++old_index) {
      methodOop old_method = (methodOop)_old_methods->obj_at(_matching_old_methods[j]);
      methodOop new_method = (methodOop)_new_methods->obj_at(_matching_new_methods[j]);
      methodOop old_array_method;

      // Maintain an old_index into the _old_methods array by skipping
      // deleted methods
      while ((old_array_method = (methodOop) _old_methods->obj_at(old_index))
        != old_method) {
          ++old_index;
      }

      if (MethodComparator::methods_EMCP(old_method, new_method)) {
        // The EMCP definition from JSR-163 requires the bytecodes to be
        // the same with the exception of constant pool indices which may
        // differ. However, the constants referred to by those indices
        // must be the same.
        //
        // We use methods_EMCP() for comparison since constant pool
        // merging can remove duplicate constant pool entries that were
        // present in the old method and removed from the rewritten new
        // method. A faster binary comparison function would consider the
        // old and new methods to be different when they are actually
        // EMCP.

        // track which methods are EMCP for add_previous_version() call
        emcp_methods->set_bit(old_index);
        (*emcp_method_count_p)++;

        // An EMCP method is _not_ obsolete. An obsolete method has a
        // different jmethodID than the current method. An EMCP method
        // has the same jmethodID as the current method. Having the
        // same jmethodID for all EMCP versions of a method allows for
        // a consistent view of the EMCP methods regardless of which
        // EMCP method you happen to have in hand. For example, a
        // breakpoint set in one EMCP method will work for all EMCP
        // versions of the method including the current one.

        old_method->set_new_version(new_method);
        new_method->set_old_version(old_method);

        TRACE_RC3("Found EMCP method %s", old_method->name_and_sig_as_C_string());

        // Transfer breakpoints
        instanceKlass *ik = instanceKlass::cast(old_method->method_holder());
        for (BreakpointInfo* bp = ik->breakpoints(); bp != NULL; bp = bp->next()) {
          TRACE_RC2("Checking breakpoint");
          TRACE_RC2("%d / %d", bp->match(old_method), bp->match(new_method));
          if (bp->match(old_method)) {
            assert(bp->match(new_method), "if old method is method, then new method must match too");
            TRACE_RC2("Found a breakpoint in an old EMCP method");
            new_method->set_breakpoint(bp->bci());
          }
        }
      } else {
        // mark obsolete methods as such
        old_method->set_is_obsolete();
        obsolete_count++;

        // With tracing we try not to "yack" too much. The position of
        // this trace assumes there are fewer obsolete methods than
        // EMCP methods.
        TRACE_RC3("mark %s(%s) as obsolete",
          old_method->name()->as_C_string(),
          old_method->signature()->as_C_string());
      }
      old_method->set_is_old();
    }
    for (int i = 0; i < _deleted_methods_length; ++i) {
      methodOop old_method = (methodOop)_old_methods->obj_at(_deleted_methods[i]);

      //assert(old_method->vtable_index() < 0,
      //  "cannot delete methods with vtable entries");;

      // Mark all deleted methods as old and obsolete
      old_method->set_is_old();
      old_method->set_is_obsolete();
      ++obsolete_count;
      // With tracing we try not to "yack" too much. The position of
      // this trace assumes there are fewer obsolete methods than
      // EMCP methods.
      TRACE_RC3("mark deleted %s(%s) as obsolete",
        old_method->name()->as_C_string(),
        old_method->signature()->as_C_string());
    }
    //assert((*emcp_method_count_p + obsolete_count) == _old_methods->length(), "sanity check");
    TRACE_RC3("EMCP_cnt=%d, obsolete_cnt=%d !", *emcp_method_count_p, obsolete_count);
}

// Increment the classRedefinedCount field in the specific instanceKlass
// and in all direct and indirect subclasses.
void VM_RedefineClasses::increment_class_counter(instanceKlass *ik, TRAPS) {
  oop class_mirror = ik->java_mirror();
  klassOop class_oop = java_lang_Class::as_klassOop(class_mirror);
  int new_count = java_lang_Class::classRedefinedCount(class_mirror) + 1;
  java_lang_Class::set_classRedefinedCount(class_mirror, new_count);
  TRACE_RC3("updated count for class=%s to %d", ik->external_name(), new_count);
}

#ifndef PRODUCT
void VM_RedefineClasses::check_class(klassOop k_oop, TRAPS) {
  Klass *k = k_oop->klass_part();
  if (k->oop_is_instance()) {
    HandleMark hm(THREAD);
    instanceKlass *ik = (instanceKlass *) k;
    assert(ik->is_newest_version(), "must be latest version in system dictionary");

    if (ik->vtable_length() > 0) {
      ResourceMark rm(THREAD);
      if (!ik->vtable()->check_no_old_or_obsolete_entries()) {
        TRACE_RC1("size of class: %d\n", k_oop->size());
        TRACE_RC1("klassVtable::check_no_old_entries failure -- OLD method found -- class: %s", ik->signature_name());
        assert(false, "OLD method found");
      }

      ik->vtable()->verify(tty, true);
    }
  }
}

#endif

static bool match_right(void* value, Pair<klassOop, klassOop> elem) {
  return elem.right() == value;
}

jvmtiError VM_RedefineClasses::do_topological_class_sorting( const jvmtiClassDefinition *class_defs, int class_count, TRAPS)
{
  GrowableArray< Pair<klassOop, klassOop> > links;

  for (int i=0; i<class_count; i++) {

    oop mirror = JNIHandles::resolve_non_null(class_defs[i].klass);
    instanceKlassHandle the_class(THREAD, java_lang_Class::as_klassOop(mirror));
    Handle the_class_loader(THREAD, the_class->class_loader());
    Handle protection_domain(THREAD, the_class->protection_domain());

    ClassFileStream st((u1*) class_defs[i].class_bytes,
      class_defs[i].class_byte_count, (char *)"__VM_RedefineClasses__");
    ClassFileParser cfp(&st);

    GrowableArray<Symbol*> symbolArr;
    TempNewSymbol parsed_name;
    TRACE_RC2("Before find super symbols of class %s", the_class->name()->as_C_string());
    cfp.parseClassFile(the_class->name(), the_class_loader, protection_domain, the_class, KlassHandle(), NULL, &symbolArr, parsed_name, false, THREAD);
    
    for (int j=0; j<symbolArr.length(); j++) {
      Symbol* sym = symbolArr.at(j);
      TRACE_RC3("Before adding link to super class %s", sym->as_C_string());
      klassOop super_klass = SystemDictionary::resolve_or_null(sym, the_class_loader, protection_domain, THREAD);
      if (super_klass != NULL) {
        instanceKlassHandle the_super_class(THREAD, super_klass);
        if (_affected_klasses->contains(the_super_class)) {
          TRACE_RC2("Found class to link");
          links.append(Pair<klassOop, klassOop>(super_klass, the_class()));
        }
      }
    }

    assert(the_class->check_redefinition_flag(Klass::MarkedAsAffected), "");
    the_class->clear_redefinition_flag(Klass::MarkedAsAffected);
  }


  TRACE_RC1("Identified links between classes! ");

  for (int i=0; i < _affected_klasses->length(); i++) {
    instanceKlassHandle klass = _affected_klasses->at(i);

    if (klass->check_redefinition_flag(Klass::MarkedAsAffected)) {
      klass->clear_redefinition_flag(Klass::MarkedAsAffected);
      klassOop superKlass = klass->super();
      if (_affected_klasses->contains(superKlass)) {
        links.append(Pair<klassOop, klassOop>(superKlass, klass()));
      }

      objArrayOop superInterfaces = klass->local_interfaces();
      for (int j=0; j<superInterfaces->length(); j++) {
        klassOop interfaceKlass = (klassOop)superInterfaces->obj_at(j);
        if (_affected_klasses->contains(interfaceKlass)) {
          links.append(Pair<klassOop, klassOop>(interfaceKlass, klass()));
        }
      }
    }
  }

  IF_TRACE_RC2 {
    TRACE_RC2("Identified links: ");
    for (int i=0; i<links.length(); i++) {
      TRACE_RC2("%s to %s", links.at(i).left()->klass_part()->name()->as_C_string(),
        links.at(i).right()->klass_part()->name()->as_C_string());
    }
  }

  for (int i = 0; i < _affected_klasses->length(); i++) {
    int j;
    for (j = i; j < _affected_klasses->length(); j++) {
      // Search for node with no incoming edges
      klassOop oop = _affected_klasses->at(j)();
      int k = links.find(oop, match_right);
      if (k == -1) break;
    }
    if (j == _affected_klasses->length()) {
      return JVMTI_ERROR_CIRCULAR_CLASS_DEFINITION;
    }

    // Remove all links from this node
    klassOop oop = _affected_klasses->at(j)();
    int k = 0;
    while (k < links.length()) {
      if (links.adr_at(k)->left() == oop) {
        links.delete_at(k);
      } else {
        k++;
      }
    }

    // Swap node
    instanceKlassHandle tmp = _affected_klasses->at(j);
    _affected_klasses->at_put(j, _affected_klasses->at(i));
    _affected_klasses->at_put(i, tmp);
  }

  return JVMTI_ERROR_NONE;
}

// This internal class transfers the native function registration from old methods
// to new methods.  It is designed to handle both the simple case of unchanged
// native methods and the complex cases of native method prefixes being added and/or
// removed.
// It expects only to be used during the VM_RedefineClasses op (a safepoint).
//
// This class is used after the new methods have been installed in "the_class".
//
// So, for example, the following must be handled.  Where 'm' is a method and
// a number followed by an underscore is a prefix.
//
//                                      Old Name    New Name
// Simple transfer to new method        m       ->  m
// Add prefix                           m       ->  1_m
// Remove prefix                        1_m     ->  m
// Simultaneous add of prefixes         m       ->  3_2_1_m
// Simultaneous removal of prefixes     3_2_1_m ->  m
// Simultaneous add and remove          1_m     ->  2_m
// Same, caused by prefix removal only  3_2_1_m ->  3_2_m
//
class TransferNativeFunctionRegistration {
private:
  instanceKlassHandle the_class;
  int prefix_count;
  char** prefixes;

  // Recursively search the binary tree of possibly prefixed method names.
  // Iteration could be used if all agents were well behaved. Full tree walk is
  // more resilent to agents not cleaning up intermediate methods.
  // Branch at each depth in the binary tree is:
  //    (1) without the prefix.
  //    (2) with the prefix.
  // where 'prefix' is the prefix at that 'depth' (first prefix, second prefix,...)
  methodOop search_prefix_name_space(int depth, char* name_str, size_t name_len,
    Symbol* signature) {
      Symbol* name_symbol = SymbolTable::probe(name_str, (int)name_len);
      if (name_symbol != NULL) {
        methodOop method = Klass::cast(the_class()->klass_part()->new_version())->lookup_method(name_symbol, signature);
        if (method != NULL) {
          // Even if prefixed, intermediate methods must exist.
          if (method->is_native()) {
            // Wahoo, we found a (possibly prefixed) version of the method, return it.
            return method;
          }
          if (depth < prefix_count) {
            // Try applying further prefixes (other than this one).
            method = search_prefix_name_space(depth+1, name_str, name_len, signature);
            if (method != NULL) {
              return method; // found
            }

            // Try adding this prefix to the method name and see if it matches
            // another method name.
            char* prefix = prefixes[depth];
            size_t prefix_len = strlen(prefix);
            size_t trial_len = name_len + prefix_len;
            char* trial_name_str = NEW_RESOURCE_ARRAY(char, trial_len + 1);
            strcpy(trial_name_str, prefix);
            strcat(trial_name_str, name_str);
            method = search_prefix_name_space(depth+1, trial_name_str, trial_len,
              signature);
            if (method != NULL) {
              // If found along this branch, it was prefixed, mark as such
              method->set_is_prefixed_native();
              return method; // found
            }
          }
        }
      }
      return NULL;  // This whole branch bore nothing
  }

  // Return the method name with old prefixes stripped away.
  char* method_name_without_prefixes(methodOop method) {
    Symbol* name = method->name();
    char* name_str = name->as_utf8();

    // Old prefixing may be defunct, strip prefixes, if any.
    for (int i = prefix_count-1; i >= 0; i--) {
      char* prefix = prefixes[i];
      size_t prefix_len = strlen(prefix);
      if (strncmp(prefix, name_str, prefix_len) == 0) {
        name_str += prefix_len;
      }
    }
    return name_str;
  }

  // Strip any prefixes off the old native method, then try to find a
  // (possibly prefixed) new native that matches it.
  methodOop strip_and_search_for_new_native(methodOop method) {
    ResourceMark rm;
    char* name_str = method_name_without_prefixes(method);
    return search_prefix_name_space(0, name_str, strlen(name_str),
      method->signature());
  }

public:

  // Construct a native method transfer processor for this class.
  TransferNativeFunctionRegistration(instanceKlassHandle _the_class) {
    assert(SafepointSynchronize::is_at_safepoint(), "sanity check");

    the_class = _the_class;
    prefixes = JvmtiExport::get_all_native_method_prefixes(&prefix_count);
  }

  // Attempt to transfer any of the old or deleted methods that are native
  void transfer_registrations(instanceKlassHandle old_klass, int* old_methods, int methods_length) {
    for (int j = 0; j < methods_length; j++) {
      methodOop old_method = (methodOop)old_klass->methods()->obj_at(old_methods[j]);

      if (old_method->is_native() && old_method->has_native_function()) {
        methodOop new_method = strip_and_search_for_new_native(old_method);
        if (new_method != NULL) {
          // Actually set the native function in the new method.
          // Redefine does not send events (except CFLH), certainly not this
          // behind the scenes re-registration.
          new_method->set_native_function(old_method->native_function(),
            !methodOopDesc::native_bind_event_is_interesting);

          TRACE_RC3("Transfering native function for method %s", old_method->name()->as_C_string());
        }
      }
    }
  }
};

// Don't lose the association between a native method and its JNI function.
void VM_RedefineClasses::transfer_old_native_function_registrations(instanceKlassHandle old_klass) {
  TransferNativeFunctionRegistration transfer(old_klass);
  transfer.transfer_registrations(old_klass, _deleted_methods, _deleted_methods_length);
  transfer.transfer_registrations(old_klass, _matching_old_methods, _matching_methods_length);
}
