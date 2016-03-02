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
package com.github.dcevm.test.methods;

import org.junit.Before;
import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * Test for replacing method with MethodHandle pointing to it.
 * <p>
 * FIXME: add tests for case when we change type of the method (like static -> non-static). If that happens,
 * MemberName should be somehow marked as invalid...
 *
 * @author Ivan Dubrov
 */
public class MethodHandleTest {

  // Version 0
  public static class A {
    public int field;

    public A(int value) {
      field = value;
    }

    public int method() {
      return 1;
    }

    public int filter(int value) {
      return value + 10;
    }

    public static int staticMethod() {
      return 3;
    }

    public static int staticFilter(int value) {
      return value + 1000;
    }
  }

  // Version 1
  public static class A___1 {
    public int field;

    public A___1(int value) {
      field = value * 10;
    }

    public int method() {
      return 2;
    }

    public int filter(int value) {
      return value + 100;
    }

    public static int staticMethod() {
      return 4;
    }

    public static int staticFilter(int value) {
      return value + 10000;
    }
  }

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testMethodHandleUpdated() throws Throwable {

    assert __version__() == 0;

    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle handle = lookup.findVirtual(A.class, "method", MethodType.methodType(int.class));

    A a = new A(3);
    assertEquals(1, (int) handle.invokeExact(a));

    __toVersion__(1);

    assertEquals(2, (int) handle.invokeExact(a));

    __toVersion__(0);
    assert __version__() == 0;
  }

  @Test
  public void testBoundMethodHandleUpdated() throws Throwable {

    assert __version__() == 0;

    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle handle = lookup.findVirtual(A.class, "method", MethodType.methodType(int.class));

    A a = new A(3);
    MethodHandle boundHandle = handle.bindTo(a);
    assertEquals(1, (int) boundHandle.invokeExact());

    __toVersion__(1);

    assertEquals(2, (int) boundHandle.invokeExact());

    __toVersion__(0);
    assert __version__() == 0;
  }

  @Test
  public void testStaticMethodHandleUpdated() throws Throwable {

    assert __version__() == 0;

    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle handle = lookup.findStatic(A.class, "staticMethod", MethodType.methodType(int.class));

    assertEquals(3, handle.invoke());

    __toVersion__(1);

    assertEquals(4, handle.invoke());

    __toVersion__(0);
    assert __version__() == 0;
  }

  @Test
  public void testConstructorMethodHandleUpdated() throws Throwable {

    assert __version__() == 0;

    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle handle = lookup.findConstructor(A.class, MethodType.methodType(void.class, int.class));

    assertEquals(12, ((A) handle.invoke(12)).field);

    __toVersion__(1);

    assertEquals(120, ((A) handle.invoke(12)).field);

    __toVersion__(0);
    assert __version__() == 0;
  }

  @Test
  public void testComplexMethodHandleUpdated() throws Throwable {

    assert __version__() == 0;

    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle handle = lookup.findVirtual(A.class, "method", MethodType.methodType(int.class));
    MethodHandle filter = lookup.findVirtual(A.class, "filter", MethodType.methodType(int.class, int.class));
    MethodHandle staticFilter = lookup.findStatic(A.class, "staticFilter", MethodType.methodType(int.class, int.class));

    A a = new A(3);
    MethodHandle boundFilter = filter.bindTo(a);
    handle = MethodHandles.filterReturnValue(handle, staticFilter);
    handle = MethodHandles.filterReturnValue(handle, boundFilter);

    assertEquals(1011, handle.invoke(a));

    __toVersion__(1);

    assertEquals(10102, handle.invoke(a));

    __toVersion__(0);
    assert __version__() == 0;
  }

}