package me.sodiumlegacy.mod.client.render.vertex.serializers.generated;

import me.sodiumlegacy.mod.api.vertex.attributes.CommonVertexAttribute;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatDescription;
import me.sodiumlegacy.mod.api.vertex.serializer.VertexSerializer;
import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;
import org.objectweb.asm.*;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class VertexSerializerFactory {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public static Bytecode generate(VertexFormatDescription srcFormat, VertexFormatDescription dstFormat, String identifier) {
        List<MemoryTransfer> memoryCopies = createMemoryTransferList(srcFormat, dstFormat);

        String name = "me/sodiumlegacy/mod/client/render/vertex/serializers/generated/VertexSerializer$Impl$" + identifier;

        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V1_8, Opcodes.ACC_FINAL | Opcodes.ACC_PUBLIC, name, null,
                Type.getInternalName(Object.class),
                new String[] { Type.getInternalName(VertexSerializer.class) });

        {
            // Local variable table slots
            final int localThis = 0;

            // Constructor method
            MethodVisitor methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();

            // Call the super class constructor
            Label labelInit = new Label();
            methodVisitor.visitLabel(labelInit);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, localThis);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "java/lang/Object", "<init>", "()V", false);

            // Exit the function
            Label labelExit = new Label();
            methodVisitor.visitLabel(labelExit);
            methodVisitor.visitInsn(Opcodes.RETURN);

            // Describe the local variable table
            Label labelEnd = new Label();
            methodVisitor.visitLabel(labelEnd);
            methodVisitor.visitLocalVariable("this",
                    "L" + name + ";", null, labelInit, labelEnd, localThis);
            methodVisitor.visitMaxs(2, 1);
            methodVisitor.visitEnd();
        }

        {
            // Local variable table slots
            final int localThis = 0;
            final int localSrcPointer = 1;
            final int localDstPointer = 3;
            final int localVertexCount = 5;
            final int localVertexIndex = 6;

            // Serialization method
            MethodVisitor methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "serialize", "(JJI)V", null, null);
            methodVisitor.visitCode();

            // Set up the loop's accumulator (vertexIndex)
            Label labelLoopInit = new Label();
            methodVisitor.visitLabel(labelLoopInit);
            methodVisitor.visitInsn(Opcodes.ICONST_0);
            methodVisitor.visitVarInsn(Opcodes.ISTORE, localVertexIndex);

            // Set up the loop's operands for a comparison (vertexIndex, vertexStart)
            Label labelLoopConditionSetup = new Label();
            methodVisitor.visitLabel(labelLoopConditionSetup);
            methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[]{Opcodes.INTEGER}, 0, null);
            methodVisitor.visitVarInsn(Opcodes.ILOAD, localVertexIndex);
            methodVisitor.visitVarInsn(Opcodes.ILOAD, localVertexCount);

            // Check the loop's condition (vertexIndex < vertexCount)
            Label labelLoopConditionComparison = new Label();
            methodVisitor.visitJumpInsn(Opcodes.IF_ICMPGE, labelLoopConditionComparison);

            // Generate instructions for each copy command
            for (MemoryTransfer op : memoryCopies) {
                int i = 0;

                while (i < op.length()) {
                    int remaining = op.length() - i;

                    Label labelMemoryTransfer = new Label();
                    methodVisitor.visitLabel(labelMemoryTransfer);

                    // Calculate the destination pointer
                    methodVisitor.visitVarInsn(Opcodes.LLOAD, localDstPointer);
                    methodVisitor.visitLdcInsn((long) (op.dst() + i));
                    methodVisitor.visitInsn(Opcodes.LADD);

                    // Calculate the source pointer
                    methodVisitor.visitVarInsn(Opcodes.LLOAD, localSrcPointer);
                    methodVisitor.visitLdcInsn((long) (op.src() + i));
                    methodVisitor.visitInsn(Opcodes.LADD);

                    if (remaining >= 8) {
                        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MemoryUtil.class), "memGetLong", "(J)J", false);
                        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MemoryUtil.class), "memPutLong", "(JJ)V", false);

                        i += 8;
                    } else if (remaining >= 4) {
                        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MemoryUtil.class), "memGetInt", "(J)I", false);
                        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MemoryUtil.class), "memPutInt", "(JI)V", false);

                        i += 4;
                    } else if (remaining >= 2) {
                        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MemoryUtil.class), "memGetShort", "(J)S", false);
                        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MemoryUtil.class), "memPutShort", "(JS)V", false);

                        i += 2;
                    } else {
                        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MemoryUtil.class), "memGetByte", "(J)B", false);
                        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MemoryUtil.class), "memPutByte", "(JB)V", false);

                        i += 1;
                    }
                }
            }

            // Increment source pointer
            Label labelIncrementSourcePointer = new Label();
            methodVisitor.visitLabel(labelIncrementSourcePointer);
            methodVisitor.visitVarInsn(Opcodes.LLOAD, localSrcPointer);
            methodVisitor.visitLdcInsn((long) srcFormat.stride());
            methodVisitor.visitInsn(Opcodes.LADD);
            methodVisitor.visitVarInsn(Opcodes.LSTORE, localSrcPointer);

            // Increment destination pointer
            Label labelIncrementDestinationPointer = new Label();
            methodVisitor.visitLabel(labelIncrementDestinationPointer);
            methodVisitor.visitVarInsn(Opcodes.LLOAD, localDstPointer);
            methodVisitor.visitLdcInsn((long) dstFormat.stride());
            methodVisitor.visitInsn(Opcodes.LADD);
            methodVisitor.visitVarInsn(Opcodes.LSTORE, localDstPointer);

            // Restart the loop if the condition still holds, otherwise exit
            Label labelRestartLoop = new Label();
            methodVisitor.visitLabel(labelRestartLoop);
            methodVisitor.visitIincInsn(localVertexIndex, 1);
            methodVisitor.visitJumpInsn(Opcodes.GOTO, labelLoopConditionSetup);
            methodVisitor.visitLabel(labelLoopConditionComparison);
            methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
            methodVisitor.visitInsn(Opcodes.RETURN);

            // Describe the local variable table
            Label labelExit = new Label();
            methodVisitor.visitLabel(labelExit);
            methodVisitor.visitLocalVariable("this", "L" + name + ";", null, labelLoopInit, labelExit, localThis);
            methodVisitor.visitLocalVariable("src", "J", null, labelLoopInit, labelExit, localSrcPointer);
            methodVisitor.visitLocalVariable("dst", "J", null, labelLoopInit, labelExit, localDstPointer);
            methodVisitor.visitLocalVariable("vertexCount", "I", null, labelLoopInit, labelExit, localVertexCount);
            methodVisitor.visitLocalVariable("vertexIndex", "I", null, labelLoopConditionSetup, labelLoopConditionComparison, localVertexIndex);
            methodVisitor.visitMaxs(6, 7);
            methodVisitor.visitEnd();
        }

        classWriter.visitEnd();

        return new Bytecode(classWriter.toByteArray());
    }

    private static List<MemoryTransfer> createMemoryTransferList(VertexFormatDescription srcVertexFormat, VertexFormatDescription dstVertexFormat) {
        ArrayList<MemoryTransfer> ops = new ArrayList<>();

        for (CommonVertexAttribute elementType : CommonVertexAttribute.values()) {
            // Check if we need to transfer the element into the destination format
            if (!dstVertexFormat.containsElement(elementType)) {
                continue;
            }

            // If the destination format has the element, then the source format needs to have it as well
            if (!srcVertexFormat.containsElement(elementType)) {
                throw new RuntimeException(String.format("Source format is missing element %s as required by destination format", elementType));
            }

            int srcOffset = srcVertexFormat.getElementOffset(elementType);
            int dstOffset = dstVertexFormat.getElementOffset(elementType);

            ops.add(new MemoryTransfer(srcOffset, dstOffset, elementType.getByteLength()));
        }

        return mergeAdjacentMemoryTransfers(ops);
    }

    private static List<MemoryTransfer> mergeAdjacentMemoryTransfers(ArrayList<MemoryTransfer> src) {
        ArrayList<MemoryTransfer> dst = new ArrayList<>(src.size());

        int srcOffset = 0;
        int dstOffset = 0;

        int length = 0;

        for (MemoryTransfer op : src) {
            if (srcOffset + length == op.src() && dstOffset + length == op.dst()) {
                length += op.length();
                continue;
            }

            if (length > 0) {
                dst.add(new MemoryTransfer(srcOffset, dstOffset, length));
            }

            srcOffset = op.src();
            dstOffset = op.dst();
            length = op.length();
        }

        if (length > 0) {
            dst.add(new MemoryTransfer(srcOffset, dstOffset, length));
        }

        return dst;
    }

    public static Class<?> define(Bytecode bytecode) {
        try {
            // Lookup#defineClass(byte[]) only exists since Java 9, so the class is defined through the class loader of this class;
            // ClassLoader#defineClass is protected, hence the reflection (works on Java 8).
            Method method = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            method.setAccessible(true);

            return (Class<?>) method.invoke(VertexSerializerFactory.class.getClassLoader(), null, bytecode.data, 0, bytecode.data.length);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access generated class", e);
        }
    }

    public static final class Bytecode {
        private final byte[] data;

        // We really don't want people defining random classes in our package
        private Bytecode(byte[] data) {
            this.data = data;
        }

        // Ensure nobody can get a mutable reference to the bytecode
        public byte[] copy() {
            return this.data.clone();
        }
    }

    public static final class MemoryTransfer {
        private final int src;
        private final int dst;
        private final int length;

        public MemoryTransfer(int src, int dst, int length) {
            this.src = src;
            this.dst = dst;
            this.length = length;
        }

        public int src() {
            return this.src;
        }

        public int dst() {
            return this.dst;
        }

        public int length() {
            return this.length;
        }
    }
}

