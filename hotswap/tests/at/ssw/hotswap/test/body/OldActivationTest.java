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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import at.ssw.hotswap.HotSwapTool;

/**
 * Stress test for the number of old activations on the stack. In the test setup 10 different versions of the method A.value will be on the stack.
 *
 * @author Thomas Wuerthinger
 */
public class OldActivationTest {

    // Version 0
    public static class A {

        public int value() {
            HotSwapTool.toVersion(OldActivationTest.class, 1);
            return 1 + this.value();
        }
    }

    // Version 1
    public static class A___1 {

        public int value() {
            HotSwapTool.toVersion(OldActivationTest.class, 2);
            return 2 + this.value();
        }
    }

    // Version 2
    public static class A___2 {

        public int value() {
            HotSwapTool.toVersion(OldActivationTest.class, 3);
            return 3 + this.value();
        }
    }

    // Version 3
    public static class A___3 {

        public int value() {
            HotSwapTool.toVersion(OldActivationTest.class, 4);
            return 4 + this.value();
        }
    }

    // Version 4
    public static class A___4 {

        public int value() {
            HotSwapTool.toVersion(OldActivationTest.class, 5);
            return 5 + this.value();
        }
    }

    // Version 5
    public static class A___5 {

        public int value() {
            HotSwapTool.toVersion(OldActivationTest.class, 6);
            return 6 + this.value();
        }
    }

    // Version 6
    public static class A___6 {

        public int value() {
            HotSwapTool.toVersion(OldActivationTest.class, 7);
            return 7 + this.value();
        }
    }

    // Version 7
    public static class A___7 {

        public int value() {
            HotSwapTool.toVersion(OldActivationTest.class, 8);
            return 8 + this.value();
        }
    }

    // Version 8
    public static class A___8 {

        public int value() {
            HotSwapTool.toVersion(OldActivationTest.class, 9);
            return 9 + this.value();
        }
    }

    // Version 9
    public static class A___9 {

        public int value() {
            HotSwapTool.toVersion(OldActivationTest.class, 0);
            return 10;
        }
    }

    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(OldActivationTest.class, 0);
    }

    @Test
    public void testOldActivationTest() {

        assert HotSwapTool.getCurrentVersion(OldActivationTest.class) == 0;

        A a = new A();

        assertEquals(1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10, a.value());
        assert HotSwapTool.getCurrentVersion(OldActivationTest.class) == 0;

        HotSwapTool.toVersion(OldActivationTest.class, 1);
        assertEquals(2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10, a.value());
        assert HotSwapTool.getCurrentVersion(OldActivationTest.class) == 0;

        HotSwapTool.toVersion(OldActivationTest.class, 8);
        assertEquals(9 + 10, a.value());
        assert HotSwapTool.getCurrentVersion(OldActivationTest.class) == 0;

        HotSwapTool.toVersion(OldActivationTest.class, 4);
        assertEquals(5 + 6 + 7 + 8 + 9 + 10, a.value());
        assert HotSwapTool.getCurrentVersion(OldActivationTest.class) == 0;
    }
}
