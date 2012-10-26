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
package at.ssw.hotswap.test.accesstests;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import at.ssw.hotswap.HotSwapTool;
import at.ssw.hotswap.test.access.ClassAccess;
import at.ssw.hotswap.test.access.MethodAccess;
import at.ssw.hotswap.test.access.StackFrameAccess;
import at.ssw.hotswap.test.access.VMAccess;
import at.ssw.hotswap.test.access.jdi.JDIVMAccess;
import at.ssw.hotswap.test.access.jni.JNIVMAccess;
import at.ssw.hotswap.test.access.reflection.ReflectionVMAccess;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
@RunWith(Parameterized.class)
public class AccessTest {

    private static VMAccess vma;

    public AccessTest(VMAccess vma) {
        this.vma = vma;
    }

    // Version 0
    public static class A {

        //needed for StackTraceTest
        public static String stackTraceHelper() {
            return getParentMethodSignature();
        }

        //needed for FindMethodTest
        public static String method_Version0() {
            return "Version0";
        }

        //needed for SignatureTest
        public static String signatureHelper(String y, int x) {
            return "Version0";
        }

        //needed for invokeMethodIntTest()
        private int testIntRetValue() {
            return 2;
        }

        //needed for invokeMethodObjectTest
        private Object testObjectRetValue() {
            return new Integer(2);
        }

        //needed for invokeMethodVoidTest
        private static void testVoidRetValue() {
            System.out.println("Version 0");
        }

        //needed for invokeMethodParameterTest()
        private static int testMethodParameter(Integer x) {
            return x;
        }
    }

    // Version 1
    public static class A___1 {

        //needed for StackTraceTest
        protected static String stackTraceHelper() {
            return getParentMethodSignature();
        }

        //needed for FindMethodTest, SignatureTest
        public static String method_Version1() {
            return "Version1";
        }

        //needed for SignatureTest
        private static String signatureHelper(int y, int x) {
            return "Version1";
        }

        //needed for invokeMethodIntTest
        private int testIntRetValue() {
            return 3;
        }

        //needed for invokeMethodObjectTest
        private Object testObjectRetValue() {
            return new Integer(3);
        }

        //needed for invokeMethodVoidTest
        private static void testVoidRetValue() {
            System.out.println("Version 1");
        }

        //needed for invokeMethodParameterTest()
        private static int testMethodParameter(Integer x) {
            return x + 1;
        }
    }

