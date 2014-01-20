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
package org.dcevm.test.methods;

import org.junit.Before;
import org.junit.Test;

import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static org.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;

/**
 * Tests for adding / removing annotations on methods, fields and classes.
 *
 * @author Thomas Wuerthinger
 * @author Jiri Bubnik
 */
public class AnnotationTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
    public @interface TestAnnotation {
    }

    // Version 0
    public static class A {
        public int testField;

        public void testMethod() {
        }
    }

    // Version 1
    @TestAnnotation
    public static class A___1 {
        @TestAnnotation
        public int testField;

        @TestAnnotation
        public void testMethod() {
        }
    }

    @Before
    public void setUp() throws Exception {
        __toVersion__(0);
    }

    private void checkAnnotation(Class<?> c, boolean shouldBePresent) throws NoSuchMethodException, NoSuchFieldException {
        Annotation annotation = c.getAnnotation(TestAnnotation.class);
        assertEquals(annotation != null, shouldBePresent);
        Method m = c.getMethod("testMethod");
        annotation = m.getAnnotation(TestAnnotation.class);
        assertEquals(annotation != null, shouldBePresent);
        Field f = c.getField("testField");
        annotation = f.getAnnotation(TestAnnotation.class);
        assertEquals(annotation != null, shouldBePresent);
    }

    @Test
    public void testAddMethodToKlassWithEMCPExceptionMethod() throws NoSuchMethodException, NoSuchFieldException {

        assert __version__() == 0;
        checkAnnotation(A.class, false);
        __toVersion__(1);
        checkAnnotation(A.class, true);
        __toVersion__(0);
        checkAnnotation(A.class, false);
    }
}