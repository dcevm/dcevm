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
package at.ssw.hotswap.test.fields;

import static org.junit.Assert.assertEquals;

import at.ssw.hotswap.test.util.HotSwapTestHelper;
import org.junit.Before;
import org.junit.Test;

import static at.ssw.hotswap.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.fail;

/**
 * Tests for accessing a deleted field. In the first scenario, the field is deleted from the class.
 * In the second scenario, it is deleted because of a changed subtype relationship.
 *
 * @author Thomas Wuerthinger
 */
public class AccessDeletedFieldTest {

    @Before
    public void setUp() throws Exception {
        HotSwapTestHelper.__toVersion__(0);
    }

    // Version 0
    public static class A {
        public int x;

        int getFieldInOldCode() {
            HotSwapTestHelper.__toVersion__(1);

            // This field does no longer exist
            return x;
        }
    }

    // Version 1
    public static class A___1 {
    }

    public static class B extends A {
    }

    // Version 2
    public static class B___2 {
    }

    @Test
    public void testOldCodeAccessesDeletedField() {
        assertEquals(0, __version__());

        A a = new A();
        a.x = 1;

        try {
            a.getFieldInOldCode();
            fail("NoSuchFieldError expected!");
        } catch (NoSuchFieldError e) {
            // Expected.
        }

        assertEquals(1, __version__());
        HotSwapTestHelper.__toVersion__(0);
        assertEquals(0, a.x);
    }

    @Test
    public void testAccessDeletedField() {
        assertEquals(0, __version__());

        A a = new A();
        a.x = 1;

        HotSwapTestHelper.__toVersion__(1);

        try {
            System.out.println(a.x);
            fail("NoSuchFieldError expected!");
        } catch (NoSuchFieldError e) {
            // Expected.
        }

        HotSwapTestHelper.__toVersion__(0);
        assertEquals(0, a.x);
    }

    @Test
    public void testAccessDeleteBaseClassFieldNormal() {
        assertEquals(0, __version__());

        A a = new A();
        B b = new B();
        a.x = 2;
        b.x = 1;

        HotSwapTestHelper.__toVersion__(2);

        try {
            System.out.println(b.x);
            fail("NoSuchFieldError expected!");
        } catch (NoSuchFieldError e) {
            // Expected.
        }

        assertEquals(2, a.x);

        HotSwapTestHelper.__toVersion__(0);
        assertEquals(0, b.x);
    }

    @Test
    public void testAccessDeleteBaseClassFieldInvalid() {
        assertEquals(0, __version__());

        A a = new A();
        B b = new B();
        a.x = 1;
        b.x = 1;

        HotSwapTestHelper.__toVersion__(2);

        try {
            System.out.println(b.x);
            fail("NoSuchFieldError expected!");
        } catch (NoSuchFieldError e) {
            // Expected.
        }

        assertEquals(1, a.x);

        HotSwapTestHelper.__toVersion__(0);
        assertEquals(0, b.x);
        assertEquals(1, a.x);

        A a2 = b;
        try {

            HotSwapTestHelper.__toVersion__(2);
            fail("Must fail, because now an instance of B is in a local variable of type A!");
        } catch (UnsupportedOperationException e) {

        }
        assertEquals(0, a2.x);

        // Still at version 0
        assertEquals(0, __version__());
    }
}
