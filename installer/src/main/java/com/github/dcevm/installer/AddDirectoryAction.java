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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.prefs.Preferences;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 * @author Ivan Dubrov
 */
class AddDirectoryAction extends AbstractAction {

    private final Component parent;
    private final InstallationsTableModel installations;
    private final ConfigurationInfo config;

    public AddDirectoryAction(Component parent, InstallationsTableModel inst, ConfigurationInfo config) {
        super("Add installation directory...");
        this.parent = parent;
        this.installations = inst;
        this.config = config;
    }

    public void actionPerformed(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        fc.setFileHidingEnabled(false);
        fc.setDialogTitle("Select a Java installation directory...");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        Preferences p = Preferences.userNodeForPackage(Installer.class);
        final String prefID = "defaultDirectory";
        fc.setCurrentDirectory(new File(p.get(prefID, ".")));

        if (fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {

            Path dir = fc.getSelectedFile().toPath();
            p.put(prefID, dir.getParent().toString());
            try {
                installations.add(new Installation(config, dir));
            } catch (IOException ex) {
                MainWindow.showInstallerException(ex, parent);
            }
        }
    }
}
