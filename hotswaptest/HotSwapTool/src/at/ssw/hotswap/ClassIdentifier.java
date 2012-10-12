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

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 */
public class ClassIdentifier {

    private String methodSignature = null;
    private String typeSignature = null;
    private String slashedTypeName = null;
    private String dottedTypeName = null;

    public ClassIdentifier(String name) {
        if (name != null) {
            if (name.indexOf('(') >= 0) {
                this.methodSignature = name;
            } else if (name.endsWith(";")) {
                this.typeSignature = name;
            } else if (name.indexOf('.') >= 0) {
                this.dottedTypeName = name;
            } else {
                this.slashedTypeName = name;
            }
        }
    }

    public String getOriginal() {
        if (this.typeSignature != null) {
            return this.typeSignature;
        }
        if (this.methodSignature != null) {
            return this.methodSignature;
        }
        if (this.dottedTypeName != null) {
            return this.dottedTypeName;
        }
        return this.slashedTypeName;
    }

    public String getDescriptor() {
        if (this.dottedTypeName != null) {
            return this.dottedTypeName;
        }
        if (this.typeSignature != null) {
            return this.typeSignature.substring(1, this.typeSignature.length() - 1).replace('/', '.');
        }
        if (this.methodSignature != null) {
            return this.methodSignature.substring(1).replace('/', '.');
        }
        if (this.slashedTypeName != null) {
            return this.slashedTypeName.replace('/', '.');
        }

        return null;
    }

    public void setDescriptor(String ts) {

        if (this.dottedTypeName != null) {
            this.dottedTypeName = ts;
        } else if (this.typeSignature != null) {
            this.typeSignature = "L" + ts.replace('.', '/') + ";";
        } else if (this.methodSignature != null) {
            this.methodSignature = "(" + ts.replace('.', '/');
        } else if (this.slashedTypeName != null) {
            this.slashedTypeName = ts.replace('.', '/');
        } else {
            assert false : "something went wrong in ClassIdentifier";
        }
    }
}
