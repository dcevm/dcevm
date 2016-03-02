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

import com.github.dcevm.test.category.Full;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * Test case that makes sure that old code does not get on-stack-replaced.
 *
 * @author Thomas Wuerthinger
 */
@Category(Full.class)
public class OldCodeNonOSRTest {

  // Chose high enough to make sure method could get OSR (usually the OSR flag in the VM is set to about 15000)
  private static final int N = 100000;

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  // Version 0
  public static class A {

    public int value() {
      return 5;
    }

    public int oldMethod() {
      __toVersion__(1);
      int sum = 0;
      for (int i = 0; i < N; i++) {
        sum += i;
      }
      return (sum & deletedMethod()) | 1;
    }

    public int oldMethod2() {
      int sum = 0;
      for (int i = 0; i < N; i++) {
        sum += i;
      }
      __toVersion__(1);
      return (sum & deletedMethod()) | 1;
    }

    public int oldMethod3() {
      int sum = 0;
      for (int i = 0; i < N; i++) {
        sum += i;
      }
      __toVersion__(1);
      for (int i = 0; i < N; i++) {
        sum += i;
      }
      return (sum & deletedMethod()) | 1;
    }

    public int deletedMethod() {
      return 1;
    }
  }

  // Version 1
  public static class A___1 {

    public int oldMethod() {
      return 2;
    }
  }

  @Test
  public void testOldCodeNonOSR() {

    assert __version__() == 0;
    A a = new A();

    assertEquals(1, a.oldMethod());
    assert __version__() == 1;
    assertEquals(2, a.oldMethod());

    __toVersion__(0);

    assertEquals(1, a.oldMethod2());
    assert __version__() == 1;
    assertEquals(2, a.oldMethod());

    __toVersion__(0);

    assertEquals(1, a.oldMethod3());
    assert __version__() == 1;
    assertEquals(2, a.oldMethod());

    __toVersion__(0);
  }
}
