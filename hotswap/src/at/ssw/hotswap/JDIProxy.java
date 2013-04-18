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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.Connector.Argument;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for performing hotswapping. Two main use cases:
 * <ul>
 * <li>Redefine a set of classes using the {@link #redefine(File[]) redefine} method.</li>
 * <li>Redefine all inner classes of a specified outer class to a certain version using the
 * {@link #toVersion(Class, int) toVersion} method.
 * The magic prefix to identify version numbers in classes is specified by the {@link #IDENTIFIER IDENTIFIER} field.
 * </li>
 * </ul>
 *
 * @author Thomas Wuerthinger
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 *
 */
public class JDIProxy {

    private static final String PORT_ARGUMENT_NAME = "port";
    private static final String TRANSPORT_NAME = "dt_socket";
    
    private VirtualMachine vm;

    private static JDIProxy jdi;
    
    /** Port at which to connect to the agent of the VM. **/
    public static final int PORT = Integer.getInteger("debugPort", 4000);
    
    private JDIProxy() {
    }

    public static JDIProxy getJDI() {
        if (jdi == null) {

            jdi = new JDIProxy();
            try {
                jdi.connect(PORT);
            } catch (HotSwapException e) {
                throw new IllegalStateException(e);
            }
        }

        return jdi;
    }

    public void connect(int port) throws HotSwapException {

        if (isConnected()) {
            throw new HotSwapException("Already connected");
        }
        VirtualMachineManager manager = Bootstrap.virtualMachineManager();

        // Find appropiate connector
        List<AttachingConnector> connectors = manager.attachingConnectors();
        AttachingConnector chosenConnector = null;
        for (AttachingConnector c : connectors) {
            if (c.transport().name().equals(TRANSPORT_NAME)) {
                chosenConnector = c;
                break;
            }
        }
        if (chosenConnector == null) {
            throw new HotSwapException("Could not find socket connector");
        }

        // Set port argument
        AttachingConnector connector = chosenConnector;
        Map<String, Argument> defaults = connector.defaultArguments();
        Argument a = defaults.get(PORT_ARGUMENT_NAME);
        if (a == null) {
            throw new HotSwapException("Could not find port argument");
        }
        a.setValue(Integer.toString(port));

        // Attach
        try {
            System.out.println("Connector arguments: " + defaults);
            vm = connector.attach(defaults);
           // vm.setDebugTraceMode(VirtualMachine.TRACE_EVENTS);
        } catch (IOException e) {
            throw new HotSwapException("IO exception during attach", e);
        } catch (IllegalConnectorArgumentsException e) {
            throw new HotSwapException("Illegal connector arguments", e);
        }

        assert isConnected();
    }

    public void disconnect() throws HotSwapException {
        assert isConnected();
        vm.dispose();
        vm = null;
        assert !isConnected();
    }

    // Checks if the tool is currently connected
    public boolean isConnected() {
        return vm != null;
    }

    public VirtualMachine getVM() {
        return vm;
    }

    /**
     * Call this method before calling allClasses() in order to refresh the JDI state of loaded classes.
     * This is necessary because the JDI map of all loaded classes is only updated based on events received over JDWP (network connection)
     * and therefore it is not necessarily up-to-date with the real state within the VM.
     */
    public void refreshAllClasses() {
        try {
            Field f = vm.getClass().getDeclaredField("retrievedAllTypes");
            f.setAccessible(true);
            f.set(vm, false);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(HotSwapTool.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(HotSwapTool.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchFieldException ex) {
            Logger.getLogger(HotSwapTool.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(HotSwapTool.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void executeSuspended(final Runnable r) {

        assert isConnected();

        Thread backup = new Thread(new Runnable() {

            public void run() {
                List<ThreadReference> threadsToSuspend = new ArrayList<ThreadReference>();

                for (ThreadReference t : vm.allThreads()) {
                    if (t.name().equals("main")) {
                        threadsToSuspend.add(t);
                    }
                }

                for (ThreadReference t : threadsToSuspend) {
                    t.suspend();
                }
                r.run();
                for (ThreadReference t : threadsToSuspend) {
                    t.resume();
                }
            }
        });
        backup.start();
        try {
            backup.join();
        } catch (InterruptedException ex) {
            ex.getStackTrace();
        }
    }

    List<ReferenceType> allClasses() {
        // System.out.println("enter all classes");
        List<ReferenceType> result = vm.allClasses();
        //System.out.println("exit all classes");
        return result;
    }
}
