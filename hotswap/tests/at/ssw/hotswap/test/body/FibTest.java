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

import static at.ssw.hotswap.test.util.HotSwapTestHelper.__toVersion__;
import static at.ssw.hotswap.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.*;

import org.junit.Before;

import org.junit.Test;

import at.ssw.hotswap.HotSwapTool;

/**
 * Recursive implementation of the fibonacci function using class redefinition.
 *
 * @author Thomas Wuerthinger
 */
public class FibTest {
    @Before
    public void setUp() throws Exception {
        __toVersion__(0);
    }

    public static abstract class Base {
        public int calcAt(int version) {
            HotSwapTool.toVersion(FibTest.class, version);
            int result = calc(__version__());
            HotSwapTool.toVersion(FibTest.class, 0);
            return result;
        }

        protected abstract int calc(int version);
    }

    public static class Fib extends Base {

        @Override
        protected int calc(int n) {
            return calcAt(n - 1) + calcAt(n - 2);
        }
    }

    public static class Fib___1 extends Base {

        @Override
        protected int calc(int n) {
            return 1;
        }
    }

    public static class Fib___2 extends Base {

        @Override
        protected int calc(int n) {
            return 2;
        }
    }


    @Test
    public void testFib() {
        assertEquals(0, __version__());

        // 0 1 2 3 4 5
        // 1 1 2 3 5 8
        Fib f = new Fib();

        assertEquals(1, f.calcAt(1));

        assertEquals(0, __version__());
        assertEquals(2, f.calcAt(2));

        assertEquals(0, __version__());
        assertEquals(3, f.calcAt(3));

        assertEquals(0, __version__());
        assertEquals(5, f.calcAt(4));

        assertEquals(0, __version__());
        assertEquals(8, f.calcAt(5));
    }
}
