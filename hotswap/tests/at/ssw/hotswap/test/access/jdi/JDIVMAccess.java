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
import at.ssw.hotswap.test.access.ClassAccess;
import at.ssw.hotswap.test.access.StackFrameAccess;
import at.ssw.hotswap.test.access.VMAccess;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class JDIVMAccess implements VMAccess {

    private List<ThreadReference> getThreads() {
        final JDIProxy jdi = JDIProxy.getJDI();
        return jdi.getVM().allThreads();
    }

    @Override
    public boolean canGetFrames() {
        return true;
    }

    @Override
    public List<StackFrameAccess> getFrames(final String threadName) {
        final JDIProxy jdi = JDIProxy.getJDI();
        final Exception[] exception = new Exception[1];
        final Object[] object = new Object[1];
        object[0] = null;
        exception[0] = null;

        Runnable r = new Runnable() {

            public void run() {
                List<StackFrameAccess> stackAccesses = new ArrayList<StackFrameAccess>();
                for (ThreadReference t : getThreads()) {
                    if (t.name().equals(threadName)) {
                        try {
                            for (StackFrame stack : t.frames()) {
                                StackFrameAccess stackAccess = new JDIStackFrameAccess(stack);
                                stackAccesses.add(stackAccess);
                            }
                        } catch (IncompatibleThreadStateException ex) {
                            exception[0] = ex;
                        }
                        object[0] = stackAccesses;
                        return;
                    }
                }
            }
        };

        jdi.executeSuspended(r);

        if (exception[0] != null) {
            throw new RuntimeException(exception[0]);
        }
        return (List<StackFrameAccess>) object[0];
    }

    @Override
    public List<String> getThreadNames() {
        List<String> threadNames = new ArrayList<String>();

        for (ThreadReference t : getThreads()) {
            threadNames.add(t.name());
        }
        return threadNames;
    }

    @Override
    public ClassAccess findClass(String clazz) {
        final JDIProxy jdi = JDIProxy.getJDI();
        ReferenceType found = null;
        // ensures, that all classes are retrieved
        jdi.refreshAllClasses();
        for (ReferenceType referenceType : jdi.getVM().allClasses()) {
            if (referenceType.name().equals(clazz)) {
                if (found != null) {
                    throw new RuntimeException("ambiguous class name");
                }
                found = referenceType;
            }
        }
        if (found == null) {
            return null;
        }
        return new JDIClassAccess(found);
    }
}
