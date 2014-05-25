package com.github.dcevm.installer;

/**
 * DCEVM patch does not exists for the version of Java.
 */
public class DcevmPatchNotFoundException extends Exception {
    // Java version to patch
    String javaVersion;

    // install path
    String installPath;

    public DcevmPatchNotFoundException(String javaVersion, String installPath) {
        this.javaVersion = javaVersion;
        this.installPath = installPath;
    }

    @Override
    public String getMessage() {
        return "DCEVM patch is not available for Java version '" + getJavaVersion() + "' at '" + getInstallPath() + "'.";
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getInstallPath() {
        return installPath;
    }
}
