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

import at.ssw.hotswap.test.access.MethodAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.objectweb.asm.Type;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class JNIMethodAccess implements MethodAccess {

    Method method;

    public JNIMethodAccess(Method m) {
        method = m;
    }

    @Override
    public String getName() {
        return method.getName();
    }

    @Override
    public String getSignature() {
        String methodString = Modifier.toString(method.getModifiers()) + " " + method.getReturnType().getName() + " " + method.getName() + "(";
        boolean paramFound = false;
        for (Class c : method.getParameterTypes()) {
            if (paramFound) {
                methodString += ", ";
            }
            paramFound = true;
            methodString += c.getName();
        }
        return methodString + ")";
    }

    @Override
    public boolean canCheckObsoletness() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isObsolete() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public static native Object invokeMethodNative(Class clazz, Object obj, String methodName, String retValue, boolean staticValue, String descriptor, Object[] params);

    @Override
    public Object invoke(Object[] o, Object instance) {
        boolean staticValue = java.lang.reflect.Modifier.isStatic(method.getModifiers());
        String retValue = method.getReturnType().getName();
        String descriptor = new org.objectweb.asm.commons.Method(method.getName(), Type.getReturnType(method), Type.getArgumentTypes(method)).getDescriptor();

        return invokeMethodNative(method.getDeclaringClass(), instance, method.getName(), retValue, staticValue, descriptor, o);
    }
}
