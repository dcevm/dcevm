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
package at.ssw.hotswap.installer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class WindowsInstaller extends Installer {

    public String getClientDirectory() {
        return "bin\\client";
    }

    public String getServerDirectory(boolean is64bit) {
        return "bin\\server";
    }

    public String getJavaExecutable() {
        return "bin\\java.exe";
    }

    public String getLibraryName() {
        return "jvm.dll";
    }

    public List<Installation> listAllInstallations() {
        List<Installation> installations = new ArrayList<Installation>();
        String[] searchForJavaString = new String[]{
            System.getenv("JAVA_HOME") + "\\..",
            System.getenv("PROGRAMW6432") + "\\JAVA",
            System.getenv("PROGRAMFILES") + "\\JAVA",
            System.getenv("SYSTEMDRIVE") + "\\JAVA"};

        for (String fileString : searchForJavaString) {
            File javaDir = new File(fileString);

            if (javaDir.exists() && javaDir.isDirectory()) {
                for (File f : javaDir.listFiles()) {
                    if (f.getName().startsWith("jdk") || f.getName().startsWith("jre")) {
                        try {
                            Installation i = new Installation(f, this);
                            if (!installations.contains(i)) {
                                installations.add(i);
                            }
                        } catch (InstallerException ex) {
                        }
                    }
                }
            }
        }
        return installations;
    }

    @Override
    public boolean isJDK(File directory) {
        if (directory.isDirectory() && directory.getName().startsWith("jdk")) {
            File jreDir = new File(directory, "jre");
            return isJRE(jreDir);
        }
        return false;
    }
}
