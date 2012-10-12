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

package at.ssw.hotswap.test.natives;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import at.ssw.hotswap.HotSwapTool;

/**
 * Testing correct resolving of a native method after class redefinition.
 *
 * @author Thomas Wuerthinger
 */
public class SimpleNativeTest {

    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(SimpleNativeTest.class, 0);
    }

    // Version 0
    public static class A {

        public static int get() {
            return value();
        }

        public static native int value();
    }

    // Version 1
    public static class A___1 {

        public static int get() {
            return value() + value2();
        }

        public static native int value();

        public static native int value2();
    }

    @Test
    public void testSimpleNativeCalls() {

        assert HotSwapTool.getCurrentVersion(SimpleNativeTest.class) == 0;


        assertEquals(1, A.get());

        HotSwapTool.toVersion(SimpleNativeTest.class, 1);

        assertEquals(3, A.get());

        HotSwapTool.toVersion(SimpleNativeTest.class, 0);

        assertEquals(1, A.get());

    }

}
