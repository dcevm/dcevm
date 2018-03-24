package com.github.dcevm.test.fields;

import com.github.dcevm.HotSwapTool;
import com.github.dcevm.test.TestUtil;
import com.github.dcevm.test.category.Light;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static org.junit.Assert.assertEquals;


@Category(Light.class)
public class FieldOperationsUpdateClassRedefinedCount {

    // Version 0
    public static class A {

        public int x;

        int getFieldInOldCode() {

            __toVersion__(1);

            // This field does no longer exist
            return x;
        }
        int getVer() {
            return 0;
        }
    }

    // Version 1
    public static class A___1 {
        public int x;
        public int y;
        int getVer() {
            return 1;
        }
    }

    public static class A___2 {
        int getVer() {
            return 2;
        }
    }

    @Before
    public void setUp() throws Exception {
        __toVersion__(0);
    }

    @Test
    public void addingFieldUpdatesClassRedifinedCount() throws NoSuchFieldException, IllegalAccessException {
        // setup
        A a = new A();
        __toVersion__(0);
        int prevVersion = TestUtil.getClassRedefinedCount(A.class);
        // examine
        __toVersion__(1);

        Object y = A.class.getDeclaredField("y").get(a);
        // verify
        assertEquals(0,y);
        assertEquals(1, a.getVer());
        assertEquals(prevVersion+1, TestUtil.getClassRedefinedCount(A.class));
    }

    @Test
    public void deletingFieldUpdatesClassRedifinedCount() {
        // setup
        A a= new A();
        __toVersion__(0);
        int prevVersion = TestUtil.getClassRedefinedCount(A.class);
        // examine
        __toVersion__(2);

        // verify
        assertEquals(2, a.getVer());
        assertEquals(prevVersion+1, TestUtil.getClassRedefinedCount(A.class));
    }



}
