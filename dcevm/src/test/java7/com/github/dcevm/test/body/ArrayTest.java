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

package com.github.dcevm.test.body;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Array;
import java.util.Arrays;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ivan Dubrov
 */
public class ArrayTest {

  public class A {
  }

  public class A___1 {
  }

  public class B extends A {
  }

  public class B___1 extends A {
  }

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testArray() {
    Object[] arr = new A[]{new A(), new A()};
    A[] arr2 = new B[]{new B(), new B()};
    A[][] arr3 = new B[10][];

    __toVersion__(1);

    assertTrue(arr instanceof A[]);
    assertTrue(arr instanceof Object[]);
    assertEquals(arr.getClass(), Array.newInstance(A.class, 0).getClass());

    assertTrue(arr2 instanceof B[]);
    assertTrue(arr2 instanceof A[]);
    assertTrue(arr2 instanceof Object[]);
    assertEquals(arr2.getClass(), Array.newInstance(B.class, 0).getClass());

    assertTrue(arr3 instanceof B[][]);
    assertTrue(arr3 instanceof A[][]);
    assertTrue(arr3 instanceof Object[][]);
    assertEquals(arr3.getClass(), Array.newInstance(B[].class, 0).getClass());

    __toVersion__(0);

    assertTrue(arr instanceof A[]);
    assertTrue(arr instanceof Object[]);
    assertEquals(arr.getClass(), Array.newInstance(A.class, 0).getClass());

    assertTrue(arr2 instanceof B[]);
    assertTrue(arr2 instanceof A[]);
    assertTrue(arr2 instanceof Object[]);
    assertEquals(arr2.getClass(), Array.newInstance(B.class, 0).getClass());

    assertTrue(arr3 instanceof B[][]);
    assertTrue(arr3 instanceof A[][]);
    assertTrue(arr3 instanceof Object[][]);
    assertEquals(arr3.getClass(), Array.newInstance(B[].class, 0).getClass());
  }

  @Test
  public void testArrayGetComponentType() {

    A[] array = new A[10];

    assertEquals(A.class, array.getClass().getComponentType());
    __toVersion__(1);

    assertEquals(A.class, array.getClass().getComponentType());
    array = Arrays.copyOf(array, array.length);
    assertEquals(A.class, array.getClass().getComponentType());
  }
}
