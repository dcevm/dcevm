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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public abstract class Installer {

    public String getJREDirectory() {
        return "jre";
    }

    public String getBackupLibraryName() {
        return getLibraryName() + ".backup";
    }

    public String getTemporaryLibraryName() {
        return getLibraryName() + ".temp";
    }

    public abstract String getClientDirectory();

    public abstract String getServerDirectory(boolean is64bit);

    public abstract String getLibraryName();

    public abstract String getJavaExecutable();

    public abstract List<Installation> listAllInstallations();

    public static Installer create() throws InstallerException {

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("windows")) {
            return new WindowsInstaller();
        } else if (os.contains("mac") || os.contains("darwin")) {
            return new MacInstaller();
        } else if (os.contains("unix") || os.contains("linux")) {
            return new LinuxInstaller();
        }
        throw new InstallerException("Unknown OS is unsupported.");
    }

    final protected void extractFile(String jarpath, String dest) throws InstallerException {
        boolean fileCreated = false;
        FileOutputStream output = null;
        InputStream in = Main.class.getClassLoader().getResourceAsStream(jarpath);
        if (in == null) {
            throw new InstallerException("Could not find " + jarpath + " in jar-file.");
        }

        boolean failure = false;
        try {
            output = new FileOutputStream(dest);
            fileCreated = true;
            int len = 0;
            byte[] cur = new byte[102400];
            while ((len = in.read(cur)) != -1) {
                output.write(cur, 0, len);
            }
        } catch (Exception ex) {
            failure = true;
            throw new InstallerException("Could not extract file " + dest + ".", ex);
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception e) {
                }
            }
            if (failure && fileCreated) {
                deleteFile(dest);
            }
        }
    }

    public void install(File dir, boolean bit64) throws InstallerException {
        if (isJDK(dir)) {
            dir = new File(dir, getJREDirectory());
        }

        String dcevm_jar = new File(dir, "lib" + File.separator + "ext" + File.separator + "dcevm.jar").getPath();
        extractFile("data/dcevm.jar", dcevm_jar);

        if (new File(dir, getServerDirectory(bit64)).exists()) {
            installClientServer(dir, getServerDirectory(bit64), bit64);
        }

        if (new File(dir, getClientDirectory()).exists() && !bit64) {
            try {
                installClientServer(dir, getClientDirectory(), false);
            } catch (InstallerException e) {
                uninstallClientServer(dir, getServerDirectory(bit64));
                throw e;
            }
        }
    }

    public void uninstall(File dir, boolean bit64) throws InstallerException {
        if (isJDK(dir)) {
            dir = new File(dir, getJREDirectory());
        }

        if (new File(dir, getServerDirectory(bit64)).exists()) {
            uninstallClientServer(dir, getServerDirectory(bit64));
        }

        if (new File(dir, getClientDirectory()).exists() && !bit64) {
            try {
                uninstallClientServer(dir, getClientDirectory());
            } catch (InstallerException e) {
                if (new File(dir, getServerDirectory(bit64)).exists()) {
                    installClientServer(dir, getServerDirectory(bit64), bit64);
                }
                throw e;
            }
        }

        String dcevm_jar = new File(dir, "lib" + File.separator + "ext" + File.separator + "dcevm.jar").getPath();
        try {
            deleteFile(dcevm_jar);
        } catch (InstallerException e) {
            install(dir, bit64);
            throw e;
        }
    }

    private void installClientServer(File jreDir, String directory, boolean bit64) throws InstallerException {
        String jarpath = "data/" + (bit64 ? "64/" : "") + directory.replace(File.separatorChar, '/') + "/" + getLibraryName();
        File clientOrServerDir = new File(jreDir, directory);
        String jvm_dll = new File(clientOrServerDir, getLibraryName()).getPath();
        String jvm_dll_backup = new File(clientOrServerDir, getBackupLibraryName()).getPath();

        renameFile(jvm_dll, jvm_dll_backup);

        try {
            extractFile(jarpath, jvm_dll);
        } catch (InstallerException e) {
            renameFile(jvm_dll_backup, jvm_dll);
            throw e;
        }
    }

    private void uninstallClientServer(File jreDir, String directory) throws InstallerException {
        File clientOrServerDir = new File(jreDir, directory);
        String jvm_dll = new File(clientOrServerDir, getLibraryName()).getPath();
        String jvm_dll_backup = new File(clientOrServerDir, getBackupLibraryName()).getPath();
        String jvm_dll_backup_temp = createTempFilename(clientOrServerDir, getBackupLibraryName() + "_", ".temp");

        renameFile(jvm_dll_backup, jvm_dll_backup_temp);

        try {
            deleteFile(jvm_dll);
        } catch (InstallerException e) {
            renameFile(jvm_dll_backup_temp, jvm_dll_backup);
            throw e;
        }

        try {
            renameFile(jvm_dll_backup_temp, jvm_dll);
        } catch (InstallerException e) {
            throw new InstallerException("Warning: JVM directory is in an inconistent state.", e);
        }
    }

    private String createTempFilename(File dir, String prefix, String suffix){
        String filename;
        int i=0;
        
        do
        {
            i++;
            filename = prefix + i + suffix;
        } while(new File(dir, filename).exists() && i<1000);

        return new File(dir, filename).getPath();
    }

    private void deleteFile(String file) throws InstallerException {
        File f = new File(file);

        boolean succ = false;
        try {
            succ = f.delete();
        } catch (Exception e) {
            throw new InstallerException("Could not delete " + file + ".", e);
        }

        if (!succ) {
            throw new InstallerException("Could not delete " + file + ".");
        }
    }

    private void renameFile(String from, String to) throws InstallerException {
        File ffr = new File(from);
        File fto = new File(to);
        if(!fto.isAbsolute()) fto = new File(ffr, to);

        if (!ffr.exists()) {
            throw new InstallerException("The file " + from + " does not exist.");
        }

        boolean succ = false;
        try {
            succ = ffr.renameTo(fto);
        } catch (Exception e) {
            throw new InstallerException("Could not rename " + from + " to " + to + ".", e);
        }

        if (!succ) {
            throw new InstallerException("Could not rename " + from + " to " + to + ".");
        }
    }

    public boolean isJRE(File directory) {
        if (directory.isDirectory() && directory.getName().startsWith("jre")) {

            String[] files = {getJavaExecutable()};
            for (int i = 0; i < files.length; i++) {
                if (!new File(directory, files[i]).exists()) {
                    return false;
                }
            }

            if (new File(directory, getClientDirectory()).exists()) {
                if (!new File(directory, getClientDirectory() + File.separator + getLibraryName()).exists()) {
                    return new File(directory, getClientDirectory() + File.separator + getBackupLibraryName()).exists();
                }
            }

            if (new File(directory, getServerDirectory(true)).exists()) {
                if (!new File(directory, getServerDirectory(true) + File.separator + getLibraryName()).exists()) {
                    return new File(directory, getServerDirectory(true) + File.separator + getBackupLibraryName()).exists();
                }
            }
            return true;
        }
        return false;
    }

    public boolean isJDK(File directory) {
        if (directory.isDirectory()) {
            File jreDir = new File(directory, getJREDirectory());
            return isJRE(jreDir);
        }
        return false;
    }

    final public String executeJava(File jreDir, String... params) throws InstallerException {
        File command = new File(jreDir, getJavaExecutable());
        try {
            StringBuilder result = new StringBuilder();
            String line;
            ArrayList<String> pp = new ArrayList<String>();
            pp.add(command.getAbsolutePath());
            pp.addAll(Arrays.asList(params));
            Process p = Runtime.getRuntime().exec(pp.toArray(new String[0]));
            BufferedReader input =
                    new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = input.readLine()) != null) {
                result.append(line);
                result.append('\n');
            }
            input.close();
            return result.toString();
        } catch (Exception err) {
            throw new InstallerException("Could not execute " + command + ".", err);
        }
    }

    public boolean isDCEInstalled(File dir) throws InstallerException {
        File jreDir;
        if (isJDK(dir)) {
            jreDir = new File(dir, "jre");
        } else {
            jreDir = dir;
        }
        File jvmDCEClientFile = new File(jreDir, getClientDirectory() + File.separator + getBackupLibraryName());
        String serverDir = getServerDirectory(false);
        if (!new File(jreDir, serverDir).exists()) {
            serverDir = getServerDirectory(true);
        }
        File jvmDCEServerFile = new File(jreDir, serverDir + File.separator + getBackupLibraryName());

        if (new File(jreDir, getClientDirectory()).exists() && new File(jreDir, serverDir).exists()) {
            if (jvmDCEServerFile.exists() != jvmDCEClientFile.exists()) {
                throw new InstallerException(jreDir.getAbsolutePath() + " has invalid state.");
            }
        }
        return jvmDCEClientFile.exists() || jvmDCEServerFile.exists();
    }

    final public String getVersionString(File jreDir) throws InstallerException {
        return executeJava(jreDir, "-version");
    }

    final public boolean is64Bit(File jreDir) throws InstallerException {
        return getVersionString(jreDir).contains("64-Bit");
    }

    final public String getJavaVersion(File jreDir) throws InstallerException {
        return getVersionHelper(jreDir, ".*java version.*\"(.*)\".*", true);
    }

    final public String getDCEVersion(File jreDir) throws InstallerException {
        return getVersionHelper(jreDir, ".*Dynamic Code Evolution.*build ([^,]+),.*", false);
    }

    private String getVersionHelper(File jreDir, String regex, boolean javaVersion) throws InstallerException {
        String version = getVersionString(jreDir);
        version = version.replaceAll("\n", "");
        Matcher matcher = Pattern.compile(regex).matcher(version);

        if (!matcher.matches()) {
            throw new InstallerException("Could not get " + (javaVersion ? "java" : "dce") + "version of " + jreDir.getAbsolutePath() + ".");
        }

        version = matcher.replaceFirst("$1");
        return version;
    }
}
