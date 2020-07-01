/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 * @author Ivan Dubrov
 * @author Jiri Bubnik
 * @author Przemysław Rumik
 * @author Denis Zygann
 */
public enum ConfigurationInfo {

    // Note: 32-bit is not supported on Mac OS X
    MAC_OS(null, "bsd_amd64_compiler2",
            "lib/client", "lib/server", "lib/dcevm", "lib/server", "lib/dcevm",
            "bin/java", "libjvm.dylib") {
        @Override
        public String[] paths() {
            return new String[] { "/Library/Java/JavaVirtualMachines/" };
        }
    },
    LINUX("linux_i486_compiler2", "linux_amd64_compiler2",
            "lib/i386/client", "lib/i386/server", "lib/i386/dcevm", "lib/amd64/server", "lib/amd64/dcevm",
            "bin/java", "libjvm.so") {
        @Override
        public String[] paths() {
            return new String[]{"/usr/java", "/usr/lib/jvm"};
        }
    },
    WINDOWS("windows_i486_compiler2", "windows_amd64_compiler2",
            "bin/client", "bin/server", "bin/dcevm", "bin/server", "bin/dcevm",
            "bin/java.exe", "jvm.dll") {
        @Override
        public String[] paths() {
            return new String[]{
                    System.getenv("JAVA_HOME") + "/..",
                    System.getenv("PROGRAMW6432") + "/JAVA",
                    System.getenv("PROGRAMFILES") + "/JAVA",
                    System.getenv("PROGRAMFILES(X86)") + "/JAVA",
                    System.getenv("SYSTEMDRIVE") + "/JAVA"};
        }
    };

    private final String resourcePath32;
    private final String resourcePath64;

    private final String clientPath;
    private final String server32Path;
    private final String dcevm32Path;
    private final String server64Path;
    private final String dcevm64Path;

    private final String javaExecutable;
    private final String libraryName;

    ConfigurationInfo(String resourcePath32, String resourcePath64,
                      String clientPath,
                      String server32Path, String dcevm32Path,
                      String server64Path, String dcevm64Path,
                      String javaExecutable, String libraryName) {
        this.resourcePath32 = resourcePath32;
        this.resourcePath64 = resourcePath64;
        this.clientPath = clientPath;
        this.server32Path = server32Path;
        this.dcevm32Path = dcevm32Path;
        this.server64Path = server64Path;
        this.dcevm64Path = dcevm64Path;
        this.javaExecutable = javaExecutable;
        this.libraryName = libraryName;
    }

    public String getResourcePath(boolean bit64) {
        return bit64 ? resourcePath64 : resourcePath32;
    }

    public String getResourcePath32() {
        return resourcePath32;
    }

    public String getResourcePath64() {
        return resourcePath64;
    }

    public String getClientPath() {
        return clientPath;
    }

    public String getServerPath(boolean bit64) {
        return bit64 ? server64Path : server32Path;
    }

    public String getServer32Path() {
        return server32Path;
    }

    public String getServer64Path() {
        return server64Path;
    }

    public String getDcevm32Path() {
        return dcevm32Path;
    }

    public String getDcevm64Path() {
        return dcevm64Path;
    }

    public String getJavaExecutable() {
        return javaExecutable;
    }

    public String getLibraryName() {
        return libraryName;
    }

    public String getBackupLibraryName() {
        return libraryName + ".backup";
    }

    public String[] paths() {
        return new String[0];
    }

