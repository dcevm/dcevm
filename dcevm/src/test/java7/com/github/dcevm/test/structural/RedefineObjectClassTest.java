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

import com.github.dcevm.ClassRedefinitionPolicy;
import com.github.dcevm.test.category.Full;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * Smallest test case for redefining java/lang/Object, without changing the number of virtual methods.
 *
 * @author Thomas Wuerthinger
 */
@Category(Full.class)
@Ignore
public class RedefineObjectClassTest {

  // Version 0
  public static class Helper {
    public static String access(Object o) {
      return "";
    }
  }

  // Version 1

  public static class Helper___1 {
    public static String access(Object o) {
      return ((A___1) o).myTestFunction___();
    }
  }

  @ClassRedefinitionPolicy(alias = java.lang.Object.class)
  public static class A___1 {

    public final native Class<? extends Object> getClass___();

    public native int hashCode();

    public boolean equals(Object obj) {
      return (this == obj);
    }

    public static int x;
    public static int x1;
    public static int x2;
    public static int x3;
    public static int x4;
    public static int x5;

    protected native Object clone() throws CloneNotSupportedException;

    public String toString() {
      System.out.println("x=" + (x++));
      return getClass().getName() + "@" + Integer.toHexString(hashCode());// myTestFunction___();
    }

    public final String myTestFunction___() {
      return "test";
    }

    public final native void notify___();

    public final native void notifyAll___();

    public final native void wait___(long timeout) throws InterruptedException;

    public final void wait___(long timeout, int nanos) throws InterruptedException {


      if (timeout < 0) {
        throw new IllegalArgumentException("timeout value is negative");
      }

      if (nanos < 0 || nanos > 999999) {
        throw new IllegalArgumentException(
                "nanosecond timeout value out of range");
      }

      if (nanos >= 500000 || (nanos != 0 && timeout == 0)) {
        timeout++;
      }

      wait(timeout);
    }

    public final void wait___() throws InterruptedException {
      wait(0);
    }

    protected void finalize() throws Throwable {
    }
  }

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testRedefineObject() {

    assert __version__() == 0;

    Object o = new Object();
    __toVersion__(1);

    System.out.println(this.toString());
    System.out.println(o.toString());
    System.out.println(this.toString());


    //assertEquals("test", o.toString());
    assertEquals("test", Helper.access(o));
    __toVersion__(0);
    __toVersion__(1);
    __toVersion__(0);
  }
}
