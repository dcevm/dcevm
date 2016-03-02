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

import com.github.dcevm.test.TestUtil;
import com.github.dcevm.test.category.Full;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * Test case for type narrowing.
 *
 * @author Thomas Wuerthinger
 */
@Category(Full.class)
public class TypeNarrowingHeapTest {

  // Version 0
  public static class A {

    int x = 1;
    int y = 2;
    int z = 3;

    public int value() {
      return x;
    }
  }

  public static class C {
    private A a;

    public C(A a) {
      this.a = a;
    }
  }

  public static class B extends A {

  }


  // Version 1
  public static class B___1 {
  }


  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
    A a = new A();
    B b = new B();
  }

  @Test
  public void testSimpleTypeNarrowing() {

    assert __version__() == 0;

    A a = convertBtoA(new B());

    assertEquals(1, a.value());

    // Cannot do conversion if A object is on the stack!
    a = null;

    __toVersion__(1);

    TestUtil.assertException(NoSuchMethodError.class, new Runnable() {
      @Override
      public void run() {
        B b = new B();
        b.value();
      }
    });

    __toVersion__(0);
    assert __version__() == 0;
  }

  @Test
  public void testTypeNarrowingWithField() {
    C c = new C(new A());

    __toVersion__(1);

    __toVersion__(0);

    c = new C(convertBtoA(new B()));

    TestUtil.assertException(UnsupportedOperationException.class, new Runnable() {
      @Override
      public void run() {
        __toVersion__(1);
      }
    });

    assert __version__() == 0;

    c.a = null;

    __toVersion__(1);

    __toVersion__(0);
  }

  // Method to enforce cast (otherwise bytecodes become invalid in version 2)
  public static A convertBtoA(Object b) {
    return (A) b;
  }

  @Test
  public void testTypeNarrowingWithArray() {
    final B b = new B();
    final A[] arr = new A[3];
    arr[0] = new A();

    assert b instanceof A;

    __toVersion__(1);

    assert !(b instanceof A);

    TestUtil.assertException(ArrayStoreException.class, new Runnable() {
      @Override
      public void run() {
        arr[1] = b;
      }
    });

    __toVersion__(0);

    arr[1] = new B();

    TestUtil.assertException(UnsupportedOperationException.class, new Runnable() {
      @Override
      public void run() {
        __toVersion__(1);
      }
    });

    assert __version__() == 0;

    assert b instanceof A;

    arr[1] = new A();

    __toVersion__(1);

    assert !(b instanceof A);

    __toVersion__(0);

    assert b instanceof A;
  }
}
