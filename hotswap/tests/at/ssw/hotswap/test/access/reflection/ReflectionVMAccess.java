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
import at.ssw.hotswap.test.access.VMAccess;
import at.ssw.hotswap.test.access.StackFrameAccess;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class ReflectionVMAccess implements VMAccess {

    private List<Thread> getThreads() {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }

        Thread[] threads;
        int cnt;
        int estsize = root.activeCount();

        do {
            estsize *= 2;
            threads = new Thread[estsize];

            cnt = root.enumerate(threads, true);
        } while (cnt == estsize);

        List<Thread> ret = new ArrayList<Thread>();
        for (int i = 0; i < cnt; i++) {
            ret.add(threads[i]);
        }
        return ret;
    }

    @Override
    public List<String> getThreadNames() {
        List<String> threadNames = new ArrayList<String>();
        List<Thread> threads = getThreads();
        for (Thread t : threads) {
            threadNames.add(t.getName());
        }
        return threadNames;
    }

    @Override
    public boolean canGetFrames() {
        return true;
    }

    @Override
    public List<StackFrameAccess> getFrames(String threadName) {

        List<Thread> threads = getThreads();
        List<StackFrameAccess> stackAccesses = new ArrayList<StackFrameAccess>();
        for (Thread t : threads) {
            if (t.getName().equals(threadName)) {
                for (StackTraceElement stackElement : t.getStackTrace()) {
                    StackFrameAccess stackAccess = new ReflectionStackFrameAccess(stackElement);
                    stackAccesses.add(stackAccess);
                }
            }
        }
        return stackAccesses;
    }

    @Override
    public ClassAccess findClass(String clazz) {
        try {
            return new ReflectionClassAccess(clazz);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }
}
