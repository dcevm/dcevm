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

import org.junit.Before;
import org.junit.Test;

import at.ssw.hotswap.HotSwapTool;
import at.ssw.hotswap.test.TestUtil;

/**
 * Tests for accessing a deleted field. In the first scenario, the field is deleted from the class.
 * In the second scenario, it is deleted because of a changed subtype relationship.
 *
 * @author Thomas Wuerthinger
 */
public class AccessDeletedFieldTest {

    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(AccessDeletedFieldTest.class, 0);
    }

    // Version 0
    public static class A {

        public int x;

        int getFieldInOldCode() {
            
            HotSwapTool.toVersion(AccessDeletedFieldTest.class, 1);

            // This field does no longer exist
            return x;
        }
    }

    public static class B extends A {
    }

    // Version 1
    public static class A___1 {
    }

    // Version 2
    public static class B___2 {
    }

    // Method to enforce cast (otherwise bytecodes become invalid in version 2)
    public static A convertBtoA(Object b) {
        return (A) b;
    }

    @Test
    public void testOldCodeAccessesDeletedField() {

        assert HotSwapTool.getCurrentVersion(AccessDeletedFieldTest.class) == 0;

        final A a = new A();
        a.x = 1;

        TestUtil.assertException(NoSuchFieldError.class, new Runnable() {
            @Override
            public void run() {
                assertEquals(0, a.getFieldInOldCode());
            }
        });

        assert HotSwapTool.getCurrentVersion(AccessDeletedFieldTest.class) == 1;
        HotSwapTool.toVersion(AccessDeletedFieldTest.class, 0);
        assertEquals(0, a.x);
    }

    @Test
    public void testAccessDeletedField() {

        assert HotSwapTool.getCurrentVersion(AccessDeletedFieldTest.class) == 0;

        final A a = new A();
        a.x = 1;

        assertEquals(1, a.x);

        HotSwapTool.toVersion(AccessDeletedFieldTest.class, 1);

        TestUtil.assertException(NoSuchFieldError.class, new Runnable() {
            @Override
            public void run() {
                System.out.println(a.x);
            }
        });

        HotSwapTool.toVersion(AccessDeletedFieldTest.class, 0);
        assertEquals(0, a.x);
    }

    @Test
    public void testAccessDeleteBaseClassFieldNormal() {

        HotSwapTool.toVersion(AccessDeletedFieldTest.class, 0);
        assert HotSwapTool.getCurrentVersion(AccessDeletedFieldTest.class) == 0;
        final B b = new B();
        b.x = 1;
        final A a = new A();
        a.x = 2;

        assertEquals(1, b.x);
        assertEquals(2, a.x);

        HotSwapTool.toVersion(AccessDeletedFieldTest.class, 2);

        TestUtil.assertException(NoSuchFieldError.class, new Runnable() {

            @Override
            public void run() {
                System.out.println(b.x);
            }
        });

        assertEquals(2, a.x);

        HotSwapTool.toVersion(AccessDeletedFieldTest.class, 0);
        assertEquals(0, b.x);
    }

    @Test
    public void testAccessDeleteBaseClassFieldInvalid() {

        HotSwapTool.toVersion(AccessDeletedFieldTest.class, 0);
        assert HotSwapTool.getCurrentVersion(AccessDeletedFieldTest.class) == 0;
        final B b = new B();
        final A a1 = new A();
        a1.x = 1;
        b.x = 1;

        HotSwapTool.toVersion(AccessDeletedFieldTest.class, 2);

        TestUtil.assertException(NoSuchFieldError.class, new Runnable() {

            @Override
            public void run() {
                System.out.println(b.x);
            }
        });

        assertEquals(1, a1.x);

        HotSwapTool.toVersion(AccessDeletedFieldTest.class, 0);
        assertEquals(0, b.x);
        assertEquals(1, a1.x);

        A a = convertBtoA(b);

        assertEquals(0, b.x);

        // Must fail, because now an instance of B is in a local variable of type A!
        TestUtil.assertException(UnsupportedOperationException.class, new Runnable() {

            @Override
            public void run() {
                HotSwapTool.toVersion(AccessDeletedFieldTest.class, 2);
            }
        });

        assertEquals(0, a.x);

        // Still at version 0
        assert HotSwapTool.getCurrentVersion(AccessDeletedFieldTest.class) == 0;
    }
}
