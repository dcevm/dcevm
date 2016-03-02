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
import com.github.dcevm.test.category.Light;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;

/**
 * Tests that change the type of the object references by the Java this pointer.
 *
 * @author Thomas Wuerthinger
 */
@Category(Light.class)
public class ThisTypeChange {

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  // Version 0
  public static class A {

    public int valueOK() {
      return 1;
    }

    public int value() {
      __toVersion__(1);
      return 1;
    }
  }

  public static class B extends A {

    @Override
    public int value() {
      return super.value();
    }


    @Override
    public int valueOK() {
      __toVersion__(1);
      return super.valueOK();
    }
  }

  // Version 1
  public static class A___1 {

    public int valueOK() {
      return 2;
    }
  }

  // Version 1
  public static class B___1 {
  }

  // Method to enforce cast (otherwise bytecodes become invalid in version 2)
  public static A convertBtoA(Object b) {
    return (A) b;
  }

  @Test
  public void testThisTypeChange() {

    assert __version__() == 0;

    final B b = new B();
    TestUtil.assertUnsupported(new Runnable() {
      @Override
      public void run() {
        b.value();
      }
    });

    assert __version__() == 0;

    TestUtil.assertUnsupported(new Runnable() {
      @Override
      public void run() {
        b.valueOK();
      }
    });

    assert __version__() == 0;

    TestUtil.assertUnsupported(new Runnable() {
      @Override
      public void run() {
        b.valueOK();
      }
    });

    assert __version__() == 0;
  }
}