    //needed for StackTraceTest
    private static String getParentMethodSignature() {

        try {
            int i = 0;
            for (StackFrameAccess stack : vma.getFrames("main")) {
                if (stack.getMethod().getName().equals("access$000")) {
                    break;
                } else {
                    i++;
                }
            }
            return vma.getFrames("main").get(++i).getMethod().getSignature();
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    @Parameters
    public static Collection accessValues() {
        return Arrays.asList(new Object[][]{{new JDIVMAccess()}, {new JNIVMAccess()}, {new ReflectionVMAccess()}});
        //return Arrays.asList(new Object[][]{{new JDIVMAccess()}});
    }

    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(AccessTest.class, 0);
    }

    @Test
    public void FindClassTest() {


        ClassAccess classAccess = vma.findClass("at.ssw.hotswap.test.accesstests.AccessTest$XYZ");
        assertNull(classAccess);

        classAccess = vma.findClass("at.ssw.hotswap.test.accesstests.AccessTest$A");
        assertNotNull(classAccess);

        HotSwapTool.toVersion(AccessTest.class, 1);

        classAccess = vma.findClass("at.ssw.hotswap.test.accesstests.AccessTest$A");
        assertNotNull(classAccess);
    }

    @Test
    public void FindMethodTest() {

        ClassAccess classAccess = vma.findClass("at.ssw.hotswap.test.accesstests.AccessTest$A");
        assertNotNull(classAccess);

        assertNotNull(classAccess.findMethod("method_Version0"));
        assertNull(classAccess.findMethod("method_Version1"));

        HotSwapTool.toVersion(AccessTest.class, 1);

        assertNull(classAccess.findMethod("method_Version0"));
        assertNotNull(classAccess.findMethod("method_Version1"));
    }

    @Test
    public void StackTraceTest() {
        if (vma.canGetFrames()) {
            assertEquals("public static java.lang.String stackTraceHelper()", A.stackTraceHelper());

            HotSwapTool.toVersion(AccessTest.class, 1);

            assertEquals("protected static java.lang.String stackTraceHelper()", A.stackTraceHelper());
        }
    }

    @Test
    public void SignatureTest() {
        MethodAccess mAccess = vma.findClass("at.ssw.hotswap.test.accesstests.AccessTest$A").findMethod("signatureHelper");
        assertEquals("public static java.lang.String signatureHelper(java.lang.String, int)", mAccess.getSignature());

        HotSwapTool.toVersion(AccessTest.class, 1);

        mAccess = vma.findClass("at.ssw.hotswap.test.accesstests.AccessTest$A").findMethod("signatureHelper");
        assertEquals("private static java.lang.String signatureHelper(int, int)", mAccess.getSignature());
    }

    @Test
    public void invokeMethodIntTest() {
        if (vma.getClass().getName().equals("at.ssw.hotswap.test.access.jdi.JDIVMAccess")) {
            return;
        }

        ClassAccess classAccess = vma.findClass("at.ssw.hotswap.test.accesstests.AccessTest$A");
        assertEquals(2, classAccess.findMethod("testIntRetValue").invoke(new Object[0], new A()));

        HotSwapTool.toVersion(AccessTest.class, 1);

        assertEquals(3, classAccess.findMethod("testIntRetValue").invoke(new Object[0], new A()));
    }

    @Test
    public void invokeMethodObjectTest() {
        if (vma.getClass().getName().equals("at.ssw.hotswap.test.access.jdi.JDIVMAccess")) {
            return;
        }

        ClassAccess classAccess = vma.findClass("at.ssw.hotswap.test.accesstests.AccessTest$A");
        assertEquals(2, classAccess.findMethod("testObjectRetValue").invoke(new Object[0], new A()));

        HotSwapTool.toVersion(AccessTest.class, 1);

        assertEquals(3, classAccess.findMethod("testObjectRetValue").invoke(new Object[0], new A()));
    }

    @Test
    public void invokeMethodVoidTest() {
        if (vma.getClass().getName().equals("at.ssw.hotswap.test.access.jdi.JDIVMAccess")) {
            return;
        }

        ClassAccess classAccess = vma.findClass("at.ssw.hotswap.test.accesstests.AccessTest$A");
        assertNull(classAccess.findMethod("testVoidRetValue").invoke(new Object[0], new A()));

        HotSwapTool.toVersion(AccessTest.class, 1);

        assertNull(classAccess.findMethod("testVoidRetValue").invoke(new Object[0], new A()));
    }

    @Test
    public void invokeMethodParameterTest() {
        if (vma.getClass().getName().equals("at.ssw.hotswap.test.access.jdi.JDIVMAccess")) {
            return;
        }

        ClassAccess classAccess = vma.findClass("at.ssw.hotswap.test.accesstests.AccessTest$A");
        assertEquals(6, classAccess.findMethod("testMethodParameter").invoke(new Object[]{6}, new A()));

        HotSwapTool.toVersion(AccessTest.class, 1);

        assertEquals(7, classAccess.findMethod("testMethodParameter").invoke(new Object[]{6}, new A()));

    }

    @Test
    public void getMethodsTest() {

        ClassAccess cAccess = vma.findClass("at.ssw.hotswap.test.accesstests.AccessTest$A");
        List<MethodAccess> list = cAccess.getMethods();

        assertEquals(7, list.size());

        Object[] mAccesses = list.toArray();

        Arrays.sort(mAccesses, new Comparator() {

            @Override
            public int compare(Object o1, Object o2) {
                return ((MethodAccess) o1).getName().compareTo(((MethodAccess) o2).getName());
            }
        });

        assertEquals("method_Version0", ((MethodAccess) mAccesses[0]).getName());
        assertEquals("signatureHelper", ((MethodAccess) mAccesses[1]).getName());
        assertEquals("stackTraceHelper", ((MethodAccess) mAccesses[2]).getName());
        assertEquals("testIntRetValue", ((MethodAccess) mAccesses[3]).getName());
        assertEquals("testMethodParameter", ((MethodAccess) mAccesses[4]).getName());
        assertEquals("testObjectRetValue", ((MethodAccess) mAccesses[5]).getName());
        assertEquals("testVoidRetValue", ((MethodAccess) mAccesses[6]).getName());

        HotSwapTool.toVersion(AccessTest.class, 1);

        list = cAccess.getMethods();

        assertEquals(7, list.size());

        mAccesses = list.toArray();
        Arrays.sort(mAccesses, new Comparator() {

            @Override
            public int compare(Object o1, Object o2) {
                return ((MethodAccess) o1).getName().compareTo(((MethodAccess) o2).getName());
            }
        });

        assertEquals("method_Version1", ((MethodAccess) mAccesses[0]).getName());
        assertEquals("signatureHelper", ((MethodAccess) mAccesses[1]).getName());
        assertEquals("stackTraceHelper", ((MethodAccess) mAccesses[2]).getName());
        assertEquals("testIntRetValue", ((MethodAccess) mAccesses[3]).getName());
        assertEquals("testMethodParameter", ((MethodAccess) mAccesses[4]).getName());
        assertEquals("testObjectRetValue", ((MethodAccess) mAccesses[5]).getName());
        assertEquals("testVoidRetValue", ((MethodAccess) mAccesses[6]).getName());

    }
}
