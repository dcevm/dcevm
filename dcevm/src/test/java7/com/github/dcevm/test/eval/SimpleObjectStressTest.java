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

package com.github.dcevm.test.eval;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * Test to reveal issues with JIT compiler (one of the compiler threads might be compiling method while we are reloading it).
 */
@Ignore("currently broken -- need to figure out how to avoid compiler crashing while it compiles old code")
public class SimpleObjectStressTest {

  private final int COUNT = 100000;

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  // Version 0
  public static class A {

    public int value() {
      return 0;
    }
  }

  // Version 1
  public static class A___1 {

    public int value() {
      return 1;
    }
  }

  @Test
  public void testLotsOfObjects() {

    assert __version__() == 0;

    A[] arr = new A[COUNT];
    for (int i = 0; i < arr.length; i++) {
      arr[i] = new A();
    }

    for (int k = 0; k < 100; k++) {

      __toVersion__(1);

      for (int i = 0; i < arr.length; i++) {
        assertEquals(1, arr[i].value());
      }

      __toVersion__(0);

      for (int i = 0; i < arr.length; i++) {
        assertEquals(0, arr[i].value());
      }
    }
  }

  public static void main(String... args) {
    new SimpleObjectStressTest().testLotsOfObjects();
  }
}
