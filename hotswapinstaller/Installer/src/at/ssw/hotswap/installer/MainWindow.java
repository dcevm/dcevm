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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class MainWindow extends JFrame {

    private final InstallationsTableModel installations;
    private final Installer installer;
    private JTable table;

    public MainWindow(Installer inst) {
        super("Dynamic Code Evolution VM Installer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        installer = inst;
        installations = new InstallationsTableModel();
        for (Installation i : installer.listAllInstallations()) {
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

    static void showInstallerException(InstallerException ex, Component parent) {
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
        } catch (IOException ex) {
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
        licenseText.append("This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License version 2 only, as published by the Free Software Foundation.\n\nThis code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License version 2 for more details (a copy is included in the LICENSE file that accompanied this code).\n\nYou should have received a copy of the GNU General Public License version 2 along with this work; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.");
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
        p.add(l, BorderLayout.WEST);

        table = new JTable(installations);
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setColumnSelectionAllowed(false);
        table.setDefaultRenderer(Object.class, new InstallationTableCellRenderer());
        table.getColumnModel().getColumn(0).setHeaderValue("Directory");
        table.getColumnModel().getColumn(0).setPreferredWidth(300);
        table.getColumnModel().getColumn(1).setHeaderValue("Java Version");
        table.getColumnModel().getColumn(2).setHeaderValue("Type");
        table.getColumnModel().getColumn(3).setHeaderValue("DCE");
        JScrollPane lists = new JScrollPane(table);
        lists.setPreferredSize(new Dimension(200, 200));
        p.add(lists, BorderLayout.CENTER);

        return p;
    }

    private JComponent getBottomPanel() {

        JPanel left = new JPanel(new FlowLayout());
        left.add(new JButton(new AddDirectoryAction(this, installations, installer)));

        JPanel right = new JPanel(new FlowLayout());
        right.add(new JButton(new TestAction(table, installer)));
        right.add(new JButton(new InstallUninstallAction(false, table)));
        right.add(new JButton(new InstallUninstallAction(true, table)));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        bottom.add(left, BorderLayout.WEST);
        bottom.add(right, BorderLayout.EAST);

        return bottom;
    }
}

class AddDirectoryAction extends AbstractAction {

    private final Component parent;
    private final InstallationsTableModel installations;
    private final Installer installer;

    public AddDirectoryAction(Component parent, InstallationsTableModel inst, Installer installer) {
        super("Add installation directory...");
        this.parent = parent;
        installations = inst;
        this.installer = installer;
    }

    public void actionPerformed(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select a Java installation directory...");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        Preferences p = Preferences.userNodeForPackage(Installer.class);
        final String prefID = "defaultDirectory";
        fc.setCurrentDirectory(new File(p.get(prefID, ".")));

        if (fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {

            File dir = fc.getSelectedFile();
            p.put(prefID, dir.getParent());
            try {
                installations.add(new Installation(dir, installer));
            } catch (InstallerException ex) {
                MainWindow.showInstallerException(ex, parent);
            }
        }
    }
}

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
        } catch (InstallerException ex) {
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

class TestAction extends AbstractAction implements ListSelectionListener, Observer {

    private final JTable table;
    private Installation installation;
    private final Installer installer;

    public TestAction(JTable t, Installer i) {
        super("Test Installation");
        setEnabled(false);
        table = t;
        installer = i;
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
        File p = installation.getPath();
        if(installation.isJDK()) p = new File(p, "jre");

        String result = "";
        try {
            String agentparam = "-javaagent:" + p.getAbsolutePath() + "/lib/ext/dcevm.jar";
            result = installer.executeJava(p, agentparam, "at.ssw.mixin.fasttest.FastTest");
        } catch (InstallerException ex) {
            MainWindow.showInstallerException(ex, table);
        }

        if(result.length()>0) {
            String msg = "Tests failed:\n" + result;
            JOptionPane.showMessageDialog(table.getTopLevelAncestor(), msg, "Error", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(table.getTopLevelAncestor(), "All tests succeeded.", "Information", JOptionPane.INFORMATION_MESSAGE);
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
        setEnabled(installation != null && installation.isDCEInstalled());
    }

    public void update(Observable o, Object arg) {
        update();
    }
}

class InstallationsTableModel extends AbstractTableModel implements Observer {

    private final ArrayList<Installation> installations;

    public InstallationsTableModel() {
        installations = new ArrayList<Installation>();
    }

    public int getRowCount() {
        return installations.size();
    }

    public int getColumnCount() {
        return 4;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        return installations.get(rowIndex);
    }

    public Installation getInstallationAt(int row) {
        return installations.get(row);
    }

    public void add(Installation i) {
        installations.add(i);
        i.addObserver(this);
        fireTableDataChanged();
    }

    public void update(Observable o, Object arg) {
        int row = installations.indexOf(o);
        fireTableRowsUpdated(row, row);
    }
}

class InstallationTableCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (c instanceof JLabel && value instanceof Installation) {
            JLabel l = (JLabel) c;
            Installation i = (Installation) value;

            switch (column) {
                case 0:
                    l.setText(i.getPath().getAbsolutePath());
                    break;
                case 1:
                    l.setText(i.getVersion());
                    break;
                case 2:
                    l.setText(i.isJDK() ? "JDK" : "JRE");
                    if (i.is64Bit()) {
                        l.setText(l.getText() + " (64 Bit)");
                    }
                    break;
                case 3:
                    if (i.isDCEInstalled()) {
                        l.setText("Yes (" + i.getDCEVersion() + ")");
                    } else {
                        l.setText("No");
                    }
                    break;
            }
        }

        return c;
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        setToolTipText(text);
    }
}
