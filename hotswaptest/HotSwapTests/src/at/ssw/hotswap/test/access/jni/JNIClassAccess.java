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
package at.ssw.hotswap.test.access.jni;

import at.ssw.hotswap.test.access.ClassAccess;
import at.ssw.hotswap.test.access.MethodAccess;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class JNIClassAccess implements ClassAccess {

    Class clazz;

    public JNIClassAccess(Class clazz) {
        this.clazz = clazz;
    }

    @Override
    public String getName() {
        return clazz.getName();
    }

    public static native Method findMethodNative(Class clazz, String methodName);

    @Override
    public MethodAccess findMethod(String methodName) {
        Method m;
        try {
            m = findMethodNative(clazz, methodName);
        } catch (NoSuchMethodError ex) {
            return null;
        }
        return new JNIMethodAccess(m);
    }

    public static native Method[] getMethodsNative(Class clazz);

    @Override
    public List<MethodAccess> getMethods() {
        Method[] array = getMethodsNative(clazz);
        List<MethodAccess> mAccesses = new ArrayList<MethodAccess>();
        for (int i = 0; i < array.length; i++) {
            mAccesses.add(new JNIMethodAccess(array[i]));
        }
        return mAccesses;
    }
}
