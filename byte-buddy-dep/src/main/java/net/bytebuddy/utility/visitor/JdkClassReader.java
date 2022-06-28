package net.bytebuddy.utility.visitor;

import jdk.classfile.*;
import jdk.classfile.attribute.StackMapTableAttribute;
import jdk.classfile.constantpool.ClassEntry;
import jdk.classfile.instruction.*;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.constant.*;
import java.util.HashMap;
import java.util.Map;

public class JdkClassReader {

    private final ClassModel classModel;

    public JdkClassReader(ClassModel classModel) {
        this.classModel = classModel;
    }

    public void accept(ClassVisitor classVisitor) {
        Map<Label, org.objectweb.asm.Label> labels = new HashMap<>();
        classVisitor.visit(classModel.minorVersion() << 16 | classModel.majorVersion(),
                classModel.flags().flagsMask(),
                classModel.thisClass().asInternalName(),
                null,
                classModel.superclass().map(ClassEntry::asInternalName).orElse(null),
                classModel.interfaces().stream().map(ClassEntry::asInternalName).toArray(String[]::new));
        classModel.attributes().forEach(attribute -> {

        });
        for (FieldModel fieldModel : classModel.fields()) {
            FieldVisitor fieldVisitor = classVisitor.visitField(fieldModel.flags().flagsMask(),
                    fieldModel.fieldName().stringValue(),
                    fieldModel.descriptorSymbol().descriptorString(),
                    null,
                    null);
            if (fieldVisitor != null) {
                fieldVisitor.visitEnd();
            }
        }
        for (MethodModel methodModel : classModel.methods()) {
            MethodVisitor methodVisitor = classVisitor.visitMethod(methodModel.flags().flagsMask(),
                    methodModel.methodName().stringValue(),
                    methodModel.descriptorSymbol().descriptorString(),
                    null,
                    null);
            if (methodVisitor != null) {
                Map<Integer, StackMapTableAttribute.StackMapFrame> frames = new HashMap<>();
                methodModel.attributes().forEach(attribute -> {
                    if (attribute instanceof StackMapTableAttribute) {
                        ((StackMapTableAttribute) attribute).entries().forEach(frame -> {
                            frames.put(frame.absoluteOffset(), frame);
                        });
                    }
                });
                methodModel.code().ifPresent(code -> {
                    methodVisitor.visitCode();
                    int offset = 0;
                    for (CodeElement codeElement : code) {
                        StackMapTableAttribute.StackMapFrame frame = frames.remove(offset);
                        if (frame != null) {
                            methodVisitor.visitFrame(frame.frameType(),
                                    frame.declaredLocals().size(),
                                    frame.declaredLocals().stream().map(info -> info.type()).toArray(),
                                    frame.declaredStack().size(),
                                    frame.declaredStack().stream().map(info -> info.type()).toArray());
                        }
                        offset += codeElement.sizeInBytes();
                        if (codeElement instanceof MonitorInstruction) {
                            methodVisitor.visitInsn(codeElement.opcode().bytecode());
                        } else if (codeElement instanceof TypeCheckInstruction) {
                            methodVisitor.visitTypeInsn(codeElement.opcode().bytecode(), (((TypeCheckInstruction) codeElement).type().asInternalName());
                        } else if (codeElement instanceof LoadInstruction) {
                            methodVisitor.visitVarInsn(codeElement.opcode().bytecode(), ((LoadInstruction) codeElement).slot());
                        } else if (codeElement instanceof OperatorInstruction) {
                            methodVisitor.visitInsn(codeElement.opcode().bytecode());
                        } else if (codeElement instanceof ReturnInstruction) {
                            methodVisitor.visitInsn(codeElement.opcode().bytecode());
                        } else if (codeElement instanceof InvokeInstruction) {
                            methodVisitor.visitMethodInsn(codeElement.opcode().bytecode(),
                                    ((InvokeInstruction) codeElement).owner().asInternalName(),
                                    ((InvokeInstruction) codeElement).name().stringValue(),
                                    ((InvokeInstruction) codeElement).type().stringValue(),
                                    ((InvokeInstruction) codeElement).isInterface());
                        } else if (codeElement instanceof IncrementInstruction) {
                            methodVisitor.visitIincInsn(((IncrementInstruction) codeElement).slot(), ((IncrementInstruction) codeElement).constant());
                        } else if (codeElement instanceof FieldInstruction) {
                            methodVisitor.visitFieldInsn(codeElement.opcode().bytecode(),
                                    ((FieldInstruction) codeElement).owner().asInternalName(),
                                    ((FieldInstruction) codeElement).name().stringValue(),
                                    ((FieldInstruction) codeElement).type().stringValue());
                        } else if (codeElement instanceof InvokeDynamicInstruction) {
                            throw new UnsupportedOperationException(); // TODO: later
                        } else if (codeElement instanceof BranchInstruction) {
                            methodVisitor.visitJumpInsn(codeElement.opcode().bytecode(), labels.computeIfAbsent(((BranchInstruction) codeElement).target(), label -> new org.objectweb.asm.Label()));
                        } else if (codeElement instanceof StoreInstruction) {
                            methodVisitor.visitVarInsn(codeElement.opcode().bytecode(), ((StoreInstruction) codeElement).slot());
                        } else if (codeElement instanceof NewReferenceArrayInstruction) {
                            methodVisitor.visitTypeInsn(codeElement.opcode().bytecode(), ((NewReferenceArrayInstruction) codeElement).componentType().asInternalName());
                        } else if (codeElement instanceof LookupSwitchInstruction) {
                            methodVisitor.visitLookupSwitchInsn(labels.computeIfAbsent(((LookupSwitchInstruction) codeElement).defaultTarget(), label -> new org.objectweb.asm.Label()),
                                    ((LookupSwitchInstruction) codeElement).cases().stream().mapToInt(SwitchCase::caseValue).toArray(),
                                    ((LookupSwitchInstruction) codeElement).cases().stream().map(c -> labels.computeIfAbsent(c.target(), label -> new org.objectweb.asm.Label())).toArray(org.objectweb.asm.Label[]::new));
                        } else if (codeElement instanceof TableSwitchInstruction) {
                            methodVisitor.visitTableSwitchInsn(((TableSwitchInstruction) codeElement).lowValue(),
                                    ((TableSwitchInstruction) codeElement).highValue(),
                                    labels.computeIfAbsent(((TableSwitchInstruction) codeElement).defaultTarget(), label -> new org.objectweb.asm.Label()),
                                    ((TableSwitchInstruction) codeElement).cases().stream().map(c -> labels.computeIfAbsent(c.target(), label -> new org.objectweb.asm.Label())).toArray(org.objectweb.asm.Label[]::new));
                        } else if (codeElement instanceof ArrayStoreInstruction) {
                            methodVisitor.visitInsn(codeElement.opcode().bytecode());
                        } else if (codeElement instanceof ArrayLoadInstruction) {
                            methodVisitor.visitInsn(codeElement.opcode().bytecode());
                        } else if (codeElement instanceof ConstantInstruction) {
                            ConstantDesc constant = ((ConstantInstruction) codeElement).constantValue();
                            if (constant instanceof DynamicConstantDesc<?>) {
                                throw new UnsupportedOperationException();
                            } else if (constant instanceof MethodHandleDesc) {
                                throw new UnsupportedOperationException();
                            } else if (constant instanceof MethodTypeDesc) {
                                throw new UnsupportedOperationException();
                            } else if (constant instanceof ClassDesc) {
                                methodVisitor.visitLdcInsn(Type.getType(((ClassDesc) constant).descriptorString()));
                            } else {
                                methodVisitor.visitLdcInsn(constant);
                            }
                        } else if (codeElement instanceof StackInstruction) {
                            methodVisitor.visitInsn(codeElement.opcode().bytecode());
                        } else if (codeElement instanceof NopInstruction) {
                            methodVisitor.visitInsn(codeElement.opcode().bytecode());
                        } else if (codeElement instanceof ThrowInstruction) {
                            methodVisitor.visitInsn(codeElement.opcode().bytecode());
                        } else if (codeElement instanceof NewObjectInstruction) {
                            methodVisitor.visitTypeInsn(codeElement.opcode().bytecode(), ((NewObjectInstruction) codeElement).className().asInternalName());
                        } else if (codeElement instanceof ConvertInstruction) {
                            methodVisitor.visitInsn(codeElement.opcode().bytecode());
                        } else if (codeElement instanceof NewMultiArrayInstruction) {
                            methodVisitor.visitMultiANewArrayInsn(((NewMultiArrayInstruction) codeElement).arrayType().asInternalName(), ((NewMultiArrayInstruction) codeElement).dimensions());
                        } else if (codeElement instanceof NewPrimitiveArrayInstruction) {
                            methodVisitor.visitInsn(codeElement.opcode().bytecode());
                        } else {
                            // TODO
                        }
                    }
                    methodVisitor.visitMaxs(code.maxStack(), code.maxLocals());
                });
                methodVisitor.visitEnd();
            }
        }
    }
}
