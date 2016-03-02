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

import com.github.dcevm.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;

/**
 * Adds an implemented interface to a class and tests whether an instance of this class can then really be treated as an instance of the interface.
 * Additionally, performs performance measurements of a call to this interface compared to a proxy object.
 *
 * @author Thomas Wuerthinger
 */
public class AddingInterfaceTest {

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
    assert __version__() == 0;
  }

  public static class A {

    public int getValue() {
      return 1;
    }
  }

  public static interface I {

    public int getValue();
  }

  public static class A___1 implements I {

    @Override
    public int getValue() {
      return 1;
    }
  }

  public static class Proxy implements I {

    private A a;

    public Proxy(A a) {
      this.a = a;
    }

    @Override
    public int getValue() {
      return a.getValue();
    }
  }

  @Test
  public void testAddInterface() {

    A a = new A();
    Proxy p = new Proxy(a);

    final int N = 100000;
    final int Z = 1;


    __toVersion__(1);
    I i = (I) a;

    long startTime = System.currentTimeMillis();
    for (int j = 0; j < Z; j++) {
      calculateSum(N, i);
    }
    long time = System.currentTimeMillis() - startTime;
    System.out.println(time);

    // Must set to null, otherwise local variable i would violate type safety
    i = null;

    TestUtil.assertUnsupportedToVersionWithLight(AddingInterfaceTest.class, 0);

    startTime = System.currentTimeMillis();
    for (int j = 0; j < Z; j++) {
      calculateSum(N, p);
    }
    time = System.currentTimeMillis() - startTime;
    System.out.println(time);
  }

  public int calculateSum(int n, I i) {
    int sum = 0;
    for (int j = 0; j < n; j++) {
      sum += i.getValue();
    }
    return sum;
  }
}
