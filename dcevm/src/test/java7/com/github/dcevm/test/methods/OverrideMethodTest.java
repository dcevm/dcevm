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

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * Tests for the class relationship A<B<C with adding / removing methods.
 *
 * @author Thomas Wuerthinger
 */
public class OverrideMethodTest {

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  // Version 0
  public static class A {

    public int value() {
      return 5;
    }
  }

  public static class B extends A {

    public int doubled() {
      return value() * 2;
    }
  }

  public static class C extends B {
  }

  // Version 1
  public static class A___1 {

    public int value() {
      return 10;
    }
  }

  // Version 2
  public static class B___2 extends A {

    public int doubled() {
      return value() * 3;
    }
  }

  // Version 3
  public static class C___3 extends B {

    @Override
    public int value() {
      return 1;
    }
  }

  // Verison 4
  public static class A___4 {

    public int value() {
      return baseValue();
    }

    public int baseValue() {
      return 20;
    }
  }

  public static class B___4 extends A {

    public int doubled() {
      return value() * 2;
    }
  }

  public static class C___4 extends B {
  }

  // Verison 5
  public static class A___5 {

    public int value() {
      return baseValue();
    }

    public int baseValue() {
      return 20;
    }
  }

  @Test
  public void testSimple() {

    assert __version__() == 0;

    A a = new A();
    B b = new B();
    C c = new C();

    assertEquals(5, a.value());
    assertEquals(5, b.value());
    assertEquals(10, b.doubled());
    assertEquals(5, c.value());
    assertEquals(10, c.doubled());

    __toVersion__(1);
    assertEquals(10, a.value());
    assertEquals(10, b.value());
    assertEquals(20, b.doubled());
    assertEquals(10, c.value());
    assertEquals(20, c.doubled());

    __toVersion__(2);
    assertEquals(10, a.value());
    assertEquals(10, b.value());
    assertEquals(30, b.doubled());
    assertEquals(10, c.value());
    assertEquals(30, c.doubled());

    __toVersion__(0);
    assertEquals(5, a.value());
    assertEquals(5, b.value());
    assertEquals(10, b.doubled());
    assertEquals(5, c.value());
    assertEquals(10, c.doubled());
  }

  @Test
  public void testMethodAdd() {

    assert __version__() == 0;
    A a = new A();
    B b = new B();
    C c = new C();

    assertEquals(5, a.value());
    assertEquals(5, b.value());
    assertEquals(10, b.doubled());
    assertEquals(5, c.value());
    assertEquals(10, c.doubled());

    __toVersion__(4);
    assertEquals(20, a.value());
    assertEquals(40, b.doubled());
    assertEquals(20, b.value());
    assertEquals(20, c.value());
    assertEquals(40, c.doubled());

    __toVersion__(0);
    assertEquals(5, a.value());
    assertEquals(5, b.value());
    assertEquals(10, b.doubled());
    assertEquals(5, c.value());
    assertEquals(10, c.doubled());
  }

  @Test
  public void testOverride() {

    assert __version__() == 0;

    A a = new A();
    B b = new B();
    C c = new C();

    assertEquals(5, a.value());
    assertEquals(5, b.value());
    assertEquals(10, b.doubled());
    assertEquals(5, c.value());
    assertEquals(10, c.doubled());

    __toVersion__(3);
    assertEquals(5, a.value());
    assertEquals(5, b.value());
    assertEquals(10, b.doubled());
    assertEquals(1, c.value());
    assertEquals(2, c.doubled());

    __toVersion__(0);
    assertEquals(5, a.value());
    assertEquals(5, b.value());
    assertEquals(10, b.doubled());
    assertEquals(5, c.value());
    assertEquals(10, c.doubled());
  }

  @Test
  public void testMethodAddAdvanced() {

    assert __version__() == 0;
    A a = new A();
    B b = new B();
    C c = new C();

    assertEquals(5, a.value());
    assertEquals(5, b.value());
    assertEquals(10, b.doubled());
    assertEquals(5, c.value());
    assertEquals(10, c.doubled());

    __toVersion__(5);
    assertEquals(20, a.value());
    assertEquals(20, b.value());
    assertEquals(40, b.doubled());
    assertEquals(20, c.value());
    assertEquals(40, c.doubled());

    __toVersion__(0);
    assertEquals(5, a.value());
    assertEquals(5, b.value());
    assertEquals(10, b.doubled());
    assertEquals(5, c.value());
    assertEquals(10, c.doubled());
  }
}
