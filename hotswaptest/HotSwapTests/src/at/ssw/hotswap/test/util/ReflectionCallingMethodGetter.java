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
package at.ssw.hotswap.test.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class ReflectionCallingMethodGetter implements CallingMethodGetter {

    @Override
    public String getCallingMethod() {
        StackTraceElement stack = Thread.currentThread().getStackTrace()[2];
        Class clazz = null;

        try {
            clazz = Class.forName(stack.getClassName());
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }

        Method found = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(stack.getMethodName())) {
                if (found != null) {
                    throw new RuntimeException("ambiguous method name");
                }
                found = method;
            }
        }

        String methodString = Modifier.toString(found.getModifiers()) + " " + found.getName() + "(";
        boolean paramFound = false;
        for (Class c : found.getParameterTypes()) {
            if (paramFound) {
                methodString += ", ";
            }
            paramFound = true;
            methodString += c.getName();
        }
        methodString += ")";

        return methodString;
    }
}
