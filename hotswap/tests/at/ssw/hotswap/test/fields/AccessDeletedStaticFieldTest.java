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

import at.ssw.hotswap.test.util.HotSwapTestHelper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static at.ssw.hotswap.test.util.HotSwapTestHelper.__version__;

/**
 * Tests for accessing a deleted static field.
 *
 * @author Thomas Wuerthinger
 */
public class AccessDeletedStaticFieldTest {

    @Before
    public void setUp() throws Exception {
        HotSwapTestHelper.__toVersion__(0);
    }

    // Version 0
    public static class A {
        public static int x = -1;

        static int getFieldInOldCode() {
            HotSwapTestHelper.__toVersion__(1);

            newMethodFromOldCode();

            // This field does no longer exist
            return x;
        }
    }

    // Version 1
    public static class A___1 {
    }

    // Version 0
    public static class B {
        public static int x;

        static int getFieldEMCPMethod() {
            HotSwapTestHelper.__toVersion__(1);
            return B.x;
        }
    }

    // Version 1
    public static class B___1 {
        // EMCP to method in version 0
        static int getFieldEMCPMethod() {
            HotSwapTestHelper.__toVersion__(1);
            return B.x;
        }
    }

    private static void newMethodFromOldCode() {
        try {
            A.x += 1;
            fail("NoSuchFieldError expected!");
        } catch(NoSuchFieldError e) {
            // Expected.
        }
    }

    @Test
    public void testAccessDeletedStaticField() {
        assertEquals(0, __version__());

        A.x = 1;
        assertEquals(1, A.getFieldInOldCode());

        assertEquals(1, __version__());
        HotSwapTestHelper.__toVersion__(0);
        assertEquals(0, A.x);
        
        assertEquals(0, __version__());
    }


    @Test
    public void testAccessDeletedStaticFieldFromEMCPMethod() {
        assertEquals(0, __version__());

        B.x = 1;

        try {
            System.out.println(B.getFieldEMCPMethod());
            fail("NoSuchFieldError expected!");
        } catch(NoSuchFieldError e) {
            // Expected.
        }

        HotSwapTestHelper.__toVersion__(0);
        assertEquals(0, B.x); // Not initialized, so value is 0
        assertEquals(0, __version__());
    }
}
