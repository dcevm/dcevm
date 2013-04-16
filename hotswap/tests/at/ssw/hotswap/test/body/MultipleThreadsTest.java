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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.List;

import static at.ssw.hotswap.test.util.HotSwapTestHelper.__toVersion__;
import static at.ssw.hotswap.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.*;

/**
 * Class for testing redefining methods of classes that extend the Thread class. In the test setup the run method
 * calls the doit method in a loop until this methods returns false.
 *
 * @author Thomas Wuerthinger
 */
@RunWith(Parameterized.class)
public class MultipleThreadsTest {

    @Before
    public void setUp() throws Exception {
        __toVersion__(0);
    }

    @Parameters
    public static List<Object[]> parameters() {
        return Arrays.asList(new Object[][]{{1}, {50}});
    }

    private int count;
    public MultipleThreadsTest(int count) {
        this.count = count;
    }

    // Version 0
    public static class A extends Thread {

        private int value;
        private int value2;
        private boolean flag = false;

        @Override
        public void run() {
            while (doit()) {
                flag = false;
            }
        }

        public boolean doit() {
            if (flag) {
                throw new IllegalStateException("Must not reach here");
            }
            flag = true;
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }

            value++;
            return true;
        }

        public int getValue() {
            return value;
        }

        public int getValue2() {
            return value2;
        }
    }

    // Version 1
    public static class A___1 extends Thread {

        private int value;
        private int value2;
        private boolean flag = false;

        @Override
        public void run() {
            while (doit()) {
                flag = false;
            }
        }

        public boolean doit() {
            if (flag) {
                throw new RuntimeException("Must not reach here");
            }
            flag = true;
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }

            value2++;
            return true;
        }

        public int getValue() {
            return value;
        }

        public int getValue2() {
            return value2;
        }
    }

    // Version 2
    public static class A___2 extends Thread {

        private int value;
        private int value2;
        private boolean flag = false;

        @Override
        public void run() {
            while (doit()) {
                flag = false;
            }
        }

        public boolean doit() {
            return false;
        }

        public int getValue() {
            return value;
        }

        public int getValue2() {
            return value2;
        }
    }

    @Test
    public void testThreads() throws Exception {
        assertEquals(0, __version__());

        A[] arr = new A[count];
        for (int i = 0; i < count; i++) {
            arr[i] = new A();
            arr[i].start();
        }

        __toVersion__(0);
        Thread.sleep(500);
        for (int i = 0; i < count; i++) {
            //assertTrue(arr[i].getValue() > 0);
            assertEquals(0, arr[i].getValue2());
        }

        __toVersion__(1);
        Thread.sleep(500);
        for (int i = 0; i < count; i++) {
            assertTrue(arr[i].getValue2() > 0);
        }

        __toVersion__(2);
        Thread.sleep(500);
        for (int i = 0; i < count; i++) {
            assertFalse(arr[i].isAlive());
        }
    }
}
