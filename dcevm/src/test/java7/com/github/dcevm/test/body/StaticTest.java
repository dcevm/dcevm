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

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.*;

/**
 * @author Thomas Wuerthinger
 */
public class StaticTest {

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  // Version 0


  public static class Helper {
    public static int getAdditionalField() {
      return -1;
    }

    public static void setAdditionalField(int x) {

    }
  }

  public static class A {

    public static int value() {
      return 1;
    }
  }

  public static class B {

    public static int value() {
      return 2;
    }
  }

  public static class C {
    static {
      System.out.println("Static initializer of C");
    }

    public static int value = 5;
  }

  public static class D {
    public static List objectField = new ArrayList();
    public static int[] arrayField = new int[10];
    public static int integerField = 5;
    public static char characterField = 6;
    public static short shortField = 7;
    public static double doubleField = 1.0;
    public static float floatField = 2.0f;
    public static long longField = 8;
    public static boolean booleanField = true;
  }

  // Version 1
  public static class A___1 {

    public static int value() {
      return B.value() * 2;
    }
  }

  // Version 2
  public static class B___2 {

    public static int value() {
      return 3;
    }
  }

  // Version 3
  public static class A___3 {

    public static int value() {
      return 5;
    }
  }

  public static class B___3 {

    public static int value() {
      return A.value() * 2;
    }
  }

  // Version 4
  public static class C___4 {

    static {
      System.out.println("Static initializer of C-4");
    }

    public static int value = 6;
  }

  public static class Helper___5 {
    public static int getAdditionalField() {
      return D___5.additionalField;
    }

    public static void setAdditionalField(int x) {
      D___5.additionalField = x;
    }
  }

  public static class D___5 {
    public static int additionalField;

    public static List objectField;
    public static long longField;
    public static short shortField = 10;
    public static float floatField;
    public static int[] arrayField;
    public static int integerField;
    public static char characterField;
    public static double doubleField;
    public static boolean booleanField;
  }

  public static class E {
    public static Class<?> eClass = E.class;
    public static Class<?> eClassArr = E[].class;
    public static Class<?> eClassNull;
    public static Class<?> eClassPrim = Integer.TYPE;
  }

  public static class E___6 {
    public static Class<?> eClass;
    public static Class<?> eClassArr;
    public static Class<?> eClassNull;
    public static Class<?> eClassPrim;
  }

  @Test
  public void testBase() {

    assert __version__() == 0;


    assertEquals(1, A.value());
    assertEquals(2, B.value());

    __toVersion__(1);

    assertEquals(4, A.value());
    assertEquals(2, B.value());

    __toVersion__(2);

    assertEquals(6, A.value());
    assertEquals(3, B.value());

    __toVersion__(3);

    assertEquals(5, A.value());
    assertEquals(10, B.value());

    __toVersion__(0);

    assertEquals(1, A.value());
    assertEquals(2, B.value());
  }

  @Test
  public void testStaticField() {

    assert __version__() == 0;
    assertEquals(5, C.value);

    __toVersion__(4);
    assertEquals(5, C.value);

    __toVersion__(0);
    assertEquals(5, C.value);
  }

  @Test
  public void testStaticFieldUpdated() {
    assert __version__() == 0;
    assertEquals(E.class, E.eClass);
    assertNull(E.eClassNull);
    assertEquals(E[].class, E.eClassArr);

    __toVersion__(6);
    assertEquals(E.class, E.eClass);
    assertNull(E.eClassNull);
    assertEquals(E[].class, E.eClassArr);
  }

  @Test
  public void testManyStaticFields() {

    assert __version__() == 0;
    assertTrue(D.objectField != null);
    assertTrue(D.arrayField != null);
    assertEquals(5, D.integerField);
    assertEquals(6, D.characterField);
    assertEquals(7, D.shortField);
    assertEquals(1.0, D.doubleField, 0.0);
    assertEquals(2.0f, D.floatField, 0.0);
    assertEquals(8, D.longField);
    assertEquals(true, D.booleanField);

    __toVersion__(5);
    assertTrue(D.objectField != null);
    assertTrue(D.arrayField != null);
    assertEquals(5, D.integerField);
    assertEquals(6, D.characterField);
    assertEquals(7, D.shortField);
    assertEquals(1.0, D.doubleField, 0.0);
    assertEquals(2.0f, D.floatField, 0.0);
    assertEquals(8, D.longField);
    assertEquals(true, D.booleanField);

    assertEquals(0, Helper.getAdditionalField());
    Helper.setAdditionalField(1000);
    assertEquals(1000, Helper.getAdditionalField());


    __toVersion__(0);

    assertTrue(D.objectField != null);
    assertTrue(D.arrayField != null);
    assertEquals(5, D.integerField);
    assertEquals(6, D.characterField);
    assertEquals(7, D.shortField);
    assertEquals(1.0, D.doubleField, 0.0);
    assertEquals(2.0f, D.floatField, 0.0);
    assertEquals(8, D.longField);
    assertEquals(true, D.booleanField);

    __toVersion__(5);
    assertTrue(D.objectField != null);
    assertTrue(D.arrayField != null);
    assertEquals(5, D.integerField);
    assertEquals(6, D.characterField);
    assertEquals(7, D.shortField);
    assertEquals(1.0, D.doubleField, 0.0);
    assertEquals(2.0f, D.floatField, 0.0);
    assertEquals(8, D.longField);
    assertEquals(true, D.booleanField);

    assertEquals(0, Helper.getAdditionalField());

    __toVersion__(0);
    assertTrue(D.objectField != null);
    assertTrue(D.arrayField != null);
    assertEquals(5, D.integerField);
    assertEquals(6, D.characterField);
    assertEquals(7, D.shortField);
    assertEquals(1.0, D.doubleField, 0.0);
    assertEquals(2.0f, D.floatField, 0.0);
    assertEquals(8, D.longField);
    assertEquals(true, D.booleanField);

  }


}
