/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.github.dcevm.test.fields;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static org.junit.Assert.assertEquals;

/**
 * Test for replacing field with MethodHandle pointing to it.
 * <p>
 * Technically, should work for Java 7, but currently is not supported in Java 7.
 *
 * @author Ivan Dubrov
 */
public class InstanceFieldHandleTest {

  // Version 0
  public static class A {
    public int fieldA;
    public int fieldB;

    public int getFieldA() {
      return -1;
    }
  }

  // Version 1 (fields swapped and new one is added)
  public static class A___1 {
    public int fieldB;
    public int fieldA;
    public String fieldC;

    public int getFieldA() {
      return fieldA;
    }
  }

  // Version 2 (fields removed)
  public static class A___2 {
  }

  // Version 3 (field type changed)
  public static class A___3 {
    public String fieldA;
    public int fieldB;
  }

  @Before
  @After
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testFieldChangeOrder() throws Throwable {
    A a = new A();
    MethodHandle getter = MethodHandles.publicLookup().findGetter(A.class, "fieldA", int.class);
    MethodHandle setter = MethodHandles.publicLookup().findSetter(A.class, "fieldA", int.class);

    a.fieldA = 3;
    assertEquals(3, getter.invoke(a));

    // Swap fields
    __toVersion__(1);

    assertEquals(3, getter.invoke(a));
    setter.invoke(a, 53);
    assertEquals(53, a.getFieldA());
    assertEquals(53, getter.invoke(a));
  }

  @Test
  public void testFieldRemoved() throws Throwable {
    A a = new A();
    MethodHandle getter = MethodHandles.publicLookup().findGetter(A.class, "fieldA", int.class);
    MethodHandle setter = MethodHandles.publicLookup().findSetter(A.class, "fieldA", int.class);

    a.fieldA = 3;
    assertEquals(3, getter.invoke(a));

    // Remove fieldA
    __toVersion__(2);

    try {
      getter.invoke(a);
      Assert.fail("Handle should have been cleared!");
    } catch (NullPointerException e) {
      // Handle was cleared!
    }

    try {
      setter.invoke(a, 10);
      Assert.fail("Handle should have been cleared!");
    } catch (NullPointerException e) {
      // Handle was cleared!
    }
  }

  @Test
  public void testFieldTypeChange() throws Throwable {
    A a = new A();
    MethodHandle getter = MethodHandles.publicLookup().findGetter(A.class, "fieldA", int.class);
    MethodHandle setter = MethodHandles.publicLookup().findSetter(A.class, "fieldA", int.class);

    a.fieldA = 3;
    assertEquals(3, getter.invoke(a));

    // Remove fieldA
    __toVersion__(3);

    try {
      getter.invoke(a);
      Assert.fail("Handle should have been cleared!");
    } catch (NullPointerException e) {
      // Handle was cleared!
    }

    try {
      setter.invoke(a, 10);
      Assert.fail("Handle should have been cleared!");
    } catch (NullPointerException e) {
      // Handle was cleared!
    }
  }
}