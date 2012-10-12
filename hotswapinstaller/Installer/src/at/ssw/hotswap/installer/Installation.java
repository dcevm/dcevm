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
import java.io.IOException;
import java.util.Observable;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class Installation extends Observable {

    private final File file;
    private final boolean isJDK;
    private final Installer installer;
    private boolean installed;
    private String version;
    private String dceVersion;
    private boolean is64Bit;

    public Installation(File f, Installer i) throws InstallerException {
        installer = i;
        try {
            file = f.getCanonicalFile();
        } catch (IOException ex) {
            throw new InstallerException(f.getAbsolutePath() + " is no JRE or JDK-directory.");
        }
        isJDK = installer.isJDK(file);
        if (!isJDK && !installer.isJRE(file)) {
            throw new InstallerException(f.getAbsolutePath() + " is no JRE or JDK-directory.");
        }

        version = installer.getJavaVersion(file);
        update();
    }

    final public void update() throws InstallerException {
        installed = installer.isDCEInstalled(file);
        if (installed) {
            dceVersion = installer.getDCEVersion(file);
        }
        is64Bit = installer.is64Bit(file);
    }

    public File getPath() {
        return file;
    }

    public String getVersion() {
        return version;
    }

    public String getDCEVersion() {
        return dceVersion;
    }

    public boolean isJDK() {
        return isJDK;
    }

    public boolean is64Bit() {
        return is64Bit;
    }

    public void installDCE() throws InstallerException {
        try {
            installer.install(file, is64Bit);
        } catch (InstallerException ex) {
            throw new InstallerException("Could not install DCE to " + file.getAbsolutePath() + ".", ex);
        }
        installed = true;
        update();
        setChanged();
        notifyObservers();
    }

    public void uninstallDCE() throws InstallerException {
        try {
            installer.uninstall(file, is64Bit);
        } catch (InstallerException ex) {
            throw new InstallerException("Could not uninstall DCE to " + file.getAbsolutePath() + ".", ex);
        }
        installed = false;
        update();
        setChanged();
        notifyObservers();
    }

    public boolean isDCEInstalled() {
        return installed;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Installation other = (Installation) obj;
        if (this.file != other.file && (this.file == null || !this.file.equals(other.file))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + (this.file != null ? this.file.hashCode() : 0);
        return hash;
    }
}