    public static ConfigurationInfo current() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("windows")) {
            return ConfigurationInfo.WINDOWS;
        } else if (os.contains("mac") || os.contains("darwin")) {
            return ConfigurationInfo.MAC_OS;
        } else if (os.contains("unix") || os.contains("linux")) {
            return ConfigurationInfo.LINUX;
        }
        throw new IllegalStateException("OS is unsupported: " + os);
    }

    // Utility methods to query installation directories
    public boolean isJRE(Path directory) {
        if (Files.isDirectory(directory)) {
            if (!Files.exists(directory.resolve(getJavaExecutable()))) {
                return false;
            }

            Path client = directory.resolve(getClientPath());
            if (Files.exists(client)) {
                if (!Files.exists(client.resolve(getLibraryName()))) {
                    return Files.exists(client.resolve(getBackupLibraryName()));
                }
            }

            Path server = directory.resolve(getServer64Path());
            if (Files.exists(server)) {
                if (!Files.exists(server.resolve(getLibraryName()))) {
                    return Files.exists(server.resolve(getBackupLibraryName()));
                }
            }
            return true;
        }
        return false;
    }

    public boolean isJDK(Path directory) {
        if (Files.isDirectory(directory)) {
            Path jreDir = directory.resolve(getJREDirectory());
            return isJRE(jreDir);
        }
        return false;
    }

    public String executeJava(Path jreDir, String... params) throws IOException {
        Path executable = jreDir.resolve(getJavaExecutable());
        String[] commands = new String[params.length + 1];
        System.arraycopy(params, 0, commands, 1, params.length);
        commands[0] = executable.toAbsolutePath().toString();
        Process p = Runtime.getRuntime().exec(commands);

        StringBuilder result = new StringBuilder();
        try (InputStream in = p.getErrorStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
                result.append('\n');
            }
        }
        return result.toString();
    }

    public boolean isDCEInstalled(Path dir, boolean altjvm) {
        Path jreDir;
        if (isJDK(dir)) {
            jreDir = dir.resolve("jre");
        } else {
            jreDir = dir;
        }

        if (altjvm) {
            Path altvm32Path = jreDir.resolve(getDcevm32Path());
            Path altvm64Path = jreDir.resolve(getDcevm64Path());

            return Files.exists(altvm32Path) || Files.exists(altvm64Path);
        } else {
            Path clientPath = jreDir.resolve(getClientPath());
            Path clientBackup = clientPath.resolve(getBackupLibraryName());

            Path serverPath = jreDir.resolve(getServer32Path());
            if (!Files.exists(serverPath)) {
                serverPath = jreDir.resolve(getServer64Path());
            }
            Path serverBackup = serverPath.resolve(getBackupLibraryName());

            if (Files.exists(clientPath) && Files.exists(serverPath)) {
                if (Files.exists(clientBackup) != Files.exists(serverBackup)) {
                    throw new IllegalStateException(jreDir.toAbsolutePath() + " has invalid state.");
                }
            }
            return Files.exists(clientBackup) || Files.exists(serverBackup);
        }
    }

    public String getVersionString(Path jreDir, boolean altjvm) {
        try {
            if (altjvm) {
                return executeJava(jreDir,  "-XXaltjvm=dcevm", "-version");
            } else {
                return executeJava(jreDir, "-version");
            }
        } catch (Throwable e) {
            return e.getMessage();
        }
    }

    public boolean is64Bit(Path jreDir) {
        String versionString = getVersionString(jreDir, false);
        return versionString.contains("64-Bit") || versionString.contains("amd64");
    }

    public String getJavaVersion(Path jreDir) throws IOException {
        return getVersionHelper(jreDir, ".*(?:java|openjdk) version.*\"(.*)\".*", true, false);
    }

    final public String getDCEVersion(Path jreDir, boolean altjvm) throws IOException {
        return getVersionHelper(jreDir, ".*Dynamic Code Evolution.*build ([^,]+),.*", false, altjvm);
    }

    private String getVersionHelper(Path jreDir, String regex, boolean javaVersion, boolean altjvm) {
        String version = getVersionString(jreDir, altjvm);
        version = version.replaceAll("\n", "");
        Matcher matcher = Pattern.compile(regex).matcher(version);

        if (!matcher.matches()) {
            return "Could not get " + (javaVersion ? "java" : "dce") +
                    "version of " + jreDir.toAbsolutePath() + ".";
        }

        version = matcher.replaceFirst("$1");
        return version;
    }

    public String getJREDirectory() {
        return "jre";
    }
}
