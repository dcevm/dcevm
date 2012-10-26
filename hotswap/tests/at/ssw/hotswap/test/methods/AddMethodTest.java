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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import at.ssw.hotswap.HotSwapTool;
import at.ssw.hotswap.test.TestUtil;

/**
 * Tests for adding / removing methods in a single class.
 *
 * @author Thomas Wuerthinger
 */
public class AddMethodTest {

    // Version 0
    public static class A {
        public int value(int newVersion) {
            return newVersion;
        }
    }

    // Version 1
    public static class A___1 {

        public int value(int newVersion) {

            int x = 1;
            try {
                x = 2;
            } catch (NumberFormatException e) {
                x = 3;
            } catch (Exception e) {
                x = 4;
            } finally {
                x = x * 2;
            }
            HotSwapTool.toVersion(AddMethodTest.class, newVersion);
            throw new IllegalArgumentException();
        }
    }

    // Version 2
    public static class A___2 {

        public int value2() {
            return 2;
        }

        public int value(int newVersion) {

            int x = 1;
            try {
                x = 2;
            } catch (NumberFormatException e) {
                x = 3;
            } catch (Exception e) {
                x = 4;
            } finally {
                x = x * 2;
            }
            HotSwapTool.toVersion(AddMethodTest.class, newVersion);
            throw new IllegalArgumentException();
        }

        public int value3() {
            return 3;
        }

        public int value4() {
            return 4;
        }

        public int value5() {
            return 5;
        }
    }

    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(AddMethodTest.class, 0);
    }

    @Test
    public void testAddMethodToKlassWithEMCPExceptionMethod() {

        assert HotSwapTool.getCurrentVersion(AddMethodTest.class) == 0;

        final A a = new A();

        assertEquals(1, a.value(1));

        HotSwapTool.toVersion(AddMethodTest.class, 1);

        int firstLineNumber = TestUtil.assertException(IllegalArgumentException.class, new Runnable() {
            @Override
            public void run() {
                assertEquals(4, a.value(1));
            }
        });

        int secondLineNumber = TestUtil.assertException(IllegalArgumentException.class, new Runnable() {
            @Override
            public void run() {
                assertEquals(4, a.value(2));
            }
        });

        System.out.println("Exception line numbers: " + firstLineNumber + " and " + secondLineNumber);

        assertTrue("Must have different line numbers (A.value is an EMCP method and therefore execution has to be transferred)", firstLineNumber != secondLineNumber);

        assert HotSwapTool.getCurrentVersion(AddMethodTest.class) == 2;

        int newFirstLineNumber = TestUtil.assertException(IllegalArgumentException.class, new Runnable() {
            @Override
            public void run() {
                assertEquals(4, a.value(2));
            }
        });

        assertEquals(secondLineNumber, newFirstLineNumber);

        int newSecondLineNumber = TestUtil.assertException(IllegalArgumentException.class, new Runnable() {
            @Override
            public void run() {
                assertEquals(4, a.value(1));
            }
        });

        assertEquals(newSecondLineNumber, firstLineNumber);

        assertTrue("Must have different line numbers (A.value is an EMCP method and therefore execution has to be transferred)", firstLineNumber != secondLineNumber);

        HotSwapTool.toVersion(AddMethodTest.class, 0);
        
        assertEquals(1, a.value(1));

        assert HotSwapTool.getCurrentVersion(AddMethodTest.class) == 0;


    }
}
