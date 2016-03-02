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
public class FieldModificationTest {

  // Version 0
  public static class A {

    public int val0;
    public int val1;
    public int val2;
    public int val3;
    public int val4;
    public int val5;
    public int val6;
    public int val7;

    public void increaseAllByOne() {
      val0++;
      val1++;
      val2++;
      val3++;
      val4++;
      val5++;
      val6++;
      val7++;
    }

    public int sum() {
      return val0 + val1 + val2 + val3 + val4 + val5 + val6 + val7;
    }
  }

  // Version 1
  public static class A___1 {

    public int val0;

    public void increaseAllByOne() {
      val0++;
    }

    public int sum() {
      return val0;
    }
  }

  // Version 2
  public static class A___2 {

    public int val0;
    public int val1;
    public int val2;
    public int val3;
    public int val4;
    public int val5;
    public int val6;
    public int val7;
    public int val8;
    public int val9;
    public int val10;
    public int val11;
    public int val12;
    public int val13;
    public int val14;
    public int val15;

    public int sum() {
      return val0 + val1 + val2 + val3 + val4 + val5 + val6 + val7 + val8 + val9 + val10 + val11 + val12 + val13 + val14 + val15;
    }

    public void increaseAllByOne() {
      val0++;
      val1++;
      val2++;
      val3++;
      val4++;
      val5++;
      val6++;
      val7++;
      val8++;
      val9++;
      val10++;
      val11++;
      val12++;
      val13++;
      val14++;
      val15++;
    }
  }

  // Version 3
  public static class A___3 {

    public int val6;
    public int val0;
    public int val7;
    public int val1;
    public int val2;
    public int val5;
    public int val3;
    public int val4;
  }

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testReorder() {

    A a = new A();

    a.val0 = 0;
    a.val1 = 1;
    a.val2 = 2;
    a.val3 = 3;
    a.val4 = 4;
    a.val5 = 5;
    a.val6 = 6;
    a.val7 = 7;
  }

  @Test
  public void testIncreaseFirst() {

    A a = new A();

    a.val0 = 0;
    a.val1 = 1;
    a.val2 = 2;
    a.val3 = 3;
    a.val4 = 4;
    a.val5 = 5;
    a.val6 = 6;
    a.val7 = 7;

    assertEquals(0, a.val0);
    assertEquals(1, a.val1);
    assertEquals(2, a.val2);
    assertEquals(3, a.val3);
    assertEquals(4, a.val4);
    assertEquals(5, a.val5);
    assertEquals(6, a.val6);
    assertEquals(7, a.val7);
    assertEquals(0 + 1 + 2 + 3 + 4 + 5 + 6 + 7, a.sum());

    __toVersion__(2);

    assertEquals(0, a.val0);
    assertEquals(1, a.val1);
    assertEquals(2, a.val2);
    assertEquals(3, a.val3);
    assertEquals(4, a.val4);
    assertEquals(5, a.val5);
    assertEquals(6, a.val6);
    assertEquals(7, a.val7);
    assertEquals(0 + 1 + 2 + 3 + 4 + 5 + 6 + 7, a.sum());

    a.increaseAllByOne();
    assertEquals(0 + 1 + 2 + 3 + 4 + 5 + 6 + 7 + 16, a.sum());

    __toVersion__(0);

    assertEquals(0 + 1 + 2 + 3 + 4 + 5 + 6 + 7 + 8, a.sum());
    assertEquals(1, a.val0);
    assertEquals(2, a.val1);
    assertEquals(3, a.val2);
    assertEquals(4, a.val3);
    assertEquals(5, a.val4);
    assertEquals(6, a.val5);
    assertEquals(7, a.val6);
    assertEquals(8, a.val7);

    __toVersion__(2);

    assertEquals(0 + 1 + 2 + 3 + 4 + 5 + 6 + 7 + 8, a.sum());
    assertEquals(1, a.val0);
    assertEquals(2, a.val1);
    assertEquals(3, a.val2);
    assertEquals(4, a.val3);
    assertEquals(5, a.val4);
    assertEquals(6, a.val5);
    assertEquals(7, a.val6);
    assertEquals(8, a.val7);

    a.increaseAllByOne();

    assertEquals(0 + 1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 16, a.sum());
    assertEquals(2, a.val0);
    assertEquals(3, a.val1);
    assertEquals(4, a.val2);
    assertEquals(5, a.val3);
    assertEquals(6, a.val4);
    assertEquals(7, a.val5);
    assertEquals(8, a.val6);
    assertEquals(9, a.val7);
    __toVersion__(0);

    assertEquals(0 + 1 + 2 + 3 + 4 + 5 + 6 + 7 + 16, a.sum());
    assertEquals(2, a.val0);
    assertEquals(3, a.val1);
    assertEquals(4, a.val2);
    assertEquals(5, a.val3);
    assertEquals(6, a.val4);
    assertEquals(7, a.val5);
    assertEquals(8, a.val6);
    assertEquals(9, a.val7);
  }

  @Test
  public void testAddRemoveField() {

    assert __version__() == 0;

    A a = new A();

    assertEquals(0, a.val0);
    assertEquals(0, a.val1);

    __toVersion__(1);

    a.val0 = 1234;

    __toVersion__(0);

    assertEquals(1234, a.val0);
    assertEquals(0, a.val1);

    a.val1 = 1234;

    assertEquals(1234, a.val0);
    assertEquals(1234, a.val1);

    __toVersion__(1);

    assertEquals(1234, a.val0);

    __toVersion__(0);

    assertEquals(1234, a.val0);
    assertEquals(0, a.val1);
  }
}
