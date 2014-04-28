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
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 * @author Ivan Dubrov
 */
public class Installer {

    private ConfigurationInfo config;

    public Installer(ConfigurationInfo config) {
        this.config = config;
    }

    public void install(Path dir, boolean bit64) throws IOException {
        if (config.isJDK(dir)) {
            dir = dir.resolve(config.getJREDirectory());
        }

        Path serverPath = dir.resolve(config.getServerPath(bit64));
        if (Files.exists(serverPath)) {
            installClientServer(serverPath, bit64);
        }

        Path clientPath = dir.resolve(config.getClientPath());
        if (Files.exists(clientPath) && !bit64) {
            installClientServer(clientPath, false);
        }
    }

    public void uninstall(Path dir, boolean bit64) throws IOException {
        if (config.isJDK(dir)) {
            dir = dir.resolve(config.getJREDirectory());
        }

        Path serverPath = dir.resolve(config.getServerPath(bit64));
        if (Files.exists(serverPath)) {
            uninstallClientServer(serverPath);
        }

        Path clientPath = dir.resolve(config.getClientPath());
        if (Files.exists(clientPath) && !bit64) {
            uninstallClientServer(clientPath);
        }
    }

    public List<Installation> listInstallations() {
        return scanPaths(config.paths());
    }

    public ConfigurationInfo getConfiguration() {
        return config;
    }

    private void installClientServer(Path path, boolean bit64) throws IOException {
        String resource = config.getResourcePath(bit64) + "/product/" + config.getLibraryName();

        Path library = path.resolve(config.getLibraryName());
        Path backup = path.resolve(config.getBackupLibraryName());


        Files.move(library, backup);
        try {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                Files.copy(in, library);
            }
        } catch (IOException e) {
            Files.move(backup, library, StandardCopyOption.REPLACE_EXISTING);
            throw e;
        }
    }

    private void uninstallClientServer(Path path) throws IOException {
        Path library = path.resolve(config.getLibraryName());
        Path backup = path.resolve(config.getBackupLibraryName());

        Files.delete(library);
        Files.move(backup, library); // FIXME: if fails, JRE is inconsistent!
    }

    private List<Installation> scanPaths(String... dirPaths) {
        List<Installation> installations = new ArrayList<>();
        for (String dirPath : dirPaths) {
            Path dir = Paths.get(dirPath);
            if (Files.isDirectory(dir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    scanDirectory(stream, installations);
                } catch (Exception ex) {
                    // Ignore, try different directory
                }
            }
        }
        return installations;
    }

    private void scanDirectory(DirectoryStream<Path> stream, List<Installation> installations) {
        for (Path path : stream) {
            try {
                Installation inst = new Installation(config, path);
                if (!installations.contains(inst)) {
                    installations.add(inst);
                }
            } catch (Exception e) {
                // FIXME: just ignore the installation for now..
            }
        }
    }
}
