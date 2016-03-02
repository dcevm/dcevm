/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Redefines a class and tests that the lock on the java.lang.Class object
 * is retained. Also tests that the object identity of the java.lang.Class
 * object is not changed.
 *
 * @author Thomas Wuerthinger
 */
public class ClassObjectSynchronizationTest {

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  // Version 0
  public static class A {

    public int value() {
      return 1;
    }
  }

  // Version 1
  public static class A___1 {

    public int value() {
      return 2;
    }
  }

  @Test
  public void testClassObjectSynchronization() {
    A a = new A();
    Class clazz = a.getClass();
    synchronized (clazz) {
      assertEquals(1, a.value());
      __toVersion__(1);
      assertEquals(2, a.value());
      assertTrue(a.getClass() == clazz);
      assertTrue(a.getClass() == ClassObjectSynchronizationTest.A.class);
    }
    assertEquals(2, a.value());
    assertTrue(a.getClass() == clazz);
    __toVersion__(0);
    assertTrue(a.getClass() == clazz);
  }
}
