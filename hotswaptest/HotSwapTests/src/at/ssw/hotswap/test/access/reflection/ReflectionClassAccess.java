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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class ReflectionClassAccess implements ClassAccess {

    private Class clazz;
   
    public ReflectionClassAccess() {
    }
    public ReflectionClassAccess(String name) throws ClassNotFoundException {
        clazz = Class.forName(name);
    }

    @Override
    public String getName() {
        return clazz.getName();
    }

    @Override
    public MethodAccess findMethod(String method) {
        try {
            return new ReflectionMethodAccess(method, clazz.getName());
        } catch (ClassNotFoundException ex) {
            return null;
        } catch (NoSuchMethodError ex) {
            return null;
        }
    }

    @Override
    public List<MethodAccess> getMethods() {
        List<MethodAccess> methodAccesses = new ArrayList<MethodAccess>();
        for (Method m : clazz.getDeclaredMethods()) {
            methodAccesses.add(new ReflectionMethodAccess(m, clazz));
        }
        return methodAccesses;
    }
}
