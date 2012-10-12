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

package at.ssw.hotswap.test.methods;

import static org.junit.Assert.assertEquals;
import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import at.ssw.hotswap.HotSwapTool;

/**
 * Test case that calls an interface method that was deleted through class redefinition.
 *
 * @author Thomas Wuerthinger
 */
public class CallDeletedInterfaceMethodTest {

    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(CallDeletedInterfaceMethodTest.class, 0);
    }

    // Version 0
    public static interface I {
        public int foo();
    }

    public static class A implements I {
        @Override
        public int foo() {
            return 1;
        }
    }

    public static class Helper {
        public static int process(I i) {
            HotSwapTool.toVersion(CallDeletedInterfaceMethodTest.class, 1);
            return i.foo();
        }
    }

    // Version 1
    public static interface I___1 {
        
    }

    public static class Helper___1 {
        public static int process(I i) {
            return 2;
        }
    }

    @Test
    public void testOldCodeCallsDeletedInterfaceMethod() {

        assert HotSwapTool.getCurrentVersion(CallDeletedInterfaceMethodTest.class) == 0;
        A a = new A();

        assertEquals(1, Helper.process(a));
        assert HotSwapTool.getCurrentVersion(CallDeletedInterfaceMethodTest.class) == 1;
        assertEquals(2, Helper.process(a));

        HotSwapTool.toVersion(CallDeletedInterfaceMethodTest.class, 0);

        assertEquals(1, Helper.process(a));
        assert HotSwapTool.getCurrentVersion(CallDeletedInterfaceMethodTest.class) == 1;
        assertEquals(2, Helper.process(a));

        HotSwapTool.toVersion(CallDeletedInterfaceMethodTest.class, 0);
    }
}
