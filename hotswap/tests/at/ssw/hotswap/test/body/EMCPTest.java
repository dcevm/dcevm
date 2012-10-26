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
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * EMCP (Equivalent modulo Constant Pool) tests.
 *
 * @author Thomas Wuerthinger
 *
 */
public class EMCPTest {

    public static class A {

        public static int EMCPReturn() {
            change();
            PrintStream s = System.out;
            return 1;
        }
    }

    public static class B {

        public static int b() {
            change();
            throw new RuntimeException();
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
            PrintStream s = System.out;
            return 1;
        }
    }

    public static class B___1 {

        public static int b() {
            change();
            throw new RuntimeException();
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

        HotSwapTool.toVersion(EMCPTest.class, 1);
    }

    public static void change3() {

        HotSwapTool.toVersion(EMCPTest.class, 1);
        HotSwapTool.toVersion(EMCPTest.class, 2);
        HotSwapTool.toVersion(EMCPTest.class, 3);
    }

    public static void changeAndThrow() {

        HotSwapTool.toVersion(EMCPTest.class, 1);

        throw new RuntimeException();
    }


    @Test
    public void testEMCPReturn() {
        HotSwapTool.toVersion(EMCPTest.class, 0);

        assertEquals(1, A.EMCPReturn());

        HotSwapTool.toVersion(EMCPTest.class, 0);

        assertEquals(1, A.EMCPReturn());
        
        HotSwapTool.toVersion(EMCPTest.class, 0);
    }
    
    @Test
    public void testEMCPMultiChangeReturn() {
        HotSwapTool.toVersion(EMCPTest.class, 0);

        assertEquals(1, D.EMCPReturn());

        HotSwapTool.toVersion(EMCPTest.class, 0);

        assertEquals(1, D.EMCPReturn());

        HotSwapTool.toVersion(EMCPTest.class, 0);
    }

    @Test
    public void testEMCPException() {
        HotSwapTool.toVersion(EMCPTest.class, 0);

        TestUtil.assertException(RuntimeException.class, new Runnable(){
            @Override
            public void run() {
               B.b();
            }
        });

        HotSwapTool.toVersion(EMCPTest.class, 0);

        TestUtil.assertException(RuntimeException.class, new Runnable(){
            @Override
            public void run() {
               B.b();
            }
        });

        HotSwapTool.toVersion(EMCPTest.class, 0);
    }

    @Test
    public void testEMCPExceptionInCallee() {
        HotSwapTool.toVersion(EMCPTest.class, 0);

        TestUtil.assertException(RuntimeException.class, new Runnable(){
            @Override
            public void run() {
               C.c();
            }
        });

        HotSwapTool.toVersion(EMCPTest.class, 0);

        TestUtil.assertException(RuntimeException.class, new Runnable(){
            @Override
            public void run() {
               C.c();
            }
        });

        HotSwapTool.toVersion(EMCPTest.class, 0);
    }
}
