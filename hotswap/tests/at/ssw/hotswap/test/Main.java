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

package at.ssw.hotswap.test;

import at.ssw.hotswap.HotSwapTool;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/**
 * Main class for running class redefinition tests. Make sure that the execution directory is set such that this class file
 * can be reached via "at/ssw/hotswap/test/Main.class".
 * 
 * There are different levels of redefinition:
 * Swap method bodies < add/remove methods < add/remove fields < add/remove super type
 * 
 * Make sure that the application is started with a Java debug agent on port 4000. If you specify an argument, only tests
 * containing the specified string are executed.
 * 
 * Example usage:
 * <pre>java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=4000,suspend=n at.ssw.hotswap.test.Main SimpleTest</pre>
 * 
 * @author Thomas Wuerthinger
 *
 * Usage:
 * If a first parameter is given, then only tests with a name containing this parameter are executed.
 *
 * Default values of system properties that can be overwritten:
 * -Dhotswap.trace=0
 *
 */
public class Main {

    private static int failedCount;
    private static int finishedCount;
    private static String failureString = "";

    public static void main(final String[] args) {

        boolean runNativeTests = true;
        String nativeLibraryName = new File("../../../HotSwapTestsNatives/dist/" + System.mapLibraryName("libHotSwapTestsNatives")).getAbsolutePath();
        try {
            System.out.println("Load native library: ");
            System.load(nativeLibraryName);
        } catch(UnsatisfiedLinkError e) {
            System.out.println("WARNING: Could not load native library from path " + nativeLibraryName);
            System.out.println("Disabling native tests");
            runNativeTests = false;
        }

        System.out.println("Running JUnit tests: ");

        JUnitCore core = new JUnitCore();
        core.addListener(runListener);
        Request request = Request.classes(CompleteTestSuite.class);
        HotSwapTool.setTraceLevel(Integer.parseInt(System.getProperty("hotswap.trace", "0")));

        // Filter the request?
        if (args.length > 0) {

            System.out.println("Only run tests containing \"" + args[0] + "\"");

            request = request.filterWith(new Filter() {

                @Override
                public String describe() {
                    return "Filter";
                }

                private Set<Description> childrenToRun = new HashSet<Description>();

                @Override
                public boolean shouldRun(Description d) {
                    System.out.println(d.getDisplayName());

                    if (d.getDisplayName().contains(args[0]) || childrenToRun.contains(d)) {
                        childrenToRun.addAll(d.getChildren());
                        return true;
                    }

                    // explicitly check if any children want to run
                    for (Description each : d.getChildren()) {
                        if (shouldRun(each)) {
                            return true;
                        }
                    }

                    return false;
                }
            });
        }

        long startTime = System.currentTimeMillis();
        core.run(request);
        long time = System.currentTimeMillis() - startTime;

        System.out.println("" + (finishedCount - failedCount) + " of " + finishedCount + " tests are OK!");
        System.out.println("Time: " + ((double) time) / 1000);
        if (failedCount == 0) {
            System.out.println("ALL OK");
        } else {
            System.out.println(failedCount + " FAILURES: " + failureString);
        }
    }

    private static RunListener runListener = new RunListener() {

        @Override
        public void testStarted(Description description) throws Exception {
            System.out.println("============================================================");
            System.out.println("Test started: " + description.getDisplayName());
        }

        @Override
        public void testFailure(Failure failure) throws Exception {
            System.out.println("Test failure: " + failure.getMessage());
            failure.getException().printStackTrace();
            failedCount++;
            failureString += failure.getDescription().getDisplayName() + " ";
        }

        @Override
        public void testFinished(Description description) throws Exception {
            System.out.println("Test finished: " + description.getDisplayName());
            finishedCount++;
        }
    };
}
