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
package at.ssw.hotswap.test.body;

import at.ssw.hotswap.HotSwapTool;
import at.ssw.hotswap.test.TestUtil;

import java.io.PrintStream;

import static at.ssw.hotswap.test.util.HotSwapTestHelper.__toVersion__;
import static at.ssw.hotswap.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * EMCP (Equivalent modulo Constant Pool) tests.
 *
 * @author Thomas Wuerthinger
 */
public class EMCPTest {

    @Before
    public void setUp() throws Exception {
        __toVersion__(0);
    }

    public static class A {
        public static int EMCPReturn() {
            change();
            return 1;
        }
    }

    public static class B {
        public static int b() {
            change();
            throw new ExpectedException();
        }
    }

    public static class C {
        public static int c() {
            changeAndThrow();
            return 0;
        }
    }

    public static class D {
        private static int value = 1;

        public static int EMCPReturn() {
            change3();
            return value;
        }
    }

    public static class A___1 {
        public static int EMCPReturn() {
            change();
            return 1;
        }
    }

    public static class B___1 {
        public static int b() {
            change();
            throw new ExpectedException();
        }
    }

    public static class C___1 {
        public static int c() {
            changeAndThrow();
            return 0;
        }
    }

    public static class D___1 {
        private static int value = 1;

        public static int EMCPReturn() {
            change3();
            return value;
        }
    }

    public static class D___2 {
        private static int value = 1;

        public static int EMCPReturn() {
            change3();
            return value;
        }
    }

    public static class D___3 {
        private static int value = 1;

        public static int EMCPReturn() {
            change3();
            return value;
        }
    }

    public static void change() {
        __toVersion__(1);
    }

    public static void change3() {
        __toVersion__(1);
        __toVersion__(2);
        __toVersion__(3);
    }

    public static void changeAndThrow() {
        __toVersion__(1);
        throw new ExpectedException();
    }


    @Test
    public void testEMCPReturn() {
        assertEquals(0, __version__());

        __toVersion__(0);
        assertEquals(1, A.EMCPReturn());

        __toVersion__(0);
        assertEquals(1, A.EMCPReturn());

        __toVersion__(0);
    }

    @Test
    public void testEMCPMultiChangeReturn() {
        assertEquals(0, __version__());

        __toVersion__(0);
        assertEquals(1, D.EMCPReturn());

        __toVersion__(0);
        assertEquals(1, D.EMCPReturn());

        __toVersion__(0);
    }

    @Test
    public void testEMCPException() {
        assertEquals(0, __version__());

        __toVersion__(0);
        try {
            B.b();
            fail("ExpectedException expected!");
        } catch (ExpectedException e) {
            // Expected.
        }

        __toVersion__(0);
        try {
            B.b();
            fail("ExpectedException expected!");
        } catch (ExpectedException e) {
            // Expected.
        }

        __toVersion__(0);
    }

    @Test
    public void testEMCPExceptionInCallee() {
        assertEquals(0, __version__());

        __toVersion__(0);
        try {
            C.c();
            fail("ExpectedException expected!");
        } catch (ExpectedException e) {
            // Expected.
        }

        __toVersion__(0);
        try {
            C.c();
            fail("ExpectedException expected!");
        } catch (ExpectedException e) {
            // Expected.
        }

        __toVersion__(0);
    }

    private static class ExpectedException extends RuntimeException {

    }
}
