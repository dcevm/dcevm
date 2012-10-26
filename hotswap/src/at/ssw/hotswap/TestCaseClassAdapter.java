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

import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AnnotationNode;

/**
 *
 * @author Thomas Wuerthinger
 */
public class TestCaseClassAdapter extends ClassAdapter {

    public String name;

    private TypeNameAdapter adapter;

    private Set<InnerClassEntry> innerClassEntries = new HashSet<InnerClassEntry>();

    private static class InnerClassEntry {
        String name;
        String outerName;
        String innerName;
        int access;

        public InnerClassEntry(String name, String outerName, String innerName, int access) {
            this.name = name;
            this.outerName = outerName;
            this.innerName = innerName;
            this.access = access;
        }

        @Override
        public int hashCode() {
            return ((name == null) ? 0 : name.hashCode()) * 27 + ((outerName == null) ? 0 : outerName.hashCode()) * 13 + ((innerName == null) ? 0 : innerName.hashCode()) * 7 + access;
        }

        @Override
        public boolean equals(Object o) {
            if (o != null && o instanceof InnerClassEntry) {
                final InnerClassEntry other = (InnerClassEntry)o;
                return equals(name, other.name) && equals(outerName, other.outerName) && equals(innerName, other.innerName) && access == other.access;
            }

            return false;
        }

        private boolean equals(Object o1, Object o2) {
            if (o1 == null) return o2 == null;
            if (o2 == null) return o1 == null;
            return o1.equals(o2);
        }
    }

    public TestCaseClassAdapter(ClassVisitor cv, TypeNameAdapter adapter) {
        super(cv);
        this.adapter = adapter;
    }

    private static String[] adapt(TypeNameAdapter adapter, String[] values) {
        if (values == null) return null;
        final String[] result = new String[values.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = adapter.adapt(values[i]);
        }
        return result;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.name = adapter.adapt(name);

        if (this.name.equals("java/lang/Object")) {
            superName = null;
        }
        
        cv.visit(version, access, this.name, adapter.adapt(signature), adapter.adapt(superName), adapt(adapter, interfaces));
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        return cv.visitField(access, name, desc, adapter.adapt(signature), value);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {

        // Inner class entries may only occur once.
        InnerClassEntry entry = new InnerClassEntry(adapter.adapt(name), adapter.adapt(outerName), adapter.adapt(innerName), access);
        if (innerClassEntries.contains(entry)) {
            return;
        }
        innerClassEntries.add(entry);

        cv.visitInnerClass(entry.name, entry.outerName, entry.innerName, entry.access);
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
        cv.visitOuterClass(adapter.adapt(owner), adapter.adapt(name), desc);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {

        if (name.endsWith("___")) {
            name = name.substring(0, name.length() - 3);
        }

        MethodVisitor mv = cv.visitMethod(access, name, adapter.adapt(desc), adapter.adapt(signature), adapt(adapter, exceptions));
        if (mv != null) {
            mv = new MethodAdapter(mv) {

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                    if (name.equals("<init>") && TestCaseClassAdapter.this.name.equals("java/lang/Object") && owner.equals("java/lang/Object")) {
                        return;
                    }

                    if (name.endsWith("___")) {
                        name = name.substring(0, name.length() - 3);
                    }
                    
                    mv.visitMethodInsn(opcode, adapter.adapt(owner), name, adapter.adapt(desc));
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    super.visitFieldInsn(opcode, adapter.adapt(owner), name, adapter.adapt(desc));
                }
                
                @Override
                public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                    super.visitLocalVariable(name, adapter.adapt(desc), adapter.adapt(signature), start, end, index);
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    super.visitTypeInsn(opcode, adapter.adapt(type));
                }

                @Override
                public void visitMultiANewArrayInsn(String desc, int dims) {
                    super.visitMultiANewArrayInsn(adapter.adapt(desc), dims);
                }

                @Override
                public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                    super.visitTryCatchBlock(start, end, handler, adapter.adapt(type));
                }

                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    return super.visitAnnotation(adapter.adapt(desc), visible);
                }
            };
        }

        return mv;
    }

    
    @Override
    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {

        return new AnnotationNode(adapter.adapt(desc)) {

            @Override
            public void visitEnum(String name, String enumDesc, String value) {


                class SingleByteAttribute extends Attribute {
                    private byte value;

                    public SingleByteAttribute(String name, byte value) {
                        super(name);
                        this.value = value;
                    }

                    @Override
                    protected ByteVector write(ClassWriter writer, byte[] code, int len, int maxStack, int maxLocals) {
                        return new ByteVector().putByte(value);
                    }

                }
                
                if (HotSwapTool.decodeDescriptor(enumDesc).equals(RedefinitionPolicy.class.getName())) {
                    RedefinitionPolicy valueAsEnum = RedefinitionPolicy.valueOf(value);
                    if (HotSwapTool.decodeDescriptor(desc).equals(FieldRedefinitionPolicy.class.getName())) {
                        cv.visitAttribute(new SingleByteAttribute(FieldRedefinitionPolicy.class.getSimpleName(), (byte) valueAsEnum.ordinal()));
                    }
                    if (HotSwapTool.decodeDescriptor(desc).equals(MethodRedefinitionPolicy.class.getName())) {
                        cv.visitAttribute(new SingleByteAttribute(MethodRedefinitionPolicy.class.getSimpleName(), (byte) valueAsEnum.ordinal()));
                    }
                }

                super.visitEnum(name, desc, value);
            }

            @Override
            public void visitEnd() {
                accept(cv.visitAnnotation(desc, visible));
            }
        };

    }
}
