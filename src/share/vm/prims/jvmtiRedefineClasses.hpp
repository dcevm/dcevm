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

#ifndef SHARE_VM_PRIMS_JVMTIREDEFINECLASSES_HPP
#define SHARE_VM_PRIMS_JVMTIREDEFINECLASSES_HPP

#include "jvmtifiles/jvmtiEnv.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/fieldStreams.hpp"
#include "prims/jvmtiRedefineClassesTrace.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"

// New version that allows arbitrary changes to already loaded classes.
class VM_RedefineClasses: public VM_GC_Operation {
 private:

  // These static fields are needed by SystemDictionary::classes_do()
  // facility and the adjust_cpool_cache_and_vtable() helper:
  static objArrayOop     _old_methods;
  static objArrayOop     _new_methods;
  static int*            _matching_old_methods;
  static int*            _matching_new_methods;
  static int*            _deleted_methods;
  static int*            _added_methods;
  static int             _matching_methods_length;
  static int             _deleted_methods_length;
  static int             _added_methods_length;

  static int             _revision_number;

  static GrowableArray<instanceKlassHandle>* _affected_klasses;

  // The instance fields are used to pass information from
  // doit_prologue() to doit() and doit_epilogue().
  jint                        _class_count;
  const jvmtiClassDefinition *_class_defs;  // ptr to _class_count defs

  // This operation is used by both RedefineClasses and
  // RetransformClasses.  Indicate which.
  JvmtiClassLoadKind          _class_load_kind;

  GrowableArray<instanceKlassHandle>* _new_classes;
  jvmtiError                  _result;
  int                         _max_redefinition_flags;

  // Performance measurement support. These timers do not cover all
  // the work done for JVM/TI RedefineClasses() but they do cover
  // the heavy lifting.
  elapsedTimer _timer_total;
  elapsedTimer _timer_prologue;
  elapsedTimer _timer_class_linking;
  elapsedTimer _timer_class_loading;
  elapsedTimer _timer_prepare_redefinition;
  elapsedTimer _timer_wait_for_locks;
  elapsedTimer _timer_heap_iteration;
  elapsedTimer _timer_redefinition;
  elapsedTimer _timer_vm_op_epilogue;

  jvmtiError check_redefinition_allowed(instanceKlassHandle new_class);
  jvmtiError find_sorted_affected_classes( );
  jvmtiError find_class_bytes(instanceKlassHandle the_class, const unsigned char **class_bytes, jint *class_byte_count, jboolean *not_changed);
  jvmtiError load_new_class_versions(TRAPS);

  // Figure out which new methods match old methods in name and signature,
  // which methods have been added, and which are no longer present
  void compute_added_deleted_matching_methods();

  // Change jmethodIDs to point to the new methods
  void update_jmethod_ids();

  void swap_all_method_annotations(int i, int j, instanceKlassHandle scratch_class);

  static void add_affected_klasses( klassOop obj );

  static jvmtiError do_topological_class_sorting(const jvmtiClassDefinition *class_definitions, int class_count, TRAPS);

  // Install the redefinition of a class
  void redefine_single_class(instanceKlassHandle the_new_class, TRAPS);

  // Increment the classRedefinedCount field in the specific instanceKlass
  // and in all direct and indirect subclasses.
  void increment_class_counter(instanceKlass *ik, TRAPS);


  void flush_dependent_code(instanceKlassHandle k_h, TRAPS);

  static void check_class(klassOop k_oop,/* oop initiating_loader,*/ TRAPS) PRODUCT_RETURN;

  static void adjust_cpool_cache(klassOop k_oop, oop initiating_loader, TRAPS);

  static void unpatch_bytecode(methodOop method);

#ifdef ASSERT
  static void verify_classes(klassOop k_oop, oop initiating_loader, TRAPS);
#endif

  int calculate_redefinition_flags(instanceKlassHandle new_version);
  void calculate_instance_update_information(klassOop new_version);
  void check_methods_and_mark_as_obsolete(BitMap *emcp_methods, int * emcp_method_count_p);
  static void mark_as_scavengable(nmethod* nm);
  
  bool check_arguments();
  jvmtiError check_arguments_error();

 public:
  VM_RedefineClasses(jint class_count, const jvmtiClassDefinition *class_defs, JvmtiClassLoadKind class_load_kind);
  virtual ~VM_RedefineClasses();
  
  bool doit_prologue();
  void doit();
  void doit_epilogue();
  void rollback();

  jvmtiError check_exception() const;
  VMOp_Type type() const                         { return VMOp_RedefineClasses; }
  bool skip_operation() const                    { return false; }
  bool allow_nested_vm_operations() const        { return true;  }
  jvmtiError check_error()                       { return _result;  }

  // Modifiable test must be shared between IsModifiableClass query
  // and redefine implementation
  static bool is_modifiable_class(oop klass_mirror);

  // Utility methods for transfering field access flags

  static void transfer_special_access_flags(JavaFieldStream *from, JavaFieldStream *to);
  static void transfer_special_access_flags(fieldDescriptor *from, fieldDescriptor *to);

  void transfer_old_native_function_registrations(instanceKlassHandle the_class);

  void lock_threads();
  void unlock_threads();

  static void swap_marks(oop first, oop second);

};

#endif // SHARE_VM_PRIMS_JVMTIREDEFINECLASSES_HPP
