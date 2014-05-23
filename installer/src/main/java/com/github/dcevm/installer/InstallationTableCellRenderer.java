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
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 * @author Ivan Dubrov
 */
class InstallationTableCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (c instanceof JLabel && value instanceof Installation) {
            JLabel l = (JLabel) c;
            Installation inst = (Installation) value;

            switch (column) {
                case 0:
                    l.setText(inst.getPath().toAbsolutePath().toString());
                    break;
                case 1:
                    l.setText(inst.getVersion());
                    break;
                case 2:
                    l.setText(inst.isJDK() ? "JDK" : "JRE");
                    if (inst.is64Bit()) {
                        l.setText(l.getText() + " (64 Bit)");
                    }
                    break;
                case 3:
                    if (inst.isDCEInstalled()) {
                        l.setText("Yes (" + inst.getVersionDcevm() + ")");
                    } else {
                        l.setText("No");
                    }
                    break;
                case 4:
                    if (inst.isDCEInstalledAltjvm()) {
                        l.setText("Yes (" + inst.getVersionDcevmAltjvm() + ")");
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
