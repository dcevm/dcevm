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
package at.ssw.hotswap.test.access.jdi;

import at.ssw.hotswap.test.access.ClassAccess;
import at.ssw.hotswap.test.access.MethodAccess;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class JDIClassAccess implements ClassAccess {

    private ReferenceType clazz;

    public JDIClassAccess(ReferenceType clazz) {
        this.clazz = clazz;
    }

    @Override
    public String getName() {
        return clazz.name();
    }

    @Override
    public MethodAccess findMethod(String method) {
        List<Method> methods = clazz.methodsByName(method);
        List<Method> newMethods = new ArrayList<Method>();
        for (Method m : methods) {
            if (!m.isObsolete()) {
                newMethods.add(m);
            }
        }
        if (newMethods.size() > 1) {
            throw new RuntimeException("ambiguous methods: " + newMethods.get(0) + ", " + newMethods.get(1));
        }
        if (newMethods.isEmpty()) {
            return null;
        }
        return new JDIMethodAccess(newMethods.get(0), clazz);
    }

    @Override
    public List<MethodAccess> getMethods() {
        List<MethodAccess> methodAccesses = new ArrayList<MethodAccess>();
        for (Method m : clazz.methods()) {
            if (!m.name().equals("<init>")) {
                methodAccesses.add(new JDIMethodAccess(m, clazz));
            }
        }
        return methodAccesses;
    }
}
