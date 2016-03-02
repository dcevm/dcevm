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

import org.junit.Before;
import org.junit.Test;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * @author Thomas Wuerthinger
 */
public class ObjectStressTest {

  private final int COUNT = 10000;

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  // Version 0
  public static class A {

    public A thisPointer;
    public int i1;
    public int i2;
    public int i3;
    public int i4;
    public int i5;
    public int i6;
    public int i7;
    public int i8;
    public int i9;
    public int i10;

    public int sum() {
      return i1 + i2 + i3 + i4 + i5 + i6 + i7 + i8 + i9 + i10;
    }
  }

  // Version 1
  public static class A___1 {

    public int i1;
    public int i2;
    public int i8;
    public int i3;
    public int i4;
    public int i10;
    public int i5;
    public int i6;
    public int i7;
    public int i9;
    public A thisPointer;

    public int sum() {
      return i1 * i2 * i3 * i4 * i5 * i6 * i7 * i8 * i9 * i10;
    }
  }

  @Test
  public void testLotsOfObjects() {

    assert __version__() == 0;

    A[] arr = new A[COUNT];
    for (int i = 0; i < arr.length; i++) {
      arr[i] = new A();
      arr[i].thisPointer = arr[i];
      arr[i].i1 = 1;
      arr[i].i2 = 2;
      arr[i].i3 = 3;
      arr[i].i4 = 4;
      arr[i].i5 = 5;
      arr[i].i6 = 6;
      arr[i].i7 = 7;
      arr[i].i8 = 8;
      arr[i].i9 = 9;
      arr[i].i10 = 10;
    }


    __toVersion__(1);

    for (int i = 0; i < arr.length; i++) {
      assertEquals(1 * 2 * 3 * 4 * 5 * 6 * 7 * 8 * 9 * 10, arr[i].sum());
      assertEquals(arr[i].thisPointer, arr[i]);
    }

    __toVersion__(0);

    for (int i = 0; i < arr.length; i++) {
      assertEquals(1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10, arr[i].sum());
      assertEquals(arr[i].thisPointer, arr[i]);
    }
  }
}
