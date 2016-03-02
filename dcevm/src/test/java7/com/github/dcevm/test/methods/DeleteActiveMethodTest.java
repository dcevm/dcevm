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

import com.github.dcevm.MethodRedefinitionPolicy;
import com.github.dcevm.RedefinitionPolicy;
import com.github.dcevm.test.TestUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * Test cases that delete a method that is currently active on the stack.
 *
 * @author Thomas Wuerthinger
 */
public class DeleteActiveMethodTest {

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  // Version 0
  public static class A {

    boolean firstCall;

    public int value() {
      firstCall = true;
      return helperValue();
    }

    public int helperValue() {

      if (!firstCall) {
        return -1;
      }
      firstCall = false;

      Thread t = new Thread(new Runnable() {

        @Override
        public void run() {
          __toVersion__(1);
        }
      });
      t.start();

      try {
        while (t.isAlive()) {
          try {
            this.helperValue();
            Thread.sleep(500);
          } catch (InterruptedException e) {
          }
          helperValue();
        }
        Assert.fail("Exception expected!");
      } catch (NoSuchMethodError e) {
      }

      try {
        t.join();
      } catch (InterruptedException e) {
      }

      return 1;
    }
  }

  public static class B {

    public int fac(int x) {
      if (x == 0) {
        __toVersion__(1);
      }

      return x * fac(x - 1);
    }
  }

  // Version 1
  @MethodRedefinitionPolicy(RedefinitionPolicy.DynamicCheck)
  public static class A___1 {

    boolean firstCall;

    public int value() {
      __toVersion__(0);
      return 2;
    }
  }

  @MethodRedefinitionPolicy(RedefinitionPolicy.DynamicCheck)
  public static class B___1 {
  }

  @Test
  public void testDeleteActiveMethodSimple() {
    assert __version__() == 0;

    final B b = new B();
    TestUtil.assertException(NoSuchMethodError.class, new Runnable() {
      @Override
      public void run() {
        b.fac(5);
      }
    });

    assert __version__() == 1;

    __toVersion__(0);
    assert __version__() == 0;
  }

  @Test
  public void testDeleteActiveMethod() {
    assert __version__() == 0;

    A a = new A();

    assertEquals(1, a.value());
    assert __version__() == 1;

    assertEquals(2, a.value());
    assert __version__() == 0;

    assertEquals(1, a.value());
    assert __version__() == 1;

    assertEquals(2, a.value());
    assert __version__() == 0;

    assertEquals(1, a.value());
    assert __version__() == 1;

    assertEquals(2, a.value());
    assert __version__() == 0;
  }
}
