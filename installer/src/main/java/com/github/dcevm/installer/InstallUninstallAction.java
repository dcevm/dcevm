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
 */
class InstallUninstallAction extends AbstractAction implements ListSelectionListener, Observer {

    private final JTable table;
    private final boolean install;
    private Installation installation;

    public InstallUninstallAction(boolean install, JTable t) {
        super(install ? "Install" : "Uninstall");
        this.install = install;
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
            if (install) {
                getSelectedInstallation().installDCE();
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
        if (install) {
            setEnabled(installation != null && !installation.isDCEInstalled());
        } else {
            setEnabled(installation != null && installation.isDCEInstalled());
        }
    }

    public void update(Observable o, Object arg) {
        update();
    }
}
