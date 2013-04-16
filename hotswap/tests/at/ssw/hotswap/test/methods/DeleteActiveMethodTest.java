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

import at.ssw.hotswap.MethodRedefinitionPolicy;
import static org.junit.Assert.assertEquals;
import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import at.ssw.hotswap.HotSwapTool;
import at.ssw.hotswap.RedefinitionPolicy;
import at.ssw.hotswap.test.TestUtil;

/**
 * Test cases that delete a method that is currently active on the stack.
 *
 * @author Thomas Wuerthinger
 */
public class DeleteActiveMethodTest {

    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(DeleteActiveMethodTest.class, 0);
    }

    // Version 0
    public static class A {

        boolean firstCall;

        public int value() {
            firstCall = true;
            return helperValue();
        }

        public int helperValue() {

            if (!firstCall) {
                return -1;
            }
            firstCall = false;

            Thread t = new Thread(new Runnable() {

                @Override
                public void run() {
                    HotSwapTool.toVersion(DeleteActiveMethodTest.class, 1);
                }
            });
            t.start();

            try {
                while (t.isAlive()) {
                    try {
                        this.helperValue();
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                }
                Assert.fail("Exception expected!");
            } catch (NoSuchMethodError e) {
            }

            try {
                t.join();
            } catch (InterruptedException e) {
            }

            return 1;
        }
    }

    public static class B {

        public int fac(int x) {
            if (x == 0) {
                HotSwapTool.toVersion(DeleteActiveMethodTest.class, 1);
            }

            return x * fac(x - 1);
        }
    }

    // Version 1
    @MethodRedefinitionPolicy(RedefinitionPolicy.DynamicCheck)
    public static class A___1 {

        boolean firstCall;

        public int value() {
            HotSwapTool.toVersion(DeleteActiveMethodTest.class, 0);
            return 2;
        }
    }

    @MethodRedefinitionPolicy(RedefinitionPolicy.DynamicCheck)
    public static class B___1 {
    }

    @Test
    public void testDeleteActiveMethodSimple() {
        assert HotSwapTool.getCurrentVersion(DeleteActiveMethodTest.class) == 0;

        final B b = new B();
        TestUtil.assertException(NoSuchMethodError.class, new Runnable() {
            @Override
            public void run() {
                b.fac(5);
            }
        });
       
        assert HotSwapTool.getCurrentVersion(DeleteActiveMethodTest.class) == 1;
        
        HotSwapTool.toVersion(DeleteActiveMethodTest.class, 0);
        assert HotSwapTool.getCurrentVersion(DeleteActiveMethodTest.class) == 0;
    }

    @Test
    public void testDeleteActiveMethod() {
        assert HotSwapTool.getCurrentVersion(DeleteActiveMethodTest.class) == 0;

        A a = new A();

        assertEquals(1, a.value());
        assert HotSwapTool.getCurrentVersion(DeleteActiveMethodTest.class) == 1;

        assertEquals(2, a.value());
        assert HotSwapTool.getCurrentVersion(DeleteActiveMethodTest.class) == 0;

        assertEquals(1, a.value());
        assert HotSwapTool.getCurrentVersion(DeleteActiveMethodTest.class) == 1;

        assertEquals(2, a.value());
        assert HotSwapTool.getCurrentVersion(DeleteActiveMethodTest.class) == 0;

        assertEquals(1, a.value());
        assert HotSwapTool.getCurrentVersion(DeleteActiveMethodTest.class) == 1;

        assertEquals(2, a.value());
        assert HotSwapTool.getCurrentVersion(DeleteActiveMethodTest.class) == 0;
    }
}
