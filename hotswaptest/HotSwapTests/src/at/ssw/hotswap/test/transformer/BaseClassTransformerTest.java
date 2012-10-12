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

package at.ssw.hotswap.test.transformer;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import at.ssw.hotswap.HotSwapTool;

class BaseClass {
    public void $transformer() {
        transformerExecuted();
    }

    public void transformerExecuted() {
        
    }
}

/**
 * Tests for executing the transformer of a base class.
 *
 * @author Thomas Wuerthinger
 */
public class BaseClassTransformerTest {

    // Version 0
    public static class A {

        public int x = 2;
    }

    // Version 3
    public static class A___1 extends BaseClass {

        public int x;

        @Override
        public void transformerExecuted() {
            System.out.println("Transformer of A executing...");
            x = x * 2;
        }
    }


    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(BaseClassTransformerTest.class, 0);
    }

    @Test
    public void testSimpleTransformer() {

        assert HotSwapTool.getCurrentVersion(BaseClassTransformerTest.class) == 0;

        A a = new A();

        assertEquals(2, a.x);

        HotSwapTool.toVersion(BaseClassTransformerTest.class, 1);

        assertEquals(4, a.x);

        HotSwapTool.toVersion(BaseClassTransformerTest.class, 0);

        assertEquals(4, a.x);

        HotSwapTool.toVersion(BaseClassTransformerTest.class, 1);

        assertEquals(8, a.x);

        HotSwapTool.toVersion(BaseClassTransformerTest.class, 0);

        assertEquals(8, a.x);
    }
}
