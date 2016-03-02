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

import com.github.dcevm.test.TestUtil;
import com.github.dcevm.test.category.Full;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * Tests for accessing a deleted static field.
 *
 * @author Thomas Wuerthinger
 */
public class AccessDeletedStaticFieldTest {

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  // Version 0
  public static class A {

    public static int x;

    static int getFieldInOldCode() {

      __toVersion__(1);

      newMethodFromOldCode();

      // This field does no longer exist
      return x;
    }

    static int getFieldEMCPMethod() {
      __toVersion__(2);
      return A.x;
    }
  }

  // Version 1
  public static class A___1 {
  }

  // Version 2

  public static class A___2 {

    // EMCP to method in version 0
    static int getFieldEMCPMethod() {
      __toVersion__(2);
      return A.x;
    }
  }

  private static void newMethodFromOldCode() {
    TestUtil.assertException(NoSuchFieldError.class, new Runnable() {
      @Override
      public void run() {
        System.out.println(A.x);
      }
    });
  }

  @Test
  @Category(Full.class)
  public void testAccessDeletedStaticField() {

    assert __version__() == 0;

    A.x = 1;
    assertEquals(1, A.getFieldInOldCode());

    assert __version__() == 1;
    __toVersion__(0);
    assertEquals(0, A.x);

    assert __version__() == 0;
  }


  @Test
  public void testAccessDeletedStaticFieldFromEMCPMethod() {

    assert __version__() == 0;
    TestUtil.assertException(NoSuchFieldError.class, new Runnable() {
      @Override
      public void run() {
        System.out.println(A.getFieldEMCPMethod());
      }
    });

    __toVersion__(0);
    assertEquals(0, A.x);

    assert __version__() == 0;
  }
}
