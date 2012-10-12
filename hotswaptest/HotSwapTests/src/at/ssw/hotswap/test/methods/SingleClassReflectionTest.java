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

import org.junit.Before;
import org.junit.Test;

import at.ssw.hotswap.HotSwapTool;

/**
 * Testing correct behaviour of the class object after class redefinition (with respect to the identity hash code and synchronization).
 *
 * @author Thomas Wuerthinger
 */
public class SingleClassReflectionTest {

    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(SingleClassReflectionTest.class, 0);
    }

    // Version 0
    public static class A {

        public static synchronized void staticSynchronized() {
            HotSwapTool.toVersion(SingleClassReflectionTest.class, 1);
        }
    }


    // Version 1
    public static class A___1 {
        public static synchronized void staticSynchronized() {
        }
    }


    @Test
    public void testHashcode() {

        assert HotSwapTool.getCurrentVersion(SingleClassReflectionTest.class) == 0;

        A a = new A();
        
        int hashcode = a.getClass().hashCode();

        HotSwapTool.toVersion(SingleClassReflectionTest.class, 1);

        assertEquals(hashcode, a.getClass().hashCode());

        HotSwapTool.toVersion(SingleClassReflectionTest.class, 0);
    }

    @Test
    public void testStaticSynchronized() {

        A.staticSynchronized();

        assertEquals(1, HotSwapTool.getCurrentVersion(SingleClassReflectionTest.class));

        HotSwapTool.toVersion(SingleClassReflectionTest.class, 0);
    }

}
