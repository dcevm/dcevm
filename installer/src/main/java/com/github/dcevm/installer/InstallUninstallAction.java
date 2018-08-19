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
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 * @author Ivan Dubrov
 * @author Jiri Bubnik
 */
class InstallUninstallAction extends AbstractAction implements ListSelectionListener, Observer {

    /**
     * Buttons to add/remove DCEVM.
     */
    public enum Type {
        UNINSTALL("Uninstall"),
        INSTALL("Replace by DCEVM"),
        INSTALL_ALTJVM("Install DCEVM as altjvm");

        String label;

        Type(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final JTable table;
    private final Type type;
    private Installation installation;

    public InstallUninstallAction(Type type, JTable t) {
        super(type.getLabel());
        this.type = type;
        setEnabled(false);
        table = t;
        t.getSelectionModel().addListSelectionListener(this);
    }

    private Installation getSelectedInstallation() {
        InstallationsTableModel itm = (InstallationsTableModel) table.getModel();
        int sel = table.getSelectedRow();
        if (sel < 0) {
            return null;
        } else {
            return itm.getInstallationAt(sel);
        }
    }

    public void actionPerformed(ActionEvent e) {
        try {
            if (type.equals(Type.INSTALL)) {
                getSelectedInstallation().installDCE(false);
            }
            else if (type.equals(Type.INSTALL_ALTJVM)) {
                getSelectedInstallation().installDCE(true);
            } else {
                getSelectedInstallation().uninstallDCE();
            }
        } catch (IOException ex) {
            MainWindow.showInstallerException(ex, table);
        }
    }

    private void setCurrentInstallation(Installation i) {
        if (installation != null) {
            installation.deleteObserver(this);
        }
        installation = i;
        if (installation != null) {
            installation.addObserver(this);
        }
        update();
    }

    public void valueChanged(ListSelectionEvent e) {
        Installation i = getSelectedInstallation();
        setCurrentInstallation(i);
    }

    private void update() {
        if (type.equals(Type.INSTALL)) {
            setEnabled(installation != null && !installation.isDCEInstalled());
        } else if (type.equals(Type.INSTALL_ALTJVM)) {
            setEnabled(installation != null && !installation.isDCEInstalledAltjvm());
        } else {
            setEnabled(installation != null && (installation.isDCEInstalled() || installation.isDCEInstalledAltjvm()));
        }
    }

    public void update(Observable o, Object arg) {
        update();
    }
}
