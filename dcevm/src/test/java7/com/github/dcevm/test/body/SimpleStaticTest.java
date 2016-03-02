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

package com.github.dcevm.test.body;

import com.github.dcevm.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * @author Thomas Wuerthinger
 */
public class SimpleStaticTest {

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);

    // E and Helper must be loaded and initialized
    E e = new E();
    Helper h = new Helper();
  }

  // Version 0

  public static class Helper {
    public static int getIntegerField() {
      return E.integerField;
    }

    public static void setIntegerField(int x) {
      E.integerField = x;
    }

    public static int getFinalIntegerField() {
      return E.finalIntegerField;
    }
  }

  public static class E {
    public static int integerField = 10;

    public static E self = new E();

    // javac will generate "ConstantValue" attribute for this field!
    public static final int finalIntegerField = 7;
  }

  public static class E___1 {
    public static E___1 self = new E___1();
  }

  // Version 1
  public static class E___2 {
    public static int integerField = 10;

    // javac will generate "ConstantValue" attribute for this field!
    public static final int finalIntegerField = 7;
  }

  @Test
  public void testSimpleNewStaticField() {

    assert __version__() == 0;

    __toVersion__(1);

    TestUtil.assertException(NoSuchFieldError.class, new Runnable() {
      @Override
      public void run() {
        Helper.getIntegerField();
      }
    });

    __toVersion__(2);

    assertEquals(0, Helper.getIntegerField());
    assertEquals(7, Helper.getFinalIntegerField());
    Helper.setIntegerField(1000);
    assertEquals(1000, Helper.getIntegerField());

    __toVersion__(1);

    TestUtil.assertException(NoSuchFieldError.class, new Runnable() {
      @Override
      public void run() {
        Helper.getIntegerField();
      }
    });

    __toVersion__(2);

    assertEquals(0, Helper.getIntegerField());
    assertEquals(7, Helper.getFinalIntegerField());
    Helper.setIntegerField(1000);
    assertEquals(1000, Helper.getIntegerField());

    __toVersion__(0);

    assertEquals(7, Helper.getFinalIntegerField());
    assertEquals(1000, Helper.getIntegerField());
  }
}
