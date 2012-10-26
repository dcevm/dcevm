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
package at.ssw.hotswap;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * @author Thomas Wuerthinger
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class HotSwapTool {

    /**
     * Prefix for the version number in the class name. The class bytes are modified that this string including
     * the following number is removed. This means that e.g. A___2 is treated as A anywhere in the source code. This is introduced
     * to make the IDE not complain about multiple defined classes.
     */
    public static final String IDENTIFIER = "___";
    /**
     * Level at which the program prints console output. Use 0 to disable the console output. *
     */
    private static int TRACE_LEVEL;
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static Map<Class<?>, Integer> currentVersion = new Hashtable<Class<?>, Integer>();
    private static int redefinitionCount;
    private static long totalTime;

    public static void setTraceLevel(int level) {
        TRACE_LEVEL = level;
    }

    /**
     * Utility method for dumping the current call stack. The parameter identifies the class version the caller method corresponds to.
     *
     * @param version the manually specified class version of the caller method
     */
    public static void dumpEnter(int version) {

        System.out.print("Method enter version " + version + " of ");

        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        if (trace.length < 3) {
            return;
        }

        boolean first = true;
        for (int i = 2; i <= 3; i++) {
            StackTraceElement elem = trace[i];
            if (elem.getClassName().startsWith("at.ssw")) {
                String className = elem.getClassName();
                className = className.substring(className.lastIndexOf('.') + 1);
                if (first) {
                    first = false;
                    System.out.print(className + "." + elem.getMethodName() + ": ");
                } else {
                    System.out.print(className + "." + elem.getMethodName() + "[" + elem.getLineNumber() + "] ");
                }
            }
        }
        System.out.println();
    }

    /**
     * Returns the current version of the inner classes of a specified outer class.
     *
     * @param baseClass the outer class whose version is queried
     * @return the version of the inner classes of the specified outer class
     */
    public static int getCurrentVersion(Class<?> baseClass) {
        if (!currentVersion.containsKey(baseClass)) {
            currentVersion.put(baseClass, 0);
        }
        return currentVersion.get(baseClass).intValue();
    }

    /**
     * Performs an explit shutdown and disconnects from the VM.
     */
    public static void shutdown() {
        final JDIProxy jdi = JDIProxy.getJDI();

        try {
            jdi.disconnect();
        } catch (HotSwapException e) {
            throw new Error(e);
        }
    }

    /**
     * Redefines the Java class specified by the array of class files.
     *
     * @param files the class files with the byte codes of the new version of the java classes
     * @throws HotSwapException       if redefining the classes failed
     * @throws ClassNotFoundException if a class that is not yet loaded in the target VM should be redefined
     */
    public static void redefine(Iterable<File> files, final Map<String, String> replacements) throws HotSwapException, ClassNotFoundException {

        final JDIProxy jdi = JDIProxy.getJDI();
        assert jdi.isConnected();

        // Be sure to be in sync with the VM.
        jdi.refreshAllClasses();
        List<ReferenceType> klasses = jdi.allClasses();
        if (TRACE_LEVEL >= 2) {
            System.out.println("Number of classes loaded before redefinition: " + klasses.size());
        }

        Map<String, ReferenceType> klassesMap = new HashMap<String, ReferenceType>();
        for (ReferenceType t : klasses) {
            if (!t.name().endsWith("[]")) {
                assert !klassesMap.containsKey(t.name()) : "Must not contain two classes with same name " + t.name() + "!";
                klassesMap.put(t.name(), t);
            }
        }

        // Print loaded types
        if (TRACE_LEVEL >= 4) {
            for (Entry<String, ReferenceType> entry : klassesMap.entrySet()) {
                System.out.println("Loaded reference type: " + entry.getValue());
            }
        }

        final Set<String> redefinedClasses = new HashSet<String>();
        Map<ReferenceType, byte[]> map = new HashMap<ReferenceType, byte[]>();

        for (File f : files) {
            try {

                InputStream s = new FileInputStream(f);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

                TestCaseClassAdapter adapter = new TestCaseClassAdapter(writer, new TypeNameAdapter() {

                    public String adapt(String typeName) {
                        String newClass = getReplacementClassName(typeName, replacements);
                        return newClass;
                    }
                });

                ClassReader cr = new ClassReader(s);
                cr.accept(adapter, 0);
                s.close();
                byte[] bytes = writer.toByteArray();

                String className = adapter.name.replace('/', '.');
                ReferenceType t = klassesMap.get(className);

                assert t != null : "Class must be loaded in target VM (name=" + className + ")!";

                // TODO: Check if the hack to put in java/lang/Object as the name for every class is apropriate.
                // This could be used to make redefinition of not-yet-loaded classes possible.
                // Problem: Which class loaded to choose from? Currently a ReferenceType object is a combination of classname/classloader.
                map.put(t, bytes);
                redefinedClasses.add(t.name());
            } catch (FileNotFoundException e) {
                throw new HotSwapException(
                        "Could not find specified class file", e);
            } catch (IOException e) {
                throw new HotSwapException(
                        "IO exception while reading class file", e);
            }
        }

        if (TRACE_LEVEL >= 2) {
            System.out.print("Redefining the classes:");
            for (ReferenceType t : map.keySet()) {
                System.out.print(" " + t.name());
            }
            System.out.println();
        }

        long startTime = System.currentTimeMillis();
        jdi.getVM().redefineClasses(map);

        long curTime = System.currentTimeMillis() - startTime;
        totalTime += curTime;
        redefinitionCount++;

    }

    private static void getAndTouchMainStackFrames() {
        try {
            List<StackFrame> frames = HotSwapTool.getStackFramesOfMainThread();
            for (int i = 0; i < frames.size(); i++) {
                StackFrame frame = frames.get(i);

                // Access the parts of the stack frame one-by-one.
                Field f = null;
                try {
                    f = frame.getClass().getDeclaredField("location");
                } catch (NoSuchFieldException ex) {
                    throw new HotSwapException(ex);
                } catch (SecurityException ex) {
                    throw new HotSwapException(ex);
                }
                f.setAccessible(true);
                Location l = null;
                try {
                    l = (Location) f.get(frame);
                } catch (IllegalArgumentException ex) {
                    throw new HotSwapException(ex);
                } catch (IllegalAccessException ex) {
                    throw new HotSwapException(ex);
                }
                if (TRACE_LEVEL >= 3) {
                    System.out.println("Frame number " + i);

                }
                com.sun.jdi.Method method = l.method();
                if (TRACE_LEVEL >= 3) {
                    System.out.println("Method: " + method);
                }
                int lineNumber = l.lineNumber();
                if (TRACE_LEVEL >= 3) {
                    System.out.println(lineNumber + " ");
                }
                if (l.lineNumber() == -1) {
                    String methodString = l.method().toString();
                    if (TRACE_LEVEL >= 3) {
                        System.out.print(methodString);
                        System.out.print("+");
                        System.out.println(l.codeIndex());
                    }
                } else if (TRACE_LEVEL >= 3) {
                    System.out.print(l.declaringType().name());
                    System.out.print(":");
                    System.out.println(l.lineNumber());
                }
            }
        } catch (HotSwapException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Redefines all inner classes of a outer class to a specified version. Inner classes who do not have a particular
     * representation for a version remain unchanged.
     *
     * @param outerClass    the outer class whose inner classes should be redefined
     * @param versionNumber the target version number
     */
    public static void toVersion(Class<?> outerClass, int versionNumber) {
        assert versionNumber >= 0;

        if (TRACE_LEVEL >= 2) {
            System.out.println("Entering toVersion");
        }

        // Touch stack frames to make sure everything is OK.
        getAndTouchMainStackFrames();

        if (TRACE_LEVEL >= 1) {
            System.out.println("Changing class " + outerClass.getSimpleName() + " to version number " + versionNumber + " from version " + getCurrentVersion(outerClass));
        }

        if (versionNumber == getCurrentVersion(outerClass)) {
            // Nothing to do!
            return;
        }

        Map<String, File> files = findClassesWithVersion(outerClass, versionNumber);

        // Make sure all classes are loaded in the VM, before they are redefined
        List<Class<?>> classes = new ArrayList<Class<?>>();
        for (String name : files.keySet()) {
            try {
                classes.add(Class.forName(name));
                if (TRACE_LEVEL >= 2) {
                    System.out.println("Class added: " + classes.get(classes.size() - 1));
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                assert false;
                return;
            }
        }
        Map<String, String> replacements = new HashMap<String, String>();

        for (Class c : classes) {
            Annotation a = c.getAnnotation(ClassRedefinitionPolicy.class);
            if (a != null && a instanceof ClassRedefinitionPolicy) {
                Class rep = ((ClassRedefinitionPolicy) a).alias();

                if (rep != ClassRedefinitionPolicy.NoClass.class) {
                    String oldClassName = c.getName();
                    String newClassName = rep.getName();
                    replacements.put(oldClassName, newClassName);
                } else {
                    replacements.put(c.getName(), stripVersion(c.getName()));
                }
            } else {
                replacements.put(c.getName(), stripVersion(c.getName()));
            }
        }

        for (String name : files.keySet()) {
            String curClassName = getReplacementClassName(name, replacements);

            try {
                classes.add(Class.forName(curClassName));
                if (TRACE_LEVEL >= 2) {
                    System.out.println("Class added: " + classes.get(classes.size() - 1));
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                assert false;
                return;
            }
        }
        try {
            redefine(files.values(), replacements);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            assert false;
            return;
        } catch (HotSwapException e) {
            e.printStackTrace();
            assert false;
            return;
        }

        // Touch stack frames again for testing purposes.
        getAndTouchMainStackFrames();

        if (TRACE_LEVEL >= 2) {
            for (Class<?> c : classes) {
                System.out.println("Classes were: " + c.getName());
                assert isClassLoaded(c.getName());
            }
        }
        setCurrentVersion(outerClass, versionNumber);
        if (TRACE_LEVEL >= 2) {
            System.out.println("Version successfully changed to " + versionNumber);
        }
        assert getCurrentVersion(outerClass) == versionNumber;
    }

    private static Map<String, File> findClassesWithVersion(Class<?> baseClass, int version) {
        Map<String, File> classes = new HashMap<String, File>();

        String packageName = baseClass.getPackage().getName().replace('.', '/');
        URL url = baseClass.getClassLoader().getResource(packageName);
        File folder = new File(url.getFile());
        for (File f : folder.listFiles(IsClassFile.INSTANCE)) {
            String fileName = f.getName();
            String simpleName = f.getName().substring(0, f.getName().length() - CLASS_FILE_SUFFIX.length());
            String name = baseClass.getPackage().getName() + '.' + simpleName;

            if (isInnerClass(name, baseClass) && parseClassVersion(fileName) == version) {
                classes.put(name, f);
            }
        }
        return classes;
    }

    private enum IsClassFile implements FilenameFilter {
        INSTANCE;

        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(CLASS_FILE_SUFFIX);
        }
    }

    /**
     * Parse version of the class from the class name. Classes are named in the form of [Name]___[Version]
     *
     * @param name
     * @return
     */
    private static int parseClassVersion(String name) {
        if (!name.endsWith(CLASS_FILE_SUFFIX)) {
            return -1;
        }

        int index = name.indexOf(IDENTIFIER);
        if (index == -1) {
            return 0;
        }

        int result = 0;
        int curIndex = index + IDENTIFIER.length();
        while (curIndex < name.length()) {

            char c = name.charAt(curIndex);
            if (!Character.isDigit(c)) {
                break;
            }
            curIndex++;
            result *= 10;
            result += c - '0';
        }

        return result;
    }

    private static boolean isInnerClass(String name, Class<?> baseClass) {
        return name.contains("." + baseClass.getSimpleName() + "$") || name.startsWith(baseClass.getSimpleName() + "$");
    }

    private static void setCurrentVersion(Class<?> baseClass, int value) {
        currentVersion.put(baseClass, value);
    }

    private static String getReplacementClassName(String clazz, Map<String, String> replacements) {
        if (clazz == null) {
            return null;
        }
        ClassIdentifier classIdentifier = new ClassIdentifier(clazz);

        for (String key : replacements.keySet()) {
            String newClazz = classIdentifier.getDescriptor();
            if (newClazz.startsWith(key)) {
                newClazz = newClazz.substring(key.length());
                classIdentifier.setDescriptor(replacements.get(key) + newClazz);
                return classIdentifier.getOriginal();
            }
        }
        return clazz;
    }

    public static String decodeDescriptor(String s) {
        assert s.charAt(0) == 'L' && s.charAt(s.length() - 1) == ';' : "argument must be type descriptor LCLASSNAME;";
        String typeName = s.substring(1, s.length() - 1);
        typeName = typeName.replace('/', '.');
        return typeName;
    }

    private static String stripVersion(String className) {
        if (className == null) {
            return null;
        }
        int index = className.indexOf(IDENTIFIER);
        if (index != -1) {

            int curIndex = index + IDENTIFIER.length();
            while (curIndex < className.length()) {
                if (Character.isDigit(className.charAt(curIndex))) {
                    curIndex++;
                } else {
                    break;
                }
            }

            className = className.substring(0, index) + className.substring(curIndex);
        }
        return className;
    }

    public static List<StackFrame> getStackFramesOfMainThread() {
        final JDIProxy jdi = JDIProxy.getJDI();
        final List<StackFrame> resultList = new ArrayList<StackFrame>();
        Runnable r = new Runnable() {

            public void run() {
                final List<ThreadReference> threads = jdi.getVM().allThreads();
                for (ThreadReference t : threads) {
                    if (t.name().equals("main")) {
                        try {
                            if (TRACE_LEVEL >= 3) {
                                System.out.println("Before accessing stack frames");
                            }
                            resultList.addAll(t.frames());
                            if (TRACE_LEVEL >= 3) {
                                System.out.println("Number of stack frames: " + resultList.size());
                            }
                        } catch (IncompatibleThreadStateException ex) {
                            throw new IllegalStateException(ex);
                        }
                    }
                }
            }
        };

        jdi.executeSuspended(r);
        return resultList;
    }

    private static boolean isClassLoaded(String className) {
        List<ReferenceType> klasses = JDIProxy.getJDI().allClasses();
        for (ReferenceType klass : klasses) {
            if (klass.name().equals(className)) {
                return true;
            }
        }
        return false;
    }

    public static void resetTimings() {
        redefinitionCount = 0;
        totalTime = 0;
    }

    public static int getRedefinitionCount() {
        return redefinitionCount;
    }

    public static long getTotalTime() {
        return totalTime;
    }
}
