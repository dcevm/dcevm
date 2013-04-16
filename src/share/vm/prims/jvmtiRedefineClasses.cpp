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
#include "memory/cardTableRS.hpp"
#include "oops/klassVtable.hpp"
#include "oops/fieldStreams.hpp"
#include "prims/jvmtiImpl.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "prims/jvmtiClassFileReconstituter.hpp"
#include "prims/methodComparator.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/relocator.hpp"
#include "utilities/bitMap.inline.hpp"
#include "compiler/compileBroker.hpp"


objArrayOop VM_RedefineClasses::_old_methods = NULL;
objArrayOop VM_RedefineClasses::_new_methods = NULL;
int*        VM_RedefineClasses::_matching_old_methods = NULL;
int*        VM_RedefineClasses::_matching_new_methods = NULL;
int*        VM_RedefineClasses::_deleted_methods      = NULL;
int*        VM_RedefineClasses::_added_methods        = NULL;
int         VM_RedefineClasses::_matching_methods_length = 0;
int         VM_RedefineClasses::_deleted_methods_length  = 0;
int         VM_RedefineClasses::_added_methods_length    = 0;
klassOop    VM_RedefineClasses::_the_class_oop = NULL;

// Holds the revision number of the current class redefinition
int    VM_RedefineClasses::_revision_number = -1;

VM_RedefineClasses::VM_RedefineClasses(jint class_count, const jvmtiClassDefinition *class_defs, JvmtiClassLoadKind class_load_kind)
   : VM_GC_Operation(Universe::heap()->total_full_collections(), GCCause::_jvmti_force_gc) {
  RC_TIMER_START(_timer_total);
  _class_count = class_count;
  _class_defs = class_defs;
  _class_load_kind = class_load_kind;
  _updated_oops = NULL;
  _result = JVMTI_ERROR_NONE;
}

VM_RedefineClasses::~VM_RedefineClasses() {
  {
    MonitorLockerEx ml(RedefinitionSync_lock);
    Threads::set_wait_at_instrumentation_entry(false);
    ml.notify_all();
  }

  unlock_threads();
  RC_TIMER_STOP(_timer_total);

  if (TimeRedefineClasses) {
    tty->print_cr("Timing Prologue:             %d", _timer_prologue.milliseconds());
    tty->print_cr("Timing Class Loading:        %d", _timer_class_loading.milliseconds());
    tty->print_cr("Timing Waiting for Lock:     %d", _timer_wait_for_locks.milliseconds());
    tty->print_cr("Timing Class Linking:        %d", _timer_class_linking.milliseconds());
    tty->print_cr("Timing Check Type:           %d", _timer_check_type.milliseconds());
    tty->print_cr("Timing Prepare Redefinition: %d", _timer_prepare_redefinition.milliseconds());
    tty->print_cr("Timing Redefinition GC:      %d", _timer_redefinition.milliseconds());
    tty->print_cr("Timing Epilogue:             %d", _timer_vm_op_epilogue.milliseconds());
    tty->print_cr("------------------------------------------------------------------");
    tty->print_cr("Total Time:                  %d", _timer_total.milliseconds());
  }
}

