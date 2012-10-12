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

package at.ssw.hotswap.test.structural;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import at.ssw.hotswap.HotSwapTool;
import at.ssw.hotswap.test.TestUtil;

/**
 * Test case for type narrowing where a non-active method fails verification because of the new hierarchy.
 *
 * @author Thomas Wuerthinger
 */
public class TypeNarrowingMethodTest {

    // Version 0
    public static class A {

        int x = 1;
        int y = 2;
        int z = 3;

        public int value() {
            return x;
        }

        public static int badMethod(B b) {
            A a = b;
            return a.y;
        }
    }

    public static class B extends A {

    }


    // Version 1
    public static class B___1 {
    }

    // Version 2
    public static class A___2 {

        int x = 1;
        int y = 2;
        int z = 3;

        public int value() {
            return x;
        }

        public static int badMethod(B b) {
            return 5;
        }
    }

    public static class B___2 {
    }


    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(TypeNarrowingMethodTest.class, 0);
        A a = new A();
        B b = new B();
    }


    @Test
    public void testTypeNarrowingWithViolatingMethod() {

        TestUtil.assertException(UnsupportedOperationException.class, new Runnable() {
            @Override
            public void run() {
                HotSwapTool.toVersion(TypeNarrowingMethodTest.class, 1);
            }
        });

        assert HotSwapTool.getCurrentVersion(TypeNarrowingMethodTest.class) == 0;

        HotSwapTool.toVersion(TypeNarrowingMethodTest.class, 2);

        HotSwapTool.toVersion(TypeNarrowingMethodTest.class, 0);
    }
}
