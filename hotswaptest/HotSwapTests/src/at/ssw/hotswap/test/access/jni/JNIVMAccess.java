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

import at.ssw.hotswap.test.access.ClassAccess;
import at.ssw.hotswap.test.access.StackFrameAccess;
import at.ssw.hotswap.test.access.VMAccess;
import java.io.File;
import java.util.List;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class JNIVMAccess implements VMAccess {

    @Override
    public boolean canGetFrames() {
        return false;
    }

    @Override
    public List<StackFrameAccess> getFrames(String threadName) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<String> getThreadNames() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public static native Class findClassNative(String clazz) throws ClassNotFoundException, NoClassDefFoundError;

    @Override
    public ClassAccess findClass(String clazz) {
        try {
            return new JNIClassAccess(findClassNative(clazz.replace('.', '/')));
        } catch (ClassNotFoundException ex) {
            return null;
        } catch (NoClassDefFoundError ex) {
            return null;
        }
    }
}