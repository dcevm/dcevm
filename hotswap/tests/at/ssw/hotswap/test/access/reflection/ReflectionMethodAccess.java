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
package at.ssw.hotswap.test.access.reflection;

import at.ssw.hotswap.test.access.MethodAccess;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class ReflectionMethodAccess implements MethodAccess {

    private Method method;

    public ReflectionMethodAccess(String methodName, String className) throws ClassNotFoundException, NoSuchMethodError {

        Class clazz = Class.forName(className);

        Method found = null;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                if (found != null) {
                    throw new RuntimeException("ambiguous method name");
                }
                found = m;
            }
        }
        if (found == null) {
            throw new NoSuchMethodError(methodName);
        }
        this.method = found;
    }

    public ReflectionMethodAccess(Method m, Class clazz) {
        this.method = m;
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
    public String getName() {
        return method.getName();
    }

    @Override
    public boolean canCheckObsoletness() {
        return false;
    }

    @Override
    public boolean isObsolete() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Object invoke(Object[] o, Object instance) {
        try {
            boolean acc = method.isAccessible();
            method.setAccessible(true);
            Object obj = method.invoke(instance, o);
            method.setAccessible(acc);
            return obj;
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }
}
