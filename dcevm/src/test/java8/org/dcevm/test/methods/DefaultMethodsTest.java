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

package org.dcevm.test.methods;

import org.junit.Before;
import org.junit.Test;

import static org.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static org.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * Tests for the class relationship A<B<C with adding / removing methods.
 *
 * @author Thomas Wuerthinger
 */
public class DefaultMethodsTest {

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  // Version 0
  public static interface A {
    default int value() {
      __toVersion__(1);
      return 1 + value();
    }
  }

  public static class B implements A {
  }

  public static interface C {
    int value();
  }

  public static class D implements C {
    @Override
    public int value() {
      __toVersion__(2);
      return 3 + value();
    }
  }

  // Version 1
  public static interface A___1 {
    int value();
  }


  public static class B___1 implements A {
    public int value() {
      return 2;
    }
  }

  // Version 2
  public static interface C___2 {
    default int value() {
      return 5;
    }
  }

  public static class D___2 implements C___2 {
  }

  @Test
  public void testDefaultMethodReplacedWithInstance() {
    assert __version__() == 0;

    A a = new B();
    assertEquals(3, a.value());

    __toVersion__(0);
  }

  @Test
  public void testInstanceMethodReplacedWithDefault() {
    assert __version__() == 0;

    C c = new D();
    assertEquals(8, c.value());

    __toVersion__(0);
  }
}