// Searches for all affected classes and performs a sorting such that a supertype is always before a subtype.
jvmtiError VM_RedefineClasses::find_sorted_affected_classes(GrowableArray<instanceKlassHandle> *all_affected_klasses) {

  // Create array with all classes for which the redefine command was given
  GrowableArray<instanceKlassHandle> klasses_to_redefine;
  for (int i=0; i<_class_count; i++) {
    oop mirror = JNIHandles::resolve_non_null(_class_defs[i].klass);
    instanceKlassHandle klass_handle(Thread::current(), java_lang_Class::as_klassOop(mirror));
    klasses_to_redefine.append(klass_handle);
    assert(klass_handle->new_version() == NULL, "Must be new class");
  }

  // Find classes not directly redefined, but affected by a redefinition (because one of its supertypes is redefined)
  GrowableArray<instanceKlassHandle> affected_classes;
  FindAffectedKlassesClosure closure(&klasses_to_redefine, &affected_classes);

  // Trace affected classes
  if (RC_TRACE_ENABLED(0x00000001)) {
    RC_TRACE(0x00000001, ("Klasses affected: %d",
      affected_classes.length()));
    for (int i=0; i<affected_classes.length(); i++) {
      RC_TRACE(0x00000001, ("%s",
        affected_classes.at(i)->name()->as_C_string()));
    }
  }

  // Add the array of affected classes and the array of redefined classes to get a list of all classes that need a redefinition
  all_affected_klasses->appendAll(&klasses_to_redefine);
  all_affected_klasses->appendAll(&affected_classes);

  // Sort the affected klasses such that a supertype is always on a smaller array index than its subtype.
  jvmtiError result = do_topological_class_sorting(_class_defs, _class_count, &affected_classes, all_affected_klasses, Thread::current());
  if (RC_TRACE_ENABLED(0x00000001)) {
    RC_TRACE(0x00000001, ("Redefine order: "));
    for (int i=0; i<all_affected_klasses->length(); i++) {
      RC_TRACE(0x00000001, ("%s",
        all_affected_klasses->at(i)->name()->as_C_string()));
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

    } else {

      // it is cached, get it from the cache
      *class_byte_count = the_class->get_cached_class_file_len();
      *class_bytes      = the_class->get_cached_class_file_bytes();
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
  RC_TRACE(0x00000001, ("Redefinition with revision number %d started!", _revision_number));

  assert(Thread::current()->is_Java_thread(), "must be Java thread");
  RC_TIMER_START(_timer_prologue);

  if (!check_arguments()) {
    RC_TIMER_STOP(_timer_prologue);
    return false;
  }

  // We first load new class versions in the prologue, because somewhere down the
  // call chain it is required that the current thread is a Java thread.
  _new_classes = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<instanceKlassHandle>(5, true);
  _result = load_new_class_versions(Thread::current());

  RC_TRACE(0x00000001, ("Loaded new class versions!"));
  if (_result != JVMTI_ERROR_NONE) {
    RC_TRACE(0x00000001, ("error occured: %d!", _result));
    delete _new_classes;
    _new_classes = NULL;
    RC_TIMER_STOP(_timer_prologue);
    return false;
  }

  RC_TRACE(0x00000001, ("nearly finished"));
  VM_GC_Operation::doit_prologue();
  RC_TIMER_STOP(_timer_prologue);
  RC_TRACE(0x00000001, ("doit_prologue finished!"));
  return true;
}

// Checks basic properties of the arguments of the redefinition command.
bool VM_RedefineClasses::check_arguments() {

  if (_class_count == 0) RC_ABORT(JVMTI_ERROR_NONE);
  if (_class_defs == NULL) RC_ABORT(JVMTI_ERROR_NULL_POINTER);
  for (int i = 0; i < _class_count; i++) {
    if (_class_defs[i].klass == NULL) RC_ABORT(JVMTI_ERROR_INVALID_CLASS);
    if (_class_defs[i].class_byte_count == 0) RC_ABORT(JVMTI_ERROR_INVALID_CLASS_FORMAT);
    if (_class_defs[i].class_bytes == NULL) RC_ABORT(JVMTI_ERROR_NULL_POINTER);
  }

  return true;
}

jvmtiError VM_RedefineClasses::check_exception() const {
  Thread* THREAD = Thread::current();
  if (HAS_PENDING_EXCEPTION) {

    Symbol* ex_name = PENDING_EXCEPTION->klass()->klass_part()->name();
    RC_TRACE(0x00000001, ("parse_stream exception: '%s'",
      ex_name->as_C_string()));
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

  RC_TRACE(0x00000001, ("==================================================================="));
  RC_TRACE(0x00000001, ("load new class versions (%d)",
    _class_count));

  // Retrieve an array of all classes that need to be redefined
  GrowableArray<instanceKlassHandle> all_affected_klasses;
  jvmtiError err = find_sorted_affected_classes(&all_affected_klasses);
  if (err != JVMTI_ERROR_NONE) {
    RC_TRACE(0x00000001, ("Error finding sorted affected classes: %d",
      (int)err));
    return err;
  }


  JvmtiThreadState *state = JvmtiThreadState::state_for(JavaThread::current());

  _max_redefinition_flags = Klass::NoRedefinition;
  jvmtiError result = JVMTI_ERROR_NONE;

  for (int i=0; i<all_affected_klasses.length(); i++) {
    RC_TRACE(0x00000002, ("Processing affected class %d of %d",
      i+1, all_affected_klasses.length()));

    instanceKlassHandle the_class = all_affected_klasses.at(i);
    RC_TRACE(0x00000002, ("name=%s",
      the_class->name()->as_C_string()));

    the_class->link_class(THREAD);
    result = check_exception();
    if (result != JVMTI_ERROR_NONE) break;

    // Find new class bytes
    const unsigned char* class_bytes;
    jint class_byte_count;
    jvmtiError error;
    jboolean not_changed;
    if ((error = find_class_bytes(the_class, &class_bytes, &class_byte_count, &not_changed)) != JVMTI_ERROR_NONE) {
      RC_TRACE(0x00000001, ("Error finding class bytes: %d",
        (int)error));
      result = error;
      break;
    }
    assert(class_bytes != NULL && class_byte_count != 0, "Class bytes defined at this point!");


    // Set redefined class handle in JvmtiThreadState class.
    // This redefined class is sent to agent event handler for class file
    // load hook event.
    state->set_class_being_redefined(&the_class, _class_load_kind);

    RC_TRACE(0x00000002, ("Before resolving from stream"));

    RC_TIMER_STOP(_timer_prologue);
    RC_TIMER_START(_timer_class_loading);


    // Parse the stream.
    Handle the_class_loader(THREAD, the_class->class_loader());
    Handle protection_domain(THREAD, the_class->protection_domain());
    Symbol* the_class_sym = the_class->name();
    ClassFileStream st((u1*) class_bytes, class_byte_count, (char *)"__VM_RedefineClasses__");
    instanceKlassHandle new_class(THREAD, SystemDictionary::resolve_from_stream(the_class_sym,
      the_class_loader,
      protection_domain,
      &st,
      true,
      the_class,
      THREAD));

    not_changed = false;

    RC_TIMER_STOP(_timer_class_loading);
    RC_TIMER_START(_timer_prologue);

    RC_TRACE(0x00000002, ("After resolving class from stream!"));
    // Clear class_being_redefined just to be sure.
    state->clear_class_being_redefined();

    result = check_exception();
    if (result != JVMTI_ERROR_NONE) break;

#ifdef ASSERT

    assert(new_class() != NULL, "Class could not be loaded!");
    assert(new_class() != the_class(), "must be different");
    assert(new_class->new_version() == NULL && new_class->old_version() != NULL, "");


    objArrayOop k_interfaces = new_class->local_interfaces();
    for (int j=0; j<k_interfaces->length(); j++) {
      assert(((klassOop)k_interfaces->obj_at(j))->klass_part()->is_newest_version(), "just checking");
    }

    if (!THREAD->is_Compiler_thread()) {

      RC_TRACE(0x00000002, ("name=%s loader="INTPTR_FORMAT" protection_domain="INTPTR_FORMAT" ",
        the_class->name()->as_C_string(),
        (address)(the_class->class_loader()),
        (address)(the_class->protection_domain())));
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

    if (RC_TRACE_ENABLED(0x00000001)) {
      if (new_class->layout_helper() != the_class->layout_helper()) {
        RC_TRACE(0x00000001, ("Instance size change for class %s: new=%d old=%d",
         new_class->name()->as_C_string(),
         new_class->layout_helper(),
         the_class->layout_helper()));
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
    } else {
      jvmtiError allowed = check_redefinition_allowed(new_class);
      if (allowed != JVMTI_ERROR_NONE) {
        RC_TRACE(0x00000001, ("Error redefinition not allowed!"));
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
      assert(new_class->layout_helper() >> 1 == new_class->old_version()->klass_part()->layout_helper() >> 1, "must be equal");
      assert(new_class->fields()->length() == ((instanceKlass*)new_class->old_version()->klass_part())->fields()->length(), "must be equal");

      fieldDescriptor fd_new;
      fieldDescriptor fd_old;
      for (JavaFieldStream fs(new_class); !fs.done(); fs.next()) {
        fd_new.initialize(new_class(), fs.index());
        fd_old.initialize(new_class->old_version(), fs.index());
        transfer_special_access_flags(&fd_old, &fd_new);
      }
    }

    if (RC_TRACE_ENABLED(0x00000008)) {
      if (new_class->super() != NULL) {
        RC_TRACE(0x00000008, ("Super class is %s",
          new_class->super()->klass_part()->name()->as_C_string()));
      }
    }

#ifdef ASSERT
    assert(new_class->super() == NULL || new_class->super()->klass_part()->new_version() == NULL, "Super klass must be newest version!");

    the_class->vtable()->verify(tty);
    new_class->vtable()->verify(tty);
#endif

    RC_TRACE(0x00000002, ("Verification done!"));

    if (i == all_affected_klasses.length() - 1) {

      // This was the last class processed => check if additional classes have been loaded in the meantime

      RC_TIMER_STOP(_timer_prologue);
      lock_threads();
      RC_TIMER_START(_timer_prologue);

      for (int j=0; j<all_affected_klasses.length(); j++) {

        klassOop initial_klass = all_affected_klasses.at(j)();
        Klass *initial_subklass = initial_klass->klass_part()->subklass();
        Klass *cur_klass = initial_subklass;
        while(cur_klass != NULL) {

          if(cur_klass->oop_is_instance() && cur_klass->is_newest_version()) {
            instanceKlassHandle handle(THREAD, cur_klass->as_klassOop());
            if (!all_affected_klasses.contains(handle)) {

              int k = i + 1;
              for (; k<all_affected_klasses.length(); k++) {
                if (all_affected_klasses.at(k)->is_subtype_of(cur_klass->as_klassOop())) {
                  break;
                }
              }
              all_affected_klasses.insert_before(k, handle);
              RC_TRACE(0x00000002, ("Adding newly loaded class to affected classes: %s",
                cur_klass->name()->as_C_string()));
            }
          }

          cur_klass = cur_klass->next_sibling();
        }
      }

      int new_count = all_affected_klasses.length() - 1 - i;
      if (new_count != 0) {

        unlock_threads();
        RC_TRACE(0x00000001, ("Found new number of affected classes: %d",
          new_count));
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
  for (int i=0; i<all_affected_klasses.length(); i++) {
    instanceKlassHandle the_class = all_affected_klasses.at(i);
    instanceKlassHandle new_class(the_class->new_version());

    RC_TRACE(0x00000002, ("Linking class %d/%d %s",
      i,
      all_affected_klasses.length(),
      the_class->name()->as_C_string()));
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

  RC_TRACE(0x00000002, ("All classes loaded!"));

#ifdef ASSERT
  for (int i=0; i<all_affected_klasses.length(); i++) {
    instanceKlassHandle the_class = all_affected_klasses.at(i);
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

  RC_TRACE(0x00000001, ("Finished verification!"));
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

  RC_TRACE(0x00000002, ("Locked %d compiler threads", cnt));

  cnt = 0;
  javaThread = Threads::first();
  while (javaThread != NULL) {
    if (javaThread != Thread::current()) {
      javaThread->redefine_classes_mutex()->lock();
    }
    javaThread = javaThread->next();
  }


  RC_TRACE(0x00000002, ("Locked %d threads", cnt));

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

  RC_TRACE(0x00000002, ("Unlocked %d compiler threads", cnt));

  cnt = 0;
  javaThread = Threads::first();
  while (javaThread != NULL) {
    if (javaThread != Thread::current()) {
      if (javaThread->redefine_classes_mutex()->owned_by_self()) {
        javaThread->redefine_classes_mutex()->unlock();
      }
    }
    javaThread = javaThread->next();
  }

  RC_TRACE(0x00000002, ("Unlocked %d threads", cnt));
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



  RC_TRACE(0x00000002, ("Comparing different class versions of class %s",
    new_class->name()->as_C_string()));

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
        RC_TRACE(0x00000002, ("Removed super class %s",
          cur_klass->klass_part()->name()->as_C_string()));
        result = result | Klass::RemoveSuperType | Klass::ModifyInstances | Klass::ModifyClass;

        if (!cur_klass->klass_part()->has_subtype_changed()) {
          RC_TRACE(0x00000002, ("Subtype changed of class %s",
            cur_klass->klass_part()->name()->as_C_string()));
          cur_klass->klass_part()->set_subtype_changed(true);
        }
      }

      cur_klass = cur_klass->klass_part()->super();
    }

    cur_klass = new_class->super();
    while (cur_klass != NULL) {
      if (!the_class->is_subclass_of(cur_klass->klass_part()->old_version())) {
        RC_TRACE(0x00000002, ("Added super class %s",
          cur_klass->klass_part()->name()->as_C_string()));
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
      RC_TRACE(0x00000002, ("Removed interface %s",
        old_interface->name()->as_C_string()));
      
      if (!old_interface->has_subtype_changed()) {
        RC_TRACE(0x00000002, ("Subtype changed of interface %s",
          old_interface->name()->as_C_string()));
        old_interface->set_subtype_changed(true);
      }
    }
  }

  // Interfaces added?
  objArrayOop new_interfaces = new_class->transitive_interfaces();
  for (i = 0; i<new_interfaces->length(); i++) {
    if (!the_class->implements_interface_any_version((klassOop)new_interfaces->obj_at(i))) {
      result = result | Klass::ModifyClass;
      RC_TRACE(0x00000002, ("Added interface %s",
        ((klassOop)new_interfaces->obj_at(i))->klass_part()->name()->as_C_string()));
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
      // (tw) Can this have any effects?
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
        RC_TRACE(0x00000002, ("swapping idnum of new and old method %d / %d!",
          new_num,
          old_num));
       // swap_all_method_annotations(old_num, new_num, new_class);
      }
    }
    RC_TRACE(0x00008000, ("Method matched: new: %s [%d] == old: %s [%d]",
      k_new_method->name_and_sig_as_C_string(), ni,
      k_old_method->name_and_sig_as_C_string(), oi));
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
      //swap_all_method_annotations(new_num, num, new_class);
    }
    RC_TRACE(0x00000001, ("Method added: new: %s [%d]",
      k_new_method->name_and_sig_as_C_string(), ni));
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
    RC_TRACE(0x00000001, ("Method deleted: old: %s [%d]",
      k_old_method->name_and_sig_as_C_string(), oi));
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

  methodHandle instanceTransformerMethod(new_class->find_method(vmSymbols::transformer_name(), vmSymbols::void_method_signature()));
  if (!instanceTransformerMethod.is_null() && !instanceTransformerMethod->is_static()) {
    result |= Klass::HasInstanceTransformer;
  }

  // (tw) Check method bodies to be able to return NoChange?
  return result;
}

void VM_RedefineClasses::calculate_instance_update_information(klassOop new_version) {

  class UpdateFieldsEvolutionClosure : public FieldEvolutionClosure {

  private:

    GrowableArray<int> info;
    int curPosition;
    bool copy_backwards;

  public:

    bool does_copy_backwards() {
      return copy_backwards;
    }

    UpdateFieldsEvolutionClosure(klassOop klass) {

      int base_offset = instanceOopDesc::base_offset_in_bytes();

      if (klass->klass_part()->newest_version() == SystemDictionary::Reference_klass()->klass_part()->newest_version()) {
        base_offset += java_lang_ref_Reference::number_of_fake_oop_fields*size_of_type(T_OBJECT);
      }

      info.append(base_offset);
      info.append(0);
      curPosition = base_offset;
      copy_backwards = false;
    }

    GrowableArray<int> &finish() {
      info.append(0);
      return info;
    }

    virtual void do_new_field(fieldDescriptor* fd){
      int alignment = fd->offset() - curPosition;
      if (alignment > 0) {
        // This field was aligned, so we need to make sure that we fill the gap
        fill(alignment);
      }

      int size = size_of_type(fd->field_type());
      fill(size);
    }

  private:

    void fill(int size) {
      if (info.length() > 0 && info.at(info.length() - 1) < 0) {
        (*info.adr_at(info.length() - 1)) -= size;
      } else {
        info.append(-size);
      }

      curPosition += size;
    }

    int size_of_type(BasicType type) {
      int size = 0;
      switch(type) {
        case T_BOOLEAN:
          size = sizeof(jboolean);
          break;

        case T_CHAR:
          size = (sizeof(jchar));
          break;

        case T_FLOAT:
          size = (sizeof(jfloat));
          break;

        case T_DOUBLE:
          size = (sizeof(jdouble));
          break;

        case T_BYTE:
          size = (sizeof(jbyte));
          break;

        case T_SHORT:
          size = (sizeof(jshort));
          break;

        case T_INT:
          size = (sizeof(jint));
          break;

        case T_LONG:
          size = (sizeof(jlong));
          break;

        case T_OBJECT:
        case T_ARRAY:
          if (UseCompressedOops) {
            size = sizeof(narrowOop);
          } else {
            size = (sizeof(oop));
          }
          break;

        default:
          ShouldNotReachHere();
      }

      assert(size > 0, "");
      return size;

    }
    
  public:

    virtual void do_old_field(fieldDescriptor* fd){}

    virtual void do_changed_field(fieldDescriptor* old_fd, fieldDescriptor *new_fd){

      int alignment = new_fd->offset() - curPosition;
      if (alignment > 0) {
        // This field was aligned, so we need to make sure that we fill the gap
        fill(alignment);
      }

      assert(old_fd->field_type() == new_fd->field_type(), "");
      assert(curPosition == new_fd->offset(), "must be correct offset!");

      int offset = old_fd->offset();
      int size = size_of_type(old_fd->field_type());

      int prevEnd = -1;
      if (info.length() > 0 && info.at(info.length() - 1) > 0) {
        prevEnd = info.at(info.length() - 2) + info.at(info.length() - 1);
      }

      if (prevEnd == offset) {
        info.at_put(info.length() - 2, info.at(info.length() - 2) + size);
      } else {
        info.append(size);
        info.append(offset);
      }

      if (old_fd->offset() < new_fd->offset()) {
        copy_backwards = true;
      }

      transfer_special_access_flags(old_fd, new_fd);

      curPosition += size;
    }
  };

  UpdateFieldsEvolutionClosure cl(new_version);
  ((instanceKlass*)new_version->klass_part())->do_fields_evolution(&cl);

  GrowableArray<int> result = cl.finish();
  ((instanceKlass*)new_version->klass_part())->store_update_information(result);
  ((instanceKlass*)new_version->klass_part())->set_copying_backwards(cl.does_copy_backwards());

  if (RC_TRACE_ENABLED(0x00000002))  {
    RC_TRACE(0x00000002, ("Instance update information for %s:",
      new_version->klass_part()->name()->as_C_string()));
    if (cl.does_copy_backwards()) {
      RC_TRACE(0x00000002, ("\tDoes copy backwards!"));
    }
    for (int i=0; i<result.length(); i++) {
      int curNum = result.at(i);
      if (curNum < 0) {
        RC_TRACE(0x00000002, ("\t%d CLEAN", curNum));
      } else if (curNum > 0) {
        RC_TRACE(0x00000002, ("\t%d COPY from %d", curNum, result.at(i + 1)));
        i++;
      } else {
        RC_TRACE(0x00000002, ("\tEND"));
      }
    }
  }
}

Symbol* VM_RedefineClasses::signature_to_class_name(Symbol* signature) {
  assert(FieldType::is_obj(signature), "");
  return SymbolTable::new_symbol(signature->as_C_string() + 1, signature->utf8_length() - 2, Thread::current());
}

void VM_RedefineClasses::calculate_type_check_information(klassOop klass) {
  if (klass->klass_part()->is_redefining()) {
    klass = klass->klass_part()->old_version();
  }

  // We found an instance klass!
  instanceKlass *cur_instance_klass = instanceKlass::cast(klass);
  GrowableArray< Pair<int, klassOop> > type_check_information;

  class MyFieldClosure : public FieldClosure {

  public:

    GrowableArray< Pair<int, klassOop> > *_arr;

    MyFieldClosure(GrowableArray< Pair<int, klassOop> > *arr) {
      _arr = arr;
    }

    virtual void do_field(fieldDescriptor* fd) {
      if (fd->field_type() == T_OBJECT) {
        Symbol* signature = fd->signature();
        if (FieldType::is_obj(signature)) {
          Symbol* name = signature_to_class_name(signature);
          klassOop field_klass;
          if (is_field_dangerous(name, fd, field_klass)) {
            RC_TRACE(0x00000002, ("Found dangerous field %s in klass %s of type %s",
              fd->name()->as_C_string(),
              fd->field_holder()->klass_part()->name()->as_C_string(),
              name->as_C_string()));
            _arr->append(Pair<int, klassOop>(fd->offset(), field_klass->klass_part()->newest_version()));
          }
        }

        // Array fields can never be a problem!
      }
    }

    bool is_field_dangerous(Symbol* klass_name, fieldDescriptor *fd, klassOop &field_klass) {
      field_klass = SystemDictionary::find(klass_name, fd->field_holder()->klass_part()->class_loader(),
              fd->field_holder()->klass_part()->protection_domain(), Thread::current());
      if(field_klass != NULL) {
        if (field_klass->klass_part()->is_redefining()) {
          field_klass = field_klass->klass_part()->old_version();
        }
        if (field_klass->klass_part()->has_subtype_changed()) {
          return true;
        }
      }
      return false;
    }
  };

  MyFieldClosure fieldClosure(&type_check_information);
  cur_instance_klass->do_nonstatic_fields(&fieldClosure);

  if (type_check_information.length() > 0) {
    type_check_information.append(Pair<int, klassOop>(-1, NULL));
    cur_instance_klass->store_type_check_information(type_check_information);
  }
}

bool VM_RedefineClasses::check_field_value_types() {

  Thread *THREAD = Thread::current();
  class CheckFieldTypesClosure : public ObjectClosure {

  private:

    bool _result;

  public:

    CheckFieldTypesClosure() {
      _result = true;
    }

    bool result() { return _result; }

    virtual void do_object(oop obj) {

      if (!_result) {
        return;
      }

      if (obj->is_objArray()) {

        objArrayOop array = objArrayOop(obj);

        klassOop element_klass = objArrayKlass::cast(array->klass())->element_klass();

        if (element_klass->klass_part()->has_subtype_changed()) {
          int length = array->length();
          for (int i=0; i<length; i++) {
            oop element = array->obj_at(i);
            if (element != NULL && element->blueprint()->newest_version()->klass_part()->is_redefining()) {
              // Check subtype relationship to static type of array
              if (!element->blueprint()->newest_version()->klass_part()->is_subtype_of(element_klass->klass_part()->newest_version())) {
                RC_TRACE(0x00000001, ("Array value is INVALID - abort redefinition (static_type=%s, index=%d, dynamic_type=%s)",
                  element_klass->klass_part()->name()->as_C_string(),
                  i,
                  element->blueprint()->name()->as_C_string()));
                _result = false;
                break;
              }
            }
          }
        }

      } else {
        Pair<int, klassOop> *cur = obj->klass()->klass_part()->type_check_information();
        if (cur != NULL) {
          // Type check information exists for this oop
          while ((*cur).left() != -1) {
            check_field(obj, (*cur).left(), (*cur).right());
            cur++;
          }
        }
      }
    }

    void check_field(oop obj, int offset, klassOop static_type) {
      oop field_value = obj->obj_field(offset);
      if (field_value != NULL) {
        // Field is not null
        if (field_value->klass()->klass_part()->newest_version()->klass_part()->is_subtype_of(static_type)) {
          // We are OK
          RC_TRACE(0x00008000, ("Field value is OK (klass=%s, static_type=%s, offset=%d, dynamic_type=%s)",
            obj->klass()->klass_part()->name()->as_C_string(),
            static_type->klass_part()->name()->as_C_string(),
            offset,
            field_value->klass()->klass_part()->name()->as_C_string()));
        } else {
          // Failure!
          RC_TRACE(0x00000001, ("Field value is INVALID - abort redefinition (klass=%s, static_type=%s, offset=%d, dynamic_type=%s)",
            obj->klass()->klass_part()->name()->as_C_string(),
            static_type->klass_part()->name()->as_C_string(),
            offset,
            field_value->klass()->klass_part()->name()->as_C_string()));
          _result = false;
        }
      }
    }
  };

  CheckFieldTypesClosure myObjectClosure;

  // make sure that heap is parsable (fills TLABs with filler objects)
  Universe::heap()->ensure_parsability(false);  // no need to retire TLABs

  // do the iteration
  // If this operation encounters a bad object when using CMS,
  // consider using safe_object_iterate() which avoids perm gen
  // objects that may contain bad references.
  Universe::heap()->object_iterate(&myObjectClosure);

  // when sharing is enabled we must iterate over the shared spaces
  if (UseSharedSpaces) {
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    CompactingPermGenGen* gen = (CompactingPermGenGen*)gch->perm_gen();
    gen->ro_space()->object_iterate(&myObjectClosure);
    gen->rw_space()->object_iterate(&myObjectClosure);
  }

  return myObjectClosure.result();
}

void VM_RedefineClasses::clear_type_check_information(klassOop k) {
  if (k->klass_part()->is_redefining()) {
    k = k->klass_part()->old_version();
  }

  // We found an instance klass!
  instanceKlass *cur_instance_klass = instanceKlass::cast(k);
  cur_instance_klass->clear_type_check_information();
}

void VM_RedefineClasses::update_active_methods() {

  RC_TRACE(0x00000002, ("Updating active methods"));
  JavaThread *java_thread = Threads::first();
  while (java_thread != NULL) {

    int stack_depth = 0;
    if (java_thread->has_last_Java_frame()) {

      RC_TRACE(0x0000000400, ("checking stack of Java thread %s", java_thread->name()));

      // vframes are resource allocated
      Thread* current_thread = Thread::current();
      ResourceMark rm(current_thread);
      HandleMark hm(current_thread);

      RegisterMap reg_map(java_thread);
      frame f = java_thread->last_frame();
      vframe* vf = vframe::new_vframe(&f, &reg_map, java_thread);
      frame* last_entry_frame = NULL;

      while (vf != NULL) {
        if (vf->is_java_frame()) {
          // java frame (interpreted, compiled, ...)
          javaVFrame *jvf = javaVFrame::cast(vf);

          if (!(jvf->method()->is_native())) {
            int bci = jvf->bci();
            RC_TRACE(0x00000400, ("found method: %s / bci=%d", jvf->method()->name()->as_C_string(), bci));
            ResourceMark rm(Thread::current());
            HandleMark hm;
            instanceKlassHandle klass(jvf->method()->method_holder());

            if (jvf->method()->new_version() != NULL && jvf->is_interpreted_frame()) {

              
              RC_TRACE(0x00000002, ("Found method that should just be updated to the newest version %s",
                jvf->method()->name_and_sig_as_C_string()));

              if (RC_TRACE_ENABLED(0x01000000)) {
                int code_size = jvf->method()->code_size();
                char *code_base_old = (char*)jvf->method()->code_base();
                char *code_base_new = (char*)jvf->method()->new_version()->code_base();
                for (int i=0; i<code_size; i++) {
                  tty->print_cr("old=%d new=%d", *code_base_old++, *code_base_new++);
                }
                jvf->method()->print_codes_on(tty);
                jvf->method()->new_version()->print_codes_on(tty);
              }
              
              assert(jvf->is_interpreted_frame(), "Every frame must be interpreted!");              
              interpretedVFrame *iframe = (interpretedVFrame *)jvf;


              if (RC_TRACE_ENABLED(0x01000000)) {
                constantPoolCacheOop cp_old = jvf->method()->constants()->cache();
                tty->print_cr("old cp");
                for (int i=0; i<cp_old->length(); i++) {
                  cp_old->entry_at(i)->print(tty, i);
                }
                constantPoolCacheOop cp_new = jvf->method()->new_version()->constants()->cache();
                tty->print_cr("new cp");
                for (int i=0; i<cp_new->length(); i++) {
                  cp_new->entry_at(i)->print(tty, i);
                }
              }

              iframe->set_method(jvf->method()->new_version(), bci);
              RC_TRACE(0x00000002, ("Updated method to newer version"));
              assert(jvf->method()->new_version() == NULL, "must be latest version");

            }
          } 
        }
        vf = vf->sender();
      }
    }

    // Advance to next thread
    java_thread = java_thread->next();
  }
}

void VM_RedefineClasses::method_forwarding() {

  int forwarding_count = 0;
  JavaThread *java_thread = Threads::first();
  while (java_thread != NULL) {

    int stack_depth = 0;
    if (java_thread->has_last_Java_frame()) {

      RC_TRACE(0x00000400, ("checking stack of Java thread %s", java_thread->name()));

      // vframes are resource allocated
      Thread* current_thread = Thread::current();
      ResourceMark rm(current_thread);
      HandleMark hm(current_thread);

      RegisterMap reg_map(java_thread);
      frame f = java_thread->last_frame();
      vframe* vf = vframe::new_vframe(&f, &reg_map, java_thread);
      frame* last_entry_frame = NULL;

      while (vf != NULL) {
        if (vf->is_java_frame()) {
          // java frame (interpreted, compiled, ...)
          javaVFrame *jvf = javaVFrame::cast(vf);

          if (!(jvf->method()->is_native())) {
            RC_TRACE(0x00008000, ("found method: %s",
              jvf->method()->name()->as_C_string()));
            ResourceMark rm(Thread::current());
            HandleMark hm;
            instanceKlassHandle klass(jvf->method()->method_holder());
            methodOop m = jvf->method();
            int bci = jvf->bci();
            RC_TRACE(0x00008000, ("klass redef %d",
              klass->is_redefining()));

            if (klass->new_version() != NULL && m->new_version() == NULL) {
              RC_TRACE(0x00008000, ("found potential forwarding method: %s",
                m->name()->as_C_string()));
              
              klassOop new_klass = klass->newest_version();
              methodOop new_method = new_klass->klass_part()->lookup_method(m->name(), m->signature());
              RC_TRACE(0x00000002, ("%d %d",
                new_method,
                new_method->constMethod()->has_code_section_table()));

              if (new_method != NULL && new_method->constMethod()->has_code_section_table()) {
                RC_TRACE(0x00008000, ("found code section table for method: %s",
                  new_method->name()->as_C_string()));
                m->set_forward_method(new_method);
                if (new_method->max_locals() != m->max_locals()) {
                  tty->print_cr("new_m max locals: %d old_m max locals: %d", new_method->max_locals(), m->max_locals());
                }
                assert(new_method->max_locals() == m->max_locals(), "number of locals must match");
                assert(new_method->max_stack() == m->max_stack(), "number of stack values must match");
                if (jvf->is_interpreted_frame()) {
                  if (m->is_in_code_section(bci)) {
                    // We must transfer now and cannot delay until next NOP.
                    int new_bci = m->calculate_forward_bci(bci, new_method);
                    interpretedVFrame* iframe = interpretedVFrame::cast(jvf);
                    RC_TRACE(0x00000002, ("Transfering execution of %s to new method old_bci=%d new_bci=%d",
                      new_method->name()->as_C_string(),
                      bci,
                      new_bci));
                    iframe->set_method(new_method, new_bci);
                  } else {
                    RC_TRACE(0x00000002, ("Delaying method forwarding of %s because %d is not in a code section",
                      new_method->name()->as_C_string(),
                      bci));
                  }
                } else {
                  RC_TRACE(0x00000002, ("Delaying method forwarding of %s because method is compiled",
                    new_method->name()->as_C_string()));
                }
              }
            }
          } 
        }
        vf = vf->sender();
      }
    }

    // Advance to next thread
    java_thread = java_thread->next();
  }

  RC_TRACE(0x00000001, ("Method forwarding applied to %d methods",
    forwarding_count));
}

bool VM_RedefineClasses::check_method_stacks() {

  JavaThread *java_thread = Threads::first();
  while (java_thread != NULL) {

    int stack_depth = 0;
    if (java_thread->has_last_Java_frame()) {

      RC_TRACE(0x00000400, ("checking stack of Java thread %s", java_thread->name()));

      // vframes are resource allocated
      Thread* current_thread = Thread::current();
      ResourceMark rm(current_thread);
      HandleMark hm(current_thread);

      RegisterMap reg_map(java_thread);
      frame f = java_thread->last_frame();
      vframe* vf = vframe::new_vframe(&f, &reg_map, java_thread);
      frame* last_entry_frame = NULL;

      while (vf != NULL) {
        if (vf->is_java_frame()) {
          // java frame (interpreted, compiled, ...)
          javaVFrame *jvf = javaVFrame::cast(vf);

          if (!(jvf->method()->is_native())) {
            RC_TRACE(0x00000400, ("found method: %s", jvf->method()->name()->as_C_string()));
            ResourceMark rm(Thread::current());
            HandleMark hm;
            instanceKlassHandle klass(jvf->method()->method_holder());

            StackValueCollection *locals = jvf->locals();
            const size_t message_buffer_len = klass->name()->utf8_length() + 1024;
            char* message_buffer = NEW_RESOURCE_ARRAY(char, message_buffer_len);

            for (int i=0; i<locals->size(); i++) {
              StackValue *stack_value = locals->at(i);
              if (stack_value->type() == T_OBJECT) {
                Handle obj = stack_value->get_obj();
                if (!obj.is_null() && obj->klass()->klass_part()->newest_version()->klass_part()->check_redefinition_flag(Klass::RemoveSuperType)) {
                  
                  // OK, so this is a possible failure => check local variable table, if it could be OK.
                  bool result = false;
                  methodOop method = jvf->method();
                  if (method->has_localvariable_table()) {
                    LocalVariableTableElement *elem = jvf->method()->localvariable_table_start();
                    for (int j=0; j<method->localvariable_table_length(); j++) {

                      if (elem->slot == i) {

                        // Matching index found

                        if (elem->start_bci <= jvf->bci() && elem->start_bci + elem->length > jvf->bci()) {

                          // Also in range!!
                          Symbol* signature = jvf->method()->constants()->symbol_at(elem->descriptor_cp_index);
                          Symbol* klass_name = signature_to_class_name(signature);

                          klassOop local_klass = SystemDictionary::find(klass_name, jvf->method()->method_holder()->klass_part()->class_loader(), jvf->method()->method_holder()->klass_part()->protection_domain(), Thread::current())->klass_part()->newest_version();
                          klassOop cur = obj->klass()->klass_part()->newest_version();

                          // Field is not null
                          if (cur->klass_part()->newest_version()->klass_part()->is_subtype_of(local_klass)) {
                            // We are OK
                            RC_TRACE(0x00008000, ("Local variable value is OK (local_klass=%s, cur_klass=%s)",
                              local_klass->klass_part()->name()->as_C_string(), cur->klass_part()->name()->as_C_string()));
                            result = true;
                          } else {
                            // Failure!
                            RC_TRACE(0x00000001, ("Local variable value is INVALID - abort redefinition (local_klass=%s, cur_klass=%s)",
                              local_klass->klass_part()->name()->as_C_string(),
                              cur->klass_part()->name()->as_C_string()));
                            return false;
                          }
                        }
                      }

                      elem++;
                    }
                  } else {
                    RC_TRACE(0x00000002, ("Method %s does not have a local variable table => abort",
                      method->name_and_sig_as_C_string()));
                  }
                  
                  if (!result) {
                    return false;
                  }

                  RC_TRACE(0x00008000, ("Verifying class %s",
                    jvf->method()->method_holder()->klass_part()->name()->as_C_string()));

                  Symbol* exception_name;
                  const size_t message_buffer_len = klass->name()->utf8_length() + 1024;
                  char* message_buffer = NEW_RESOURCE_ARRAY(char, message_buffer_len);

                  Thread::current()->set_pretend_new_universe(true);
                  ClassVerifier split_verifier(klass, Thread::current());
                  split_verifier.verify_method(jvf->method(), Thread::current());
                  exception_name = split_verifier.result();
                  Thread::current()->set_pretend_new_universe(false);

                  if (exception_name != NULL) {
                 
                    RC_TRACE(0x00000001, ("Verification of class %s failed",
                      jvf->method()->method_holder()->klass_part()->name()->as_C_string()));
                    RC_TRACE(0x00000001, ("Exception: %s",
                      exception_name->as_C_string()));
                    RC_TRACE(0x00000001, ("Message: %s",
                      message_buffer));
                    Thread::current()->clear_pending_exception();
                    return false;
                  } 

                }
              }
            }
          } 
        }
        vf = vf->sender();
      }
    }

    // Advance to next thread
    java_thread = java_thread->next();
  }

  return true;
}

bool VM_RedefineClasses::check_method(methodOop method) {


  return true;
}

// Warning: destroys redefinition level values of klasses.
bool VM_RedefineClasses::check_loaded_methods() {

  class CheckLoadedMethodsClosure : public ObjectClosure {

  private:
    
    bool _result;
    GrowableArray<klassOop> *_dangerous_klasses;

  public:
    CheckLoadedMethodsClosure(GrowableArray<klassOop> *dangerous_klasses) {
      _result = true;
      _dangerous_klasses = dangerous_klasses;
    }

    bool result() {
      return _result;
    }

    bool is_class_dangerous(klassOop k) {
      return k->klass_part()->newest_version()->klass_part()->check_redefinition_flag(Klass::RemoveSuperType);
    }

    bool can_be_affected(instanceKlass *klass) {

      constantPoolOop cp = klass->constants();

      Thread *THREAD = Thread::current();
      klassOop k;
      Symbol* symbol;

      for (int i=1; i<cp->length(); i++) {
        jbyte tag = cp->tag_at(i).value();
        switch(tag) {
          case JVM_CONSTANT_Long:
          case JVM_CONSTANT_Double:
            i++;
            break;

          case JVM_CONSTANT_Utf8:
          case JVM_CONSTANT_Unicode:
          case JVM_CONSTANT_Integer:
          case JVM_CONSTANT_Float:
          case JVM_CONSTANT_String:
          case JVM_CONSTANT_Fieldref:
          case JVM_CONSTANT_Methodref:
          case JVM_CONSTANT_InterfaceMethodref:
          case JVM_CONSTANT_ClassIndex:
          case JVM_CONSTANT_UnresolvedString:
          case JVM_CONSTANT_StringIndex:
          case JVM_CONSTANT_UnresolvedClassInError:
          case JVM_CONSTANT_Object:
            // do nothing
            break;

          case JVM_CONSTANT_Class:
            k = cp->klass_at(i, CHECK_(true));
            if (is_class_dangerous(k)) {
              RC_TRACE(0x00000002, ("Class %s is potentially affected, because at cp[%d] references class %s",
                klass->name()->as_C_string(),
                i,
                k->klass_part()->name()->as_C_string()));
              return true;
            }
            break;
          
          case JVM_CONSTANT_NameAndType:
            symbol = cp->symbol_at(cp->signature_ref_index_at(i));
            if (symbol->byte_at(0) == '(') {
              // This must be a method
              SignatureStream signatureStream(symbol);
              while (true) {

                if (signatureStream.is_array()) {
                  Symbol* cur_signature = signatureStream.as_symbol(Thread::current());
                  if (is_type_signature_dangerous(cur_signature)) {
                    return true;
                  }
                } else if (signatureStream.is_object()) {
                  if (is_symbol_dangerous(signatureStream.as_symbol(Thread::current()))) {
                    return true;
                  }
                } 

                if (signatureStream.at_return_type()) {
                  break;
                }

                signatureStream.next();
              }

            } else if (is_type_signature_dangerous(symbol)) {
              return true;
            }
            break;

          case JVM_CONSTANT_UnresolvedClass:
            symbol = cp->unresolved_klass_at(i);
            if (is_symbol_dangerous(symbol)) {
              return true;
            }
            break;

          default:
            ShouldNotReachHere();
        }
      }

      return false;
    }

    bool is_type_signature_dangerous(Symbol* signature) {
      // This must be a field type
      if (FieldType::is_obj(signature)) {
        Symbol* name = signature_to_class_name(signature);
        if (is_symbol_dangerous(name)) {
          return true;
        }
      } else if (FieldType::is_array(signature)) {
        //jint dimension;
        //Symbol* object_key;
        FieldArrayInfo fd;
        FieldType::get_array_info(signature, fd, Thread::current());
        if (is_symbol_dangerous(fd.object_key())) {
          return true;
        }
      }
      return false;
    }

    bool is_symbol_dangerous(Symbol* symbol) {
      for (int i=0; i<_dangerous_klasses->length(); i++) {
        if(_dangerous_klasses->at(i)->klass_part()->name() == symbol) {
          RC_TRACE(0x00000002, ("Found constant pool index %d references class %s",
            i,
            symbol->as_C_string()));
          return true;
        }
      }
      return false;
    }

    virtual void do_object(oop obj) {

      if (!_result) return;

      klassOop klassObj = (klassOop)obj;
      Thread *THREAD = Thread::current();

      // We found an instance klass!
      instanceKlass *klass = instanceKlass::cast(klassObj);
      instanceKlassHandle handle(klassObj);

      RC_TRACE(0x00000400, ("Check if verification is necessary for class %s major_version=%d", handle->name()->as_C_string(), handle->major_version()));

      if (!can_be_affected(klass)) {
        RC_TRACE(0x00000400, ("Skipping verification of class %s major_version=%d", handle->name()->as_C_string(), handle->major_version()));
        return;
      }

      if (handle->major_version() < Verifier::STACKMAP_ATTRIBUTE_MAJOR_VERSION) {
        RC_TRACE(0x00000001, ("Failing because cannot verify class %s major_version=%d", handle->name()->as_C_string(), handle->major_version()));
        _result = false;
        return;
      }

      RC_TRACE(0x00000001, ("Verifying class %s", handle->name()->as_C_string()));

      if (!Verifier::verify(handle, Verifier::NoException, true, false, Thread::current())) {
        
        RC_TRACE(0x00000001, ("Verification of class %s failed", handle->name()->as_C_string()));
        //Symbol* ex_name = PENDING_EXCEPTION->klass()->klass_part()->name();
        //RC_TRACE(0x00000002, ("exception when verifying class: '%s'", ex_name->as_C_string());
        //PENDING_EXCEPTION->print();
        CLEAR_PENDING_EXCEPTION;
        _result = false;
      } 

      /*int method_count = klass->methods()->length();
      for (int i=0; i<method_count; i++) {
        methodOop cur_method = (methodOop)klass->methods()->obj_at(i);
        if (!check_method(cur_method)) {
          RC_TRACE(0x00000001, ("Failed to verify consistency of method %s of klass %s", cur_method->name()->as_C_string(), klass->name()->as_C_string());
        }
      }*/
    }
  };

  // TODO: Check bytecodes in case of interface => class or class => interface etc..

  GrowableArray<klassOop> dangerous_klasses;
  for (int i=0; i<_new_classes->length(); i++) {
    instanceKlassHandle handle = _new_classes->at(i);
    if (handle->check_redefinition_flag(Klass::RemoveSuperType)) {
      dangerous_klasses.append(handle());
    }
  }

  CheckLoadedMethodsClosure checkLoadedMethodsClosure(&dangerous_klasses);
  Thread::current()->set_pretend_new_universe(true);
  SystemDictionary::classes_do(&checkLoadedMethodsClosure);
  Thread::current()->set_pretend_new_universe(false);


  return checkLoadedMethodsClosure.result();
}

bool VM_RedefineClasses::check_type_consistency() {

  Universe::set_verify_in_progress(true);

  SystemDictionary::classes_do(calculate_type_check_information);
  bool result = check_field_value_types();
  SystemDictionary::classes_do(clear_type_check_information);
  if (!result) {
    RC_TRACE(0x00000001, ("Aborting redefinition because of wrong field or array element value!"));
    Universe::set_verify_in_progress(false);
    return false;
  }

  result = check_method_stacks();
  if (!result) {
    RC_TRACE(0x00000001, ("Aborting redefinition because of wrong value on the stack"));
    Universe::set_verify_in_progress(false);
    return false;
  }

  result = check_loaded_methods();
  if (!result) {
    RC_TRACE(0x00000001, ("Aborting redefinition because of wrong loaded method"));
    Universe::set_verify_in_progress(false);
    return false;
  }

  RC_TRACE(0x00000001, ("Verification passed => hierarchy change is valid!"));
  Universe::set_verify_in_progress(false);
  return true;
}

void VM_RedefineClasses::rollback() {
  RC_TRACE(0x00000001, ("Rolling back redefinition!"));
  SystemDictionary::rollback_redefinition();

  RC_TRACE(0x00000001, ("After rolling back system dictionary!"));
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

template <class T> void VM_RedefineClasses::do_oop_work(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (obj->is_instanceKlass()) {
      klassOop klass = (klassOop)obj;
      // DCEVM: note: can overwrite owner of old_klass constants pool with new_klass, so we need to fix it back later
      if (klass->new_version() != NULL && klass->new_version()->klass_part()->is_redefining()) {
        obj = klass->klass_part()->new_version();
        oopDesc::encode_store_heap_oop_not_null(p, obj);
      }
    } else if (obj->blueprint()->newest_version() == SystemDictionary::Class_klass()->klass_part()->newest_version()) {
      // update references to java.lang.Class to point to newest version. Only update references to non-primitive
      // java.lang.Class instances.
      klassOop klass_oop = java_lang_Class::as_klassOop(obj);
      if (klass_oop != NULL) {
        if (klass_oop->new_version() != NULL && klass_oop->new_version()->klass_part()->is_redefining()) {
          obj = klass_oop->new_version()->java_mirror();
        } else if (klass_oop->klass_part()->is_redefining()) {
          obj = klass_oop->java_mirror();
        }
        oopDesc::encode_store_heap_oop_not_null(p, obj);


        // FIXME: DCEVM: better implementation?
        // Starting from JDK 7 java_mirror can be kept in the regular heap. Therefore, it is possible
        // that new java_mirror is in the young generation whereas p is in tenured generation. In that
        // case we need to run write barrier to make sure card table is properly updated. This will
        // allow JVM to detect reference in tenured generation properly during young generation GC.
        if (Universe::heap()->is_in_reserved(p)) {
          if (GenCollectedHeap::heap()->is_in_young(obj)) {
            GenRemSet* rs = GenCollectedHeap::heap()->rem_set();
            assert(rs->rs_kind() == GenRemSet::CardTable, "Wrong rem set kind.");
            CardTableRS* _rs = (CardTableRS*)rs;
            _rs->inline_write_ref_field_gc(p, obj);
          }
        }
      }
    }
  }
}

void VM_RedefineClasses::swap_marks(oop first, oop second) {
  markOop first_mark = first->mark();
  markOop second_mark = second->mark();
  first->set_mark(second_mark);
  second->set_mark(first_mark);
}

void VM_RedefineClasses::doit() {
  Thread *thread = Thread::current();

  RC_TRACE(0x00000001, ("Entering doit!"));


  if ((_max_redefinition_flags & Klass::RemoveSuperType) != 0) {

    RC_TIMER_START(_timer_check_type);

    if (!check_type_consistency()) {
      // (tw) TODO: Rollback the class redefinition
      rollback();
      RC_TRACE(0x00000001, ("Detected type inconsistency!"));
      _result = JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED;
      RC_TIMER_STOP(_timer_check_type);
      return;
    }

    RC_TIMER_STOP(_timer_check_type);

  } else {
    RC_TRACE(0x00000001, ("No type narrowing => skipping check for type inconsistency"));
  }

  if (UseMethodForwardPoints) {
    RC_TRACE(0x00000001, ("Check stack for forwarding methods to new version"));
    method_forwarding();
  }

  if (UseSharedSpaces) {
    // Sharing is enabled so we remap the shared readonly space to
    // shared readwrite, private just in case we need to redefine
    // a shared class. We do the remap during the doit() phase of
    // the safepoint to be safer.
    if (!CompactingPermGenGen::remap_shared_readonly_as_readwrite()) {
      RC_TRACE(0x00000001, ("failed to remap shared readonly space to readwrite, private"));
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
  RC_TIMER_START(_timer_redefinition);

    class ChangePointersOopClosure : public OopClosure {
      virtual void do_oop(oop* o) {
        do_oop_work(o);
      }

      virtual void do_oop(narrowOop* o) {
        do_oop_work(o);
      }
    };

    class ChangePointersObjectClosure : public ObjectClosure {

    private:

      OopClosure *_closure;
      bool _needs_instance_update;
      GrowableArray<oop> *_updated_oops;

    public:
      ChangePointersObjectClosure(OopClosure *closure) : _closure(closure), _needs_instance_update(false), _updated_oops(NULL) {}

      bool needs_instance_update() {
        return _needs_instance_update;
      }

      GrowableArray<oop> *updated_oops() { return _updated_oops; }

      virtual void do_object(oop obj) {
        if (!obj->is_instanceKlass()) {
          obj->oop_iterate(_closure);
          
          if (obj->blueprint()->is_redefining()) {

            if (obj->blueprint()->check_redefinition_flag(Klass::HasInstanceTransformer)) {
              if (_updated_oops == NULL) {
                _updated_oops = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<oop>(100, true);
              }
              _updated_oops->append(obj);
            }

            if(obj->blueprint()->update_information() != NULL || obj->is_perm()) {

              assert(obj->blueprint()->old_version() != NULL, "must have old version");
              obj->set_klass_no_check(obj->blueprint()->old_version());

              if (obj->size() != obj->size_given_klass(obj->blueprint()->new_version()->klass_part()) || obj->is_perm()) {
                // We need an instance update => set back to old klass
                _needs_instance_update = true;

              } else {
                MarkSweep::update_fields(obj, obj);
                assert(obj->blueprint()->is_redefining(), "update fields resets the klass");
              }
            }
          }

        } else {
          instanceKlass *klass = instanceKlass::cast((klassOop)obj);
          if (klass->is_redefining()) {
            // DCEVM: We need to restorte constants pool owner which was updated by do_oop_work
            instanceKlass* old_klass = instanceKlass::cast(klass->old_version());
            old_klass->constants()->set_pool_holder(klass->old_version());
 
            // Initialize the new class! Special static initialization that does not execute the
            // static constructor but copies static field values from the old class if name
            // and signature of a static field match.
            klass->initialize_redefined_class();
          }
          // idubrov: FIXME: we probably don't need that since oop's will be visited in a regular way...
          // idubrov: need to check if there is a test to verify that fields referencing class being updated
          // idubrov: will get new version of that class
          //klass->iterate_static_fields(_closure);
        }
      }
    };

    ChangePointersOopClosure oopClosure;
    ChangePointersObjectClosure objectClosure(&oopClosure);

    {
      SharedHeap::heap()->gc_prologue(true);
      Universe::root_oops_do(&oopClosure);
      Universe::heap()->object_iterate(&objectClosure);
      SharedHeap::heap()->gc_epilogue(false);
    }

    // Swap marks to have same hashcodes
    for (int i=0; i<_new_classes->length(); i++) {
      swap_marks(_new_classes->at(i)(), _new_classes->at(i)->old_version());
      swap_marks(_new_classes->at(i)->java_mirror(), _new_classes->at(i)->old_version()->java_mirror());
    }

    _updated_oops = objectClosure.updated_oops();

  if (objectClosure.needs_instance_update()){

    // Do a full garbage collection to update the instance sizes accordingly
    RC_TRACE(0x00000001, ("Before performing full GC!"));
    Universe::set_redefining_gc_run(true);
    JvmtiGCMarker jgcm;
    notify_gc_begin(true);
    Universe::heap()->collect_as_vm_thread(GCCause::_heap_inspection);
    notify_gc_end();
    Universe::set_redefining_gc_run(false);
    RC_TRACE(0x00000001, ("GC done!"));
  }


  if (RC_TRACE_ENABLED(0x00000001)) {
    if (_updated_oops != NULL) {
      RC_TRACE(0x00000001, ("%d object(s) updated!", _updated_oops->length()));
    } else {
      RC_TRACE(0x00000001, ("No objects updated!"));
    }
  }

  // Unmark klassOops as "redefining"
  for (int i=0; i<_new_classes->length(); i++) {
    klassOop cur = _new_classes->at(i)();
    _new_classes->at(i)->set_redefining(false);
    _new_classes->at(i)->clear_update_information();
    _new_classes->at(i)->update_supers_to_newest_version();

    if (((instanceKlass *)cur->klass_part()->old_version()->klass_part())->array_klasses() != NULL) {
      update_array_classes_to_newest_version(((instanceKlass *)cur->klass_part()->old_version()->klass_part())->array_klasses());

      // Transfer the array classes, otherwise we might get cast exceptions when casting array types.
      ((instanceKlass*)cur->klass_part())->set_array_klasses(((instanceKlass*)cur->klass_part()->old_version()->klass_part())->array_klasses());

      oop new_mirror = _new_classes->at(i)->java_mirror();
      oop old_mirror = _new_classes->at(i)->old_version()->java_mirror();
      java_lang_Class::set_array_klass(new_mirror, java_lang_Class::array_klass(old_mirror));

      // Transfer init state
      instanceKlass::ClassState state = instanceKlass::cast(cur->old_version())->init_state();
      if (state > instanceKlass::linked) {
        instanceKlass::cast(cur)->call_class_initializer(thread);
      }
    }
  }

  for (int i=T_BOOLEAN; i<=T_LONG; i++) {
    update_array_classes_to_newest_version(Universe::typeArrayKlassObj((BasicType)i));
  }

  // Disable any dependent concurrent compilations
  SystemDictionary::notice_modification();

  // Set flag indicating that some invariants are no longer true.
  // See jvmtiExport.hpp for detailed explanation.
  JvmtiExport::set_has_redefined_a_class();

  // Clean up caches in the compiler interface and compiler threads
  CompileBroker::cleanup_after_redefinition();

#ifdef ASSERT

  // Universe::verify();
  // JNIHandles::verify();

  SystemDictionary::classes_do(check_class, thread);
#endif

  update_active_methods();
  RC_TIMER_STOP(_timer_redefinition);

}

void VM_RedefineClasses::update_array_classes_to_newest_version(klassOop smallest_dimension) {

  arrayKlass *curArrayKlass = arrayKlass::cast(smallest_dimension);
  assert(curArrayKlass->lower_dimension() == NULL, "argument must be smallest dimension");


  while (curArrayKlass != NULL) {
    klassOop higher_dimension = curArrayKlass->higher_dimension();
    klassOop lower_dimension = curArrayKlass->lower_dimension();
    curArrayKlass->update_supers_to_newest_version();

    curArrayKlass = NULL;
    if (higher_dimension != NULL) {
      curArrayKlass = arrayKlass::cast(higher_dimension);
    }
  }

}

void VM_RedefineClasses::doit_epilogue() {

  RC_TIMER_START(_timer_vm_op_epilogue);

  unlock_threads();

  ResourceMark mark;

  VM_GC_Operation::doit_epilogue();
  RC_TRACE(0x00000001, ("GC Operation epilogue finished! "));

  GrowableArray<methodHandle> instanceTransformerMethods;

  // Call static transformers
  for (int i=0; i<_new_classes->length(); i++) {
    
    instanceKlassHandle klass = _new_classes->at(i);
    
    // Find instance transformer method

    if (klass->check_redefinition_flag(Klass::HasInstanceTransformer)) {

      RC_TRACE(0x00008000, ("Call instance transformer of %s instance", klass->name()->as_C_string()));
      klassOop cur_klass = klass();
      while (cur_klass != NULL) {
        methodOop method = ((instanceKlass*)cur_klass->klass_part())->find_method(vmSymbols::transformer_name(), vmSymbols::void_method_signature());
        if (method != NULL) {
          methodHandle instanceTransformerMethod(method);
          instanceTransformerMethods.append(instanceTransformerMethod);
          break;
        } else {
          cur_klass = cur_klass->klass_part()->super();
        }
      }
      assert(cur_klass != NULL, "must have instance transformer method");
    } else {
      instanceTransformerMethods.append(methodHandle(Thread::current(), NULL));
    }
  }


  // Call instance transformers
  if (_updated_oops != NULL) {

    for (int i=0; i<_updated_oops->length(); i++) {
      assert(_updated_oops->at(i) != NULL, "must not be null!");
      Handle cur(_updated_oops->at(i));
      instanceKlassHandle klass(cur->klass());

      if (klass->check_redefinition_flag(Klass::HasInstanceTransformer)) {

        methodHandle method = instanceTransformerMethods.at(klass->redefinition_index());

        RC_TRACE(0x00008000, ("executing transformer method"));
        
        Thread *__the_thread__ = Thread::current();
        JavaValue result(T_VOID);
        JavaCallArguments args(cur);
        JavaCalls::call(&result,
          method,
          &args,
          THREAD);

        // TODO: What to do with an exception here?
        if (HAS_PENDING_EXCEPTION) {
          Symbol* ex_name = PENDING_EXCEPTION->klass()->klass_part()->name();
          RC_TRACE(0x00000002, ("exception when executing transformer: '%s'",
            ex_name->as_C_string()));
          CLEAR_PENDING_EXCEPTION;
        }
      }
    }

    delete _updated_oops;
    _updated_oops = NULL;
  }

  // Free the array of scratch classes
  delete _new_classes;
  _new_classes = NULL;
  RC_TRACE(0x00000001, ("Redefinition finished!"));

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
static void unpatch_bytecode(methodOop method) {
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
    //tty->print_cr("name=%s", k_oop->klass_part()->name()->as_C_string());
/*
    methodOop *matching_old_methods = NEW_RESOURCE_ARRAY(methodOop, _old_methods->length());
    methodOop *matching_new_methods = NEW_RESOURCE_ARRAY(methodOop, _old_methods->length());

    for (int i=0; i<_matching_methods_length; i++) {
      matching_old_methods[i] = (methodOop)_old_methods->obj_at(_matching_old_methods[i]);
      matching_new_methods[i] = (methodOop)_new_methods->obj_at(_matching_new_methods[i]);
    }*/

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
        cp_cache->adjust_entries(NULL,
          NULL,
          0);
      }

      // If bytecode rewriting is enabled, we also need to unpatch bytecode to force resolution of zeroied entries
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
    RC_TRACE(0x00008000, ("matching method %s", old_method->name_and_sig_as_C_string()));
    
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
        //RC_TRACE(0x00008000, ("Changed jmethodID for old method assigned to %d / result=%d", new_jmethod_id, result);
        //RC_TRACE(0x00008000, ("jmethodID new method: %d jmethodID old method: %d", new_method_h->jmethod_id(), old_method->jmethod_id());
      } else {
        jmethodID mid = new_method_h->jmethod_id();
        bool result = instanceKlass::cast(new_method_h->method_holder())->update_jmethod_id(new_method_h(), jmid);
        //RC_TRACE(0x00008000, ("Changed jmethodID for new method assigned to %d / result=%d", jmid, result);

      }
      JNIHandles::change_method_associated_with_jmethod_id(jmid, new_method_h);
      //RC_TRACE(0x00008000, ("changing method associated with jmethod id %d to %s", (int)jmid, new_method_h->name()->as_C_string());
      assert(JNIHandles::resolve_jmethod_id(jmid) == (methodOop)_new_methods->obj_at(_matching_new_methods[j]), "should be replaced");
      jmethodID mid = ((methodOop)_new_methods->obj_at(_matching_new_methods[j]))->jmethod_id();
      assert(JNIHandles::resolve_non_null((jobject)mid) == new_method_h(), "must match!");

      //RC_TRACE(0x00008000, ("jmethodID new method: %d jmethodID old method: %d", new_method_h->jmethod_id(), old_method->jmethod_id());
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
  RC_TRACE(0x00008000, ("Matching methods = %d / deleted methods = %d / added methods = %d",
    _matching_methods_length, _deleted_methods_length, _added_methods_length));
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
  _the_class_oop = the_old_class();
  compute_added_deleted_matching_methods();

  // track which methods are EMCP for add_previous_version() call below
  
  // (tw) TODO: Check if we need the concept of EMCP?
   BitMap emcp_methods(_old_methods->length());
  int emcp_method_count = 0;
  emcp_methods.clear();  // clears 0..(length() - 1)
  
  // We need to mark methods as old!!
  check_methods_and_mark_as_obsolete(&emcp_methods, &emcp_method_count);
  update_jmethod_ids();

  // keep track of previous versions of this class
  the_new_class->add_previous_version(the_old_class, &emcp_methods,
    emcp_method_count);

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
    RC_TRACE(0x00008000, ("Checking matching methods for EMCP"));
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

        RC_TRACE(0x00008000, ("Found EMCP method %s", old_method->name_and_sig_as_C_string()));

        // Transfer breakpoints
        instanceKlass *ik = instanceKlass::cast(old_method->method_holder());
        for (BreakpointInfo* bp = ik->breakpoints(); bp != NULL; bp = bp->next()) {
          RC_TRACE(0x00000002, ("Checking breakpoint"));
          RC_TRACE(0x00000002, ("%d / %d",
            bp->match(old_method), bp->match(new_method)));
          if (bp->match(old_method)) {
            assert(bp->match(new_method), "if old method is method, then new method must match too");
            RC_TRACE(0x00000002, ("Found a breakpoint in an old EMCP method"));
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
        RC_TRACE(0x00008000, ("mark %s(%s) as obsolete",
          old_method->name()->as_C_string(),
          old_method->signature()->as_C_string()));
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
      RC_TRACE(0x00008000, ("mark deleted %s(%s) as obsolete",
        old_method->name()->as_C_string(),
        old_method->signature()->as_C_string()));
    }
    //assert((*emcp_method_count_p + obsolete_count) == _old_methods->length(), "sanity check");
    RC_TRACE(0x00008000, ("EMCP_cnt=%d, obsolete_cnt=%d !", *emcp_method_count_p, obsolete_count));
}

// Increment the classRedefinedCount field in the specific instanceKlass
// and in all direct and indirect subclasses.
void VM_RedefineClasses::increment_class_counter(instanceKlass *ik, TRAPS) {
  oop class_mirror = ik->java_mirror();
  klassOop class_oop = java_lang_Class::as_klassOop(class_mirror);
  int new_count = java_lang_Class::classRedefinedCount(class_mirror) + 1;
  java_lang_Class::set_classRedefinedCount(class_mirror, new_count);
  RC_TRACE(0x00008000, ("updated count for class=%s to %d", ik->external_name(), new_count));
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
      if (!ik->vtable()->check_no_old_entries()) {
        RC_TRACE(0x00000001, ("size of class: %d\n",
          k_oop->size()));
        RC_TRACE(0x00000001, ("klassVtable::check_no_old_entries failure -- OLD method found -- class: %s",
          ik->signature_name()));
        assert(false, "OLD method found");
      }

      ik->vtable()->verify(tty, true);
    }
  }
}

#endif

VM_RedefineClasses::FindAffectedKlassesClosure::FindAffectedKlassesClosure( GrowableArray<instanceKlassHandle> *original_klasses, GrowableArray<instanceKlassHandle> *result )
{
  assert(original_klasses != NULL && result != NULL, "");
  this->_original_klasses = original_klasses;
  this->_result = result;
  SystemDictionary::classes_do(this);
}

void VM_RedefineClasses::FindAffectedKlassesClosure::do_object( oop obj )
{
  klassOop klass = (klassOop)obj;
  assert(!_result->contains(klass), "must not occur more than once!");
  assert(klass->klass_part()->new_version() == NULL, "Only last version is valid entry in system dictionary");

  for(int i=0; i<_original_klasses->length(); i++) {
    instanceKlassHandle cur = _original_klasses->at(i);
    if (cur() != klass && klass->klass_part()->is_subtype_of(cur()) && !_original_klasses->contains(klass)) {  
      RC_TRACE(0x00008000, ("Found affected class: %s", klass->klass_part()->name()->as_C_string()));
      _result->append(klass);
      break;
    }
  }
}

jvmtiError VM_RedefineClasses::do_topological_class_sorting( const jvmtiClassDefinition *class_defs, int class_count, GrowableArray<instanceKlassHandle> *affected, GrowableArray<instanceKlassHandle> *arr, TRAPS)
{
  GrowableArray< Pair<klassOop, klassOop> > *links = new GrowableArray< Pair<klassOop, klassOop> >();

  for (int i=0; i<class_count; i++) {

    oop mirror = JNIHandles::resolve_non_null(class_defs[i].klass);
    klassOop the_class_oop = java_lang_Class::as_klassOop(mirror);
    instanceKlassHandle the_class(THREAD, the_class_oop);
    Handle the_class_loader(THREAD, the_class->class_loader());
    Handle protection_domain(THREAD, the_class->protection_domain());

    ClassFileStream st((u1*) class_defs[i].class_bytes,
      class_defs[i].class_byte_count, (char *)"__VM_RedefineClasses__");
    ClassFileParser cfp(&st);

    GrowableArray<Symbol*> symbolArr;
    RC_TRACE(0x00000002, ("Before find super symbols of class %s",
      the_class->name()->as_C_string()));
    cfp.findSuperSymbols(the_class->name(), the_class_loader, protection_domain, the_class, symbolArr, THREAD);
    
    for (int j=0; j<symbolArr.length(); j++) {
      Symbol* sym = symbolArr.at(j);

      RC_TRACE(0x00008000, ("Before adding link to super class %s", sym->as_C_string()));

      for (int k=0; k<arr->length(); k++) {
        klassOop curOop = arr->at(k)();
        // (tw) TODO: Check if we get aliasing problems with different class loaders?
        if (curOop->klass_part()->name() == sym /*&& curOop->klass_part()->class_loader() == the_class_loader()*/) {
          RC_TRACE(0x00000002, ("Found class to link"));
          links->append(Pair<klassOop, klassOop>(curOop, the_class()));
          break;
        }
      }
    }
  }


  RC_TRACE(0x00000001, ("Identified links between classes! "));

  for (int i=0; i<affected->length(); i++) {

    instanceKlassHandle klass = affected->at(i);

    klassOop superKlass = klass->super();
    if (affected->contains(superKlass)) {
      links->append(Pair<klassOop, klassOop>(superKlass, klass()));
    }

    objArrayOop superInterfaces = klass->local_interfaces();
    for (int j=0; j<superInterfaces->length(); j++) {
      klassOop interfaceKlass = (klassOop)superInterfaces->obj_at(j);
      if (arr->contains(interfaceKlass)) {
        links->append(Pair<klassOop, klassOop>(interfaceKlass, klass()));
      }
    }
  }

  if (RC_TRACE_ENABLED(0x00000002))  {
    RC_TRACE(0x00000002, ("Identified links: "));
    for (int i=0; i<links->length(); i++) {
      RC_TRACE(0x00000002, ("%s to %s",
        links->at(i).left()->klass_part()->name()->as_C_string(),
        links->at(i).right()->klass_part()->name()->as_C_string()));
    }
  }

  for (int i=0; i<arr->length(); i++) {

    int j;
    for (j=i; j<arr->length(); j++) {

      int k;
      for (k=0; k<links->length(); k++) {

        klassOop k1 = links->adr_at(k)->right();
        klassOop k2 = arr->at(j)();
        if (k1 == k2) {
          break;
        }
      }

      if (k == links->length()) {
        break;
      }
    }

    if (j == arr->length()) {
      // circle detected
      return JVMTI_ERROR_CIRCULAR_CLASS_DEFINITION;
    }

    for (int k=0; k<links->length(); k++) {
      if (links->adr_at(k)->left() == arr->at(j)()) {
        links->at_put(k, links->at(links->length() - 1));
        links->remove_at(links->length() - 1);
        k--;
      }
    }

    instanceKlassHandle tmp = arr->at(j);
    arr->at_put(j, arr->at(i));
    arr->at_put(i, tmp);
  }

  return JVMTI_ERROR_NONE;
}

void VM_RedefineClasses::oops_do(OopClosure *closure) {

  if (_updated_oops != NULL) {
    for (int i=0; i<_updated_oops->length(); i++) {
      closure->do_oop(_updated_oops->adr_at(i));
    }
  }
}

void VM_RedefineClasses::transfer_special_access_flags(fieldDescriptor *from, fieldDescriptor *to) {
  to->set_is_field_modification_watched(from->is_field_modification_watched());
  to->set_is_field_access_watched(from->is_field_access_watched());
  if (from->is_field_modification_watched() || from->is_field_access_watched()) {
    RC_TRACE(0x00000002, ("Transfered watch for field %s",
      from->name()->as_C_string()));
  }
  update_klass_field_access_flag(to);
}

void VM_RedefineClasses::update_klass_field_access_flag(fieldDescriptor *fd) {
  instanceKlass* ik = instanceKlass::cast(fd->field_holder());
  FieldInfo* fi = FieldInfo::from_field_array(ik->fields(), fd->index());
  fi->set_access_flags(fd->access_flags().as_short());
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

          RC_TRACE(0x00008000, ("Transfering native function for method %s", old_method->name()->as_C_string()));
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
