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

package com.github.dcevm.test.structural;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.*;

/**
 * Smallest test case for a hierarchy swap. A<B is changed to B<A.
 *
 * @author Thomas Wuerthinger
 */
@Ignore
public class HierarchySwapTest {

  // Version 0
  public static class A {

    public int value() {
      return 1;
    }
  }

  public static class B extends A {

    @Override
    public int value() {
      return super.value() * 2;
    }
  }

  public static class C {

    public boolean doInstanceCheckA(Object o) {
      return o instanceof A;
    }

    public boolean doInstanceCheckB(Object o) {
      return o instanceof B;
    }
  }

  public static class Base {

    public String className() {
      return "Base";
    }
  }

  public static class D extends Base {

    @Override
    public String className() {
      return "D";
    }

    public String superClassName() {
      return super.className();
    }
  }

  public static class E extends Base {

    @Override
    public String className() {
      return "E";
    }

    public String superClassName() {
      return super.className();
    }
  }

  public static class F extends Base {

    @Override
    public String className() {
      return "F";
    }

    public String superClassName() {
      return super.className();
    }
  }

  // Version 1
  public static class A___1 extends B___1 {

    @Override
    public int value() {
      return super.value() * 2;
    }
  }

  public static class B___1 {

    public int value() {
      return 4;
    }
  }

  public static class C___1 {

    public boolean doInstanceCheckA(Object o) {
      return o instanceof A;
    }

    public boolean doInstanceCheckB(Object o) {
      return o instanceof B;
    }
  }

  // Version 2
  public static class D___2 extends Base {

    @Override
    public String className() {
      return "D";
    }

    public String superClassName() {
      return super.className();
    }
  }

  public static class E___2 extends D {

    @Override
    public String className() {
      return "E";
    }

    @Override
    public String superClassName() {
      return super.className();
    }
  }

  public static class F___2 extends E {

    @Override
    public String className() {
      return "F";
    }

    @Override
    public String superClassName() {
      return super.className();
    }
  }

  // Version 3
  public static class D___3 extends E {

    @Override
    public String className() {
      return "D";
    }

    @Override
    public String superClassName() {
      return super.className();
    }
  }

  public static class E___3 extends F {

    @Override
    public String className() {
      return "E";
    }

    @Override
    public String superClassName() {
      return super.className();
    }
  }

  public static class F___3 extends Base {

    @Override
    public String className() {
      return "F";
    }

    public String superClassName() {
      return super.className();
    }
  }

  // Version 4
  public static class D___4 extends E {

    @Override
    public String className() {
      return "D";
    }

    @Override
    public String superClassName() {
      return super.className();
    }
  }

  public static class E___4 extends Base {

    @Override
    public String className() {
      return "E";
    }

    public String superClassName() {
      return super.className();
    }
  }

  public static class F___4 extends E {

    @Override
    public String className() {
      return "F";
    }

    @Override
    public String superClassName() {
      return super.className();
    }
  }

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testHierarchySwap() {

    assert __version__() == 0;

    A a = new A();
    B b = new B();

    assertEquals(1, a.value());
    assertEquals(2, b.value());
    assertTrue(b.getClass().getSuperclass().equals(A.class));
    assertFalse(a.getClass().getSuperclass().equals(B.class));

    __toVersion__(1);

    assertEquals(8, a.value());
    assertEquals(4, b.value());
    assertFalse(b.getClass().getSuperclass().equals(A.class));
    assertTrue(a.getClass().getSuperclass().equals(B.class));

    __toVersion__(0);

    assertEquals(1, a.value());
    assertEquals(2, b.value());
    assertTrue(b.getClass().getSuperclass().equals(A.class));
    assertFalse(a.getClass().getSuperclass().equals(B.class));
  }

  @Test
  public void testHierarchySwapWithInstanceOfTest() {

    assert __version__() == 0;

    A a = new A();
    B b = new B();
    C c = new C();

    assertEquals(1, a.value());
    assertEquals(2, b.value());
    assertTrue(c.doInstanceCheckA(b));
    assertFalse(c.doInstanceCheckB(a));

    __toVersion__(1);

    assertEquals(8, a.value());
    assertEquals(4, b.value());
    assertFalse(c.doInstanceCheckA(b));
    assertTrue(c.doInstanceCheckB(a));

    __toVersion__(0);

    assertEquals(1, a.value());
    assertEquals(2, b.value());
    assertTrue(c.doInstanceCheckA(b));
    assertFalse(c.doInstanceCheckB(a));
  }

  @Test
  public void testHierarchySwapWithInstanceOf() {

    assert __version__() == 0;

    A a = new A();
    B b = new B();

    assertEquals(1, a.value());
    assertEquals(2, b.value());
    assertTrue(b instanceof A);
    assertFalse(a instanceof B);

    __toVersion__(1);

    assertEquals(8, a.value());
    assertEquals(4, b.value());
    assertFalse(b instanceof A);
    assertTrue(a instanceof B);

    __toVersion__(0);

    assertEquals(1, a.value());
    assertEquals(2, b.value());
    assertTrue(b instanceof A);
    assertFalse(a instanceof B);
  }

  @Test
  public void testTripleSwap() {


    assert __version__() == 0;

    D d = new D();
    E e = new E();
    F f = new F();

    assertEquals(d.superClassName(), "Base");
    assertEquals(e.superClassName(), "Base");
    assertEquals(f.superClassName(), "Base");

    __toVersion__(2);

    assertEquals(d.superClassName(), "Base");
    assertEquals(e.superClassName(), "D");
    assertEquals(f.superClassName(), "E");

    __toVersion__(3);

    assertEquals(d.superClassName(), "E");
    assertEquals(e.superClassName(), "F");
    assertEquals(f.superClassName(), "Base");

    __toVersion__(4);

    assertEquals(d.superClassName(), "E");
    assertEquals(e.superClassName(), "Base");
    assertEquals(f.superClassName(), "E");

    __toVersion__(3);

    assertEquals(d.superClassName(), "E");
    assertEquals(e.superClassName(), "F");
    assertEquals(f.superClassName(), "Base");

    __toVersion__(2);

    assertEquals(d.superClassName(), "Base");
    assertEquals(e.superClassName(), "D");
    assertEquals(f.superClassName(), "E");

    __toVersion__(0);

    assertEquals(d.superClassName(), "Base");
    assertEquals(e.superClassName(), "Base");
    assertEquals(f.superClassName(), "Base");
  }
}
