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

import at.ssw.hotswap.JDIProxy;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Method;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Type;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class JDICallingMethodGetter implements CallingMethodGetter {

    @Override
    public String getCallingMethod() {

        final StringBuffer methodBuffer = new StringBuffer();
        final JDIProxy jdi = JDIProxy.getJDI();
        final Exception[] exception = new Exception[1];
        exception[0] = null;

        Runnable r = new Runnable() {

            public void run() {

                final List<ThreadReference> threads = jdi.getVM().allThreads();
                for (ThreadReference t : threads) {
                    if (t.name().equals("main")) {
                        StackFrame stackFrame = null;
                        boolean found = false;
                        try {
                            for (StackFrame s : t.frames()) {
                                if (found) {
                                    stackFrame = s;
                                    break;
                                }
                                if (s.toString().contains("JDICallingMethodGetter")) {
                                    found = true;
                                }
                            }
                        } catch (IncompatibleThreadStateException ex) {
                            exception[0] = ex;
                        }

                        Method method = stackFrame.location().method();
                        String methodString = Modifier.toString(method.modifiers()) + " " + method.name() + "(";
                        boolean paramFound = false;
                        try {
                            for (Type type : method.argumentTypes()) {
                                if (paramFound) {
                                    methodString += ", ";
                                }
                                paramFound = true;
                                methodString += type.name();
                            }
                        } catch (ClassNotLoadedException ex) {
                            exception[0] = ex;
                        }
                        methodString += ")";
                        methodBuffer.append(methodString);
                    }
                }
            }
        };

        jdi.executeSuspended(r);
        if (exception[0] != null) {
            throw new RuntimeException(exception[0]);
        }

        return methodBuffer.toString();
    }
}
