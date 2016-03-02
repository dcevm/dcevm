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
 * Tests for adding / removing methods in a single class.
 *
 * @author Thomas Wuerthinger
 */
public class SingleClassTest {

  // Version 0
  public static class A {

    public int value() {
      return 5;
    }
  }

  // Version 3
  public static class A___3 {

    public int value() {
      return 5;
    }
  }

  // Version 1
  public static class A___1 {

    public int value() {
      return 6;
    }

    public int testValue() {
      return 1;

    }
  }

  // Version 2
  public static class A___2 {

    public int value() {
      return baseValue() * 2;
    }

    public int baseValue() {
      return 10;
    }
  }

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testSimpleReplacement() {

    assert __version__() == 0;

    A a = new A();

    assertEquals(5, a.value());

    __toVersion__(1);

    assertEquals(6, a.value());

    __toVersion__(3);

    assertEquals(5, a.value());

    __toVersion__(0);

    assertEquals(5, a.value());
  }

  @Test
  public void testAddMethod() {

    assert __version__() == 0;

    A a = new A();
    assertEquals(a.value(), 5);

    __toVersion__(2);
    assertEquals(a.value(), 20);

    __toVersion__(0);
    assertEquals(a.value(), 5);
  }
}
