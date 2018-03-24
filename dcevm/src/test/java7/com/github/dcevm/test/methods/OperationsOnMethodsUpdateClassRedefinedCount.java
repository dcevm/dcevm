package com.github.dcevm.test.methods;

import com.github.dcevm.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static org.junit.Assert.assertEquals;

public class OperationsOnMethodsUpdateClassRedefinedCount {
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
            __toVersion__(newVersion);
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
            __toVersion__(newVersion);
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
        __toVersion__(0);
    }

    @Test
    public void changingMethodUpdatesClassRedefinedCount() {
        // setup
        __toVersion__(0);
        int prevVersion = TestUtil.getClassRedefinedCount(A.class);
        // examine
        __toVersion__(1);
        // verify
        assertEquals(prevVersion+1, TestUtil.getClassRedefinedCount(A.class));
    }

    @Test
    public void addingMethodUpdatesClassRedefinedCount() {
        // setup
        __toVersion__(0);
        int prevVersion = TestUtil.getClassRedefinedCount(A.class);
        // examine
        __toVersion__(2);
        // verify
        assertEquals(prevVersion+1, TestUtil.getClassRedefinedCount(A.class));
    }

    @Test
    public void deletingMethodUpdatesClassRedefinedCount() {
        // setup
        __toVersion__(2);
        int prevVersion = TestUtil.getClassRedefinedCount(A.class);
        // examine
        __toVersion__(0);
        // verify
        assertEquals(prevVersion+1, TestUtil.getClassRedefinedCount(A.class));
    }
}
