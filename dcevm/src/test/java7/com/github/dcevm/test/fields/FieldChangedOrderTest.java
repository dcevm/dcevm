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
 * Test that changes the order of two int fields.
 *
 * @author Thomas Wuerthinger
 */
public class FieldChangedOrderTest {

  // Version 0
  public static class A {

    public int value1;
    public int value2;

    public A() {
      value1 = 1;
      value2 = 2;
    }

    public int getValue1() {
      return value1;
    }

    public int getValue2() {
      return value2;
    }
  }

  public static class B {

    public static int getStaticValue1(A a) {
      return a.value1;
    }

    public static int getStaticValue2(A a) {
      return a.value2;
    }
  }

  // Version 1
  public static class A___1 {

    public int value2;
    public int value1;

    public int getValue1() {
      return value1;
    }

    public int getValue2() {
      return value2;
    }
  }

  public static class B___1 {

    public static int getStaticValue1(A a) {
      return a.value1;
    }

    public static int getStaticValue2(A a) {
      return a.value2;
    }
  }

  // Version 2
  public static class A___2 {

    public int tmp1;
    public int value2;
    public int tmp2;
    public int value1;
    public int tmp3;

    public int getValue1() {
      return value1;
    }

    public int getValue2() {
      return value2;
    }
  }

  // Version 3
  public static class A___3 {

    public int tmp1;
    public int value2;

    public int getValue1() {
      return tmp1;
    }

    public int getValue2() {
      return value2;
    }
  }

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testRenameField() {
    assert __version__() == 0;
    A a = new A();
    assertObjectOK(a);
    __toVersion__(3);
    assertEquals(0, a.getValue1());
    assertEquals(2, a.getValue2());
    __toVersion__(0);
    assertEquals(0, a.getValue1());
    assertEquals(2, a.getValue2());
  }

  @Test
  public void testSimpleOrderChange() {
    assert __version__() == 0;
    A a = new A();
    assertObjectOK(a);
    __toVersion__(1);
    assertObjectOK(a);
    __toVersion__(0);
    assertObjectOK(a);
  }

  /**
   * Checks that the given object is unmodified (i.e. the values of the fields are correct)
   *
   * @param a the object to be checked
   */
  private void assertObjectOK(A a) {
    assertEquals(1, a.getValue1());
    assertEquals(2, a.getValue2());
    assertEquals(1, B.getStaticValue1(a));
    assertEquals(2, B.getStaticValue2(a));
    assertEquals(1, a.value1);
    assertEquals(2, a.value2);
  }

  @Test
  public void testSimpleOrderChangeWithNewTempFields() {
    assert __version__() == 0;
    A a = new A();
    assertObjectOK(a);
    __toVersion__(2);
    assertObjectOK(a);
    __toVersion__(0);
    assertObjectOK(a);
  }
}
