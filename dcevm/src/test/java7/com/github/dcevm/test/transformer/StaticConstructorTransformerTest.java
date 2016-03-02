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
package com.github.dcevm.test.transformer;

import org.junit.Before;
import org.junit.Test;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class StaticConstructorTransformerTest {

  //Version 0
  public static class Static_TestClass {

    // remove static --> no fatal error occurs
    public static int x = 0;
    //public int x = 0;

    static {
      System.out.println("Start Static_TestClass Version 0");

      try {
        Thread.sleep(1000);
      } catch (InterruptedException ex) {
      }
      System.out.println("End Static_TestClass Version 0");
    }
  }

  //Version 1
  public static class Static_TestClass___1 {

    public int version = 1;

    static {
      System.out.println("Static_TestClass Version 1");
    }

    public void $transformer() {
      System.out.println(":::::::::transformerExecuted:::::::::::");
    }
  }

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testStaticConstructorTransformerTest() {

    assert __version__() == 0;
    try {
      Class.forName("at.ssw.hotswap.test.transformer.StaticConstructorTransformerTest$Static_TestClass");
    } catch (ClassNotFoundException ex) {
      ex.printStackTrace();
    }
    Static_TestClass clazz = new Static_TestClass();

    __toVersion__(1);
  }
}
