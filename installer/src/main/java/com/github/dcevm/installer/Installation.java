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
package com.github.dcevm.installer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Observable;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 * @author Ivan Dubrov
 * @author Jiri Bubnik
 */
public class Installation extends Observable {

    private final Path file;
    private final ConfigurationInfo config;

    private final boolean isJDK;

    // DCEVM is installed over default JVM (either server or client)
    private boolean installed;

    // DCEVM is installed as an alternative DCEVM (in separate dcevm directory)
    private boolean installedAltjvm;

    // version of Java
    private String version;
    // version of DCEVM in main location (client/server)
    private String versionDcevm;
    // version of DCEVM in alternative location (dcevm altjvm)
    private String versionDcevmAltjvm;

    private boolean is64Bit;

    public Installation(ConfigurationInfo config, Path path) throws IOException {
        this.config = config;
        try {
            file = path.toRealPath();
        } catch (IOException ex) {
            throw new IllegalArgumentException(path.toAbsolutePath() + " is no JRE or JDK-directory.");
        }

        isJDK = config.isJDK(file);
        if (!isJDK && !config.isJRE(file)) {
            throw new IllegalArgumentException(path.toAbsolutePath() + " is no JRE or JDK-directory.");
        }

        version = config.getJavaVersion(file);
        update();
    }

    final public void update() throws IOException {
        installed = config.isDCEInstalled(file, false);
        versionDcevm = installed ? config.getDCEVersion(file, false) : "";

        installedAltjvm = config.isDCEInstalled(file, true);
        versionDcevmAltjvm = installedAltjvm ? config.getDCEVersion(file, true) : "";

        is64Bit = config.is64Bit(file);
    }

    public Path getPath() {
        return file;
    }

    public String getVersion() {
        return version;
    }

    public String getVersionDcevm() {
        return versionDcevm;
    }

    public String getVersionDcevmAltjvm() {
        return versionDcevmAltjvm;
    }

    public boolean isJDK() {
        return isJDK;
    }

    public boolean is64Bit() {
        return is64Bit;
    }

    public void installDCE(boolean altjvm) throws IOException {
        new Installer(config).install(file, is64Bit, altjvm);
        update();
        setChanged();
        notifyObservers();
    }

    public void uninstallDCE() throws IOException {
        new Installer(config).uninstall(file, is64Bit);
        installed = false;
        installedAltjvm = false;
        update();
        setChanged();
        notifyObservers();
    }

    public boolean isDCEInstalled() {
        return installed;
    }

    public boolean isDCEInstalledAltjvm() {
        return installedAltjvm;
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
        return !(this.file != other.file && (this.file == null || !this.file.equals(other.file)));
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + (this.file != null ? this.file.hashCode() : 0);
        return hash;
    }
}
