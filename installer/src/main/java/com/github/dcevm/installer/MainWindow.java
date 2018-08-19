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

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 * @author Ivan Dubrov
 * @author Przemysław Rumik
 */
public class MainWindow extends JFrame {

    private final InstallationsTableModel installations;
    private JTable table;

    public MainWindow() {
        super("Dynamic Code Evolution VM Installer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Installer installer = new Installer(ConfigurationInfo.current());
        installations = new InstallationsTableModel();
        for (Installation i : installer.listInstallations()) {
            installations.add(i);
        }
        add(getBanner(), BorderLayout.NORTH);
        add(getCenterPanel(), BorderLayout.CENTER);
        add(getBottomPanel(), BorderLayout.SOUTH);

        if (table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }

        pack();
        setMinimumSize(getSize());
    }

    static void showInstallerException(Exception ex, Component parent) {
        Throwable e = ex;
        String error = e.getMessage();
        e = e.getCause();

        while (e != null) {
            error += "\n" + e.getMessage();
            e = e.getCause();
        }

        ex.printStackTrace();

        error += "\nPlease ensure that no other Java applications are running and you have sufficient permissions.";
        JOptionPane.showMessageDialog(parent, error, ex.getMessage(), JOptionPane.ERROR_MESSAGE);
    }

    private JComponent getBanner() {
        try {
            BufferedImage img = ImageIO.read(getClass().getResource("splash.png"));
            JLabel title = new JLabel(new ImageIcon(img));
            title.setPreferredSize(new Dimension(img.getWidth() + 10, img.getHeight()));
            title.setOpaque(true);
            title.setBackground(new Color(238, 238, 255));
            return title;
        } catch (Exception ignore) {
        }
        return new JLabel();
    }

    private JComponent getCenterPanel() {
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        center.setBackground(Color.WHITE);
        JTextArea license = new javax.swing.JTextArea();
        license.setLineWrap(true);
        license.setWrapStyleWord(true);
        license.setEditable(false);
        license.setFont(license.getFont().deriveFont(11.0f));
        StringBuilder licenseText = new StringBuilder();
        licenseText.append("Enhance current Java (JRE/JDK) installations with DCEVM (http://github.com/dcevm/dcevm).");
        licenseText.append("\n\nYou can either replace current Java VM or install DCEVM as alternative JVM (run with -XXaltjvm=dcevm command-line option).");
        licenseText.append("\nInstallation as alternative JVM is preferred, it gives you more control where you will use DCEVM.\nWhy this is important? Because DCEVM forces your JVM to use only one GC algorithm, and this may cause performance penalty.");
        licenseText.append("\n\n\nThis program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License version 2 only, as published by the Free Software Foundation.\n\nThis code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License version 2 for more details (a copy is included in the LICENSE file that accompanied this code).\n\nYou should have received a copy of the GNU General Public License version 2 along with this work; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.");
        licenseText.append("\n\n\nASM LICENSE TEXT:\nCopyright (c) 2000-2005 INRIA, France Telecom\nAll rights reserved.\n\nRedistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:\n\n1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.\n\n2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.\n\n3. Neither the name of the copyright holders nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.\n\nTHIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.");
        license.setText(licenseText.toString());
        JScrollPane licenses = new JScrollPane(license, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        licenses.setPreferredSize(new Dimension(150, 150));
        center.add(licenses, BorderLayout.NORTH);
        center.add(getChooserPanel(), BorderLayout.CENTER);
        return center;
    }

    private JComponent getChooserPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        p.setOpaque(false);

        JLabel l = new JLabel("Please choose installation directory:");
        l.setVerticalAlignment(JLabel.NORTH);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        p.add(l, BorderLayout.NORTH);

        table = new JTable(installations);
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setColumnSelectionAllowed(false);
        table.setDefaultRenderer(Object.class, new InstallationTableCellRenderer());
        table.getColumnModel().getColumn(0).setHeaderValue("Directory");
        table.getColumnModel().getColumn(0).setPreferredWidth(300);
        table.getColumnModel().getColumn(1).setHeaderValue("Java Version");
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setHeaderValue("Type");
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setHeaderValue("Replaced by DCEVM?");
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.getColumnModel().getColumn(4).setHeaderValue("Installed altjvm?");
        table.getColumnModel().getColumn(4).setPreferredWidth(200);
        JScrollPane lists = new JScrollPane(table);
        lists.setPreferredSize(new Dimension(900, 200));
        p.add(lists, BorderLayout.CENTER);

        return p;
    }

    private JComponent getBottomPanel() {

        JPanel left = new JPanel(new FlowLayout());
        left.add(new JButton(new AddDirectoryAction(this, installations, ConfigurationInfo.current())));

        JPanel right = new JPanel(new FlowLayout());
        //right.add(new JButton(new TestAction(table, installer)));
        right.add(new JButton(new InstallUninstallAction(InstallUninstallAction.Type.UNINSTALL, table)));
        right.add(new JButton(new InstallUninstallAction(InstallUninstallAction.Type.INSTALL, table)));
        right.add(new JButton(new InstallUninstallAction(InstallUninstallAction.Type.INSTALL_ALTJVM, table)));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        bottom.add(left, BorderLayout.WEST);
        bottom.add(right, BorderLayout.EAST);

        return bottom;
    }
}

