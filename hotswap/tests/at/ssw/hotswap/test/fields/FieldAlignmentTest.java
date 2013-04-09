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

package at.ssw.hotswap.test.fields;

import static org.junit.Assert.*;

import at.ssw.hotswap.test.util.HotSwapTestHelper;
import org.junit.Before;
import org.junit.Test;

/**
 * Complex field test.
 *
 * @author Thomas Wuerthinger
 */
public class FieldAlignmentTest {

    // Version 0
    public static class A {
    }

    // Version 1
    public static class A___1 {
        public boolean booleanFld2;
        public String stringFld2 = "NEW";
    }

    @Before
    public void setUp() throws Exception {
        HotSwapTestHelper.__toVersion__(0);
    }

    @Test
    public void testFieldChange() {
        assertEquals(0, HotSwapTestHelper.__version__());
        A a = new A();
        HotSwapTestHelper.__toVersion__(1);
        System.gc();
        HotSwapTestHelper.__toVersion__(0);
    }
}
