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
 * Test for moving method around with MethodHandle pointing to it. vmindex in the MethodHandle should be updated by DCEVM.
 *
 * @author Ivan Dubrov
 */
public class MethodHandleSwapMethodsTest {

  // Version 0
  public static class A {
    public int method() {
      return 1;
    }

    public int method2() {
      return 10;
    }
  }

  // Version 1
  public static class A___1 {
    public int method0() {
      return 20;
    }

    public int method2() {
      return 30;
    }

    public int method() {
      return 2;
    }

    public int method3() {
      return 40;
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

    A a = new A();
    assertEquals(1, (int) handle.invokeExact(a));

    __toVersion__(1);

    assertEquals(2, (int) handle.invokeExact(a));

    __toVersion__(0);
    assert __version__() == 0;
  }
}