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

package com.github.dcevm.test;

import com.github.dcevm.HotSwapTool;
import org.junit.Assert;
import java.lang.reflect.Field;

/**
 * Utility methods for unit testing.
 *
 * @author Thomas Wuerthinger
 */
public class TestUtil {
  public static final boolean LIGHT = Boolean.getBoolean("dcevm.test.light");

  public static int assertException(Class exceptionClass, Runnable run) {

    try {
      run.run();
    } catch (Throwable t) {
      if (t.getClass().equals(exceptionClass)) {
        return t.getStackTrace()[0].getLineNumber();
      }
      Assert.assertTrue("An exception of type " + t.getClass().getSimpleName() + " instead of " + exceptionClass.getSimpleName() + " has been thrown!", false);
    }

    Assert.assertTrue("No exception has been thrown!", false);
    return -1;
  }

  public static int assertUnsupportedWithLight(Runnable run) {
    if (TestUtil.LIGHT) {
      return assertUnsupported(run);
    }
    run.run();
    return -1;
  }

  public static int assertUnsupported(Runnable run) {
    return assertException(UnsupportedOperationException.class, run);
  }

  public static void assertUnsupportedToVersionWithLight(final Class clazz, final int version) {
    TestUtil.assertUnsupportedWithLight(new Runnable() {
      @Override
      public void run() {
        HotSwapTool.toVersion(clazz, version);
      }
    });
  }

  public static int getClassRedefinedCount(Class type) {
    try {
      Field field = Class.class.getDeclaredField("classRedefinedCount");
      boolean accessibility = field.isAccessible();
      field.setAccessible(true);
      int classRedefinedCount = (Integer) field.get(type);
      field.setAccessible(accessibility);
      return  classRedefinedCount;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
