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

/**
 * Tests that modify a large class hierarchy with the classes A, B, C, D, E, and F.
 * 
 * @author Thomas Wuerthinger
 */
public class LargeHierarchyTest {

    private A a = new A();
    private B b = new B();
    private C c = new C();
    private D d = new D();
    private E e = new E();
    private F f = new F();

    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(LargeHierarchyTest.class, 0);
    }

    // Version 0
    public static class A {

        public int value() {
            return 1;
        }
    }

    public static class B extends A {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    public static class C extends B {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    public static class D extends C {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    public static class E extends D {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    public static class F extends E {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    // Version 1
    public static class A___1 {

        public int value() {
            return 2;
        }
    }

    // Version 2
    //     D - E - F
    //   /
    // A - B - C
    public static class D___2 extends A {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    // Version 3
    //     D
    //   /
    // A - B - C - E - F
    public static class D___3 extends A {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    public static class E___3 extends A {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    // Version 4
    // Completely flat
    public static class C___4 extends A {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    public static class D___4 extends A {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    public static class E___4 extends A {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    public static class F___4 extends A {

        @Override
        public int value() {
            return super.value() * 2;
        }
    }

    // Version 5
    public static class F___5 extends E {

        @Override
        public int value() {
            return 0;
        }
    }

    @Test
    public void testChangeBaseClass() {

        assert HotSwapTool.getCurrentVersion(LargeHierarchyTest.class) == 0;

        assertEquals(1, a.value());
        assertEquals(2, b.value());
        assertEquals(4, c.value());
        assertEquals(8, d.value());
        assertEquals(16, e.value());
        assertEquals(32, f.value());

        HotSwapTool.toVersion(LargeHierarchyTest.class, 1);

        assertEquals(2, a.value());
        assertEquals(4, b.value());
        assertEquals(8, c.value());
        assertEquals(16, d.value());
        assertEquals(32, e.value());
        assertEquals(64, f.value());

        HotSwapTool.toVersion(LargeHierarchyTest.class, 0);

        assertEquals(1, a.value());
        assertEquals(2, b.value());
        assertEquals(4, c.value());
        assertEquals(8, d.value());
        assertEquals(16, e.value());
        assertEquals(32, f.value());
    }

    @Test
    public void testChangeSubClass() {
        assert HotSwapTool.getCurrentVersion(LargeHierarchyTest.class) == 0;

        assertEquals(1, a.value());
        assertEquals(2, b.value());
        assertEquals(4, c.value());
        assertEquals(8, d.value());
        assertEquals(16, e.value());
        assertEquals(32, f.value());

        HotSwapTool.toVersion(LargeHierarchyTest.class, 5);

        assertEquals(1, a.value());
        assertEquals(2, b.value());
        assertEquals(4, c.value());
        assertEquals(8, d.value());
        assertEquals(16, e.value());
        assertEquals(0, f.value());

        HotSwapTool.toVersion(LargeHierarchyTest.class, 0);

        assertEquals(1, a.value());
        assertEquals(2, b.value());
        assertEquals(4, c.value());
        assertEquals(8, d.value());
        assertEquals(16, e.value());
        assertEquals(32, f.value());
    }

    @Test
    public void testChangeHierarchy() {

        assert HotSwapTool.getCurrentVersion(LargeHierarchyTest.class) == 0;

        assertEquals(1, a.value());
        assertEquals(2, b.value());
        assertEquals(4, c.value());
        assertEquals(8, d.value());
        assertEquals(16, e.value());
        assertEquals(32, f.value());

        HotSwapTool.toVersion(LargeHierarchyTest.class, 2);

        assertEquals(1, a.value());
        assertEquals(2, b.value());
        assertEquals(4, c.value());
        assertEquals(2, d.value());
        assertEquals(4, e.value());
        assertEquals(8, f.value());

        HotSwapTool.toVersion(LargeHierarchyTest.class, 3);

        assertEquals(1, a.value());
        assertEquals(2, b.value());
        assertEquals(4, c.value());
        assertEquals(2, d.value());
        assertEquals(2, e.value());
        assertEquals(4, f.value());

        HotSwapTool.toVersion(LargeHierarchyTest.class, 4);

        assertEquals(1, a.value());
        assertEquals(2, b.value());
        assertEquals(2, c.value());
        assertEquals(2, d.value());
        assertEquals(2, e.value());
        assertEquals(2, f.value());

        HotSwapTool.toVersion(LargeHierarchyTest.class, 0);

        assertEquals(1, a.value());
        assertEquals(2, b.value());
        assertEquals(4, c.value());
        assertEquals(8, d.value());
        assertEquals(16, e.value());
        assertEquals(32, f.value());
    }
}
