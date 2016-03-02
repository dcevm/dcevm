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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * Test case that produces a list of integer values recursively.
 * The recursive function does not contain a conditional statement.
 * The recursion is stopped by swapping the recursive method with a different non-recursive implementation.
 *
 * @author Thomas Wuerthinger
 */
public class YieldTest {

  // Version 0
  public static class Base {

    protected List<Integer> arr = new ArrayList<Integer>();

    public void reset() {
      __toVersion__(0);
    }

    public void next() {
      __toVersion__(__version__() + 1);
    }
  }

  public static abstract class A extends Base {

    public List<Integer> gen() {
      arr.add(produce());
      next();
      return gen();
    }

    public abstract int produce();
  }

  public static class B extends A {

    @Override
    public int produce() {
      return 1;
    }
  }

  public static class B___10 extends A {

    @Override
    public int produce() {
      return 2;
    }
  }

  public static class B___20 extends A {

    private int x;

    @Override
    public int produce() {
      return ++x;
    }
  }

  public static class A___30 extends Base {

    public List<Integer> gen() {
      reset();
      return arr;
    }
  }

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testYield() {

    assert __version__() == 0;

    B b = new B();
    assertEquals(Arrays.asList(
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10), b.gen());
    assert __version__() == 0;
  }
}
