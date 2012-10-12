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

import at.ssw.hotswap.JDIProxy;
import at.ssw.hotswap.test.access.MethodAccess;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.InvocationException;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Type;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class JDIMethodAccess implements MethodAccess {

    private Method method;

    public JDIMethodAccess(Method m, ReferenceType clazz) {
        method = m;
    }

    @Override
    public String toString() {
        return method.toString();
    }

    @Override
    public String getName() {
        return method.name();
    }

    @Override
    public String getSignature() {
        String methodString = Modifier.toString(method.modifiers()) + " " + method.returnTypeName() + " " + method.name() + "(";
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
            throw new RuntimeException(ex);
        }
        return methodString + ")";
    }

    @Override
    public boolean canCheckObsoletness() {
        return true;
    }

    @Override
    public boolean isObsolete() {
        return method.isObsolete();
    }

    @Override
    public Object invoke(Object[] o, Object instance) {
        Thread tempthread = new Thread("invokeThread") {

            @Override
            public void run() {
                try {
                    sleep(2000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(JDIMethodAccess.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        tempthread.start();

        // get Main-Thread
        final JDIProxy jdi = JDIProxy.getJDI();
        ThreadReference reference = null;
        for (ThreadReference t : jdi.getVM().allThreads()) {
            if (t.name().equals("invokeThread")) {
                reference = t;
                break;
            }
        }
        //  reference.suspend();

        Object obj = null;
        try {
            obj = ((ClassType) method.declaringType()).invokeMethod(reference, method, new ArrayList(), ClassType.INVOKE_SINGLE_THREADED);
        } catch (InvalidTypeException ex) {
            Logger.getLogger(JDIMethodAccess.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotLoadedException ex) {
            Logger.getLogger(JDIMethodAccess.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IncompatibleThreadStateException ex) {
            Logger.getLogger(JDIMethodAccess.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationException ex) {
            Logger.getLogger(JDIMethodAccess.class.getName()).log(Level.SEVERE, null, ex);
        }
        // reference.resume();
        return obj;
    }
}
