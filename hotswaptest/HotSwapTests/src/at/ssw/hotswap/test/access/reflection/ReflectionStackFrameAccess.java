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

import at.ssw.hotswap.test.access.ClassAccess;
import at.ssw.hotswap.test.access.MethodAccess;
import at.ssw.hotswap.test.access.StackFrameAccess;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class ReflectionStackFrameAccess implements StackFrameAccess {

    private String methodName;
    private String className;
    private MethodAccess methodAccess;
    private ClassAccess classAccess;

    ReflectionStackFrameAccess(StackTraceElement element) {
        methodName = element.getMethodName();
        className = element.getClassName();
    }

    @Override
    public MethodAccess getMethod() throws ClassNotFoundException {
        if (methodAccess == null) {
            methodAccess = new ReflectionMethodAccess(methodName, className);
        }
        return methodAccess;
    }

    @Override
    public ClassAccess getClazz() {
        if (classAccess == null) {
            try {
                classAccess = new ReflectionClassAccess(className);
            } catch (ClassNotFoundException ex) {
                return null;
            }
        }
        return classAccess;
    }

    @Override
    public String toString() {
        return methodName;
    }
}
