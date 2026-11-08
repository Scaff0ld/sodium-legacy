package me.sodiumlegacy.mod.client.render.immediate.model;

import me.sodiumlegacy.mod.client.model.quad.ModelQuadView;
import me.sodiumlegacy.mod.api.math.MatrixHelper;
import me.sodiumlegacy.mod.api.util.ColorABGR;
import me.sodiumlegacy.mod.api.util.ColorU8;
import me.sodiumlegacy.mod.api.vertex.buffer.VertexBufferWriter;
import me.sodiumlegacy.mod.api.vertex.format.common.ModelVertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import me.sodiumlegacy.lwjgl3shim.system.MemoryStack;

/**
 * 1.8.9 port: the modern {@code MatrixStack.Entry} parameter has been replaced by sodium's own
 * {@link ModelMatrices.Entry} (there is no vanilla matrix stack on this fork).
 */
public class BakedModelEncoder {
    public static void writeQuadVertices(VertexBufferWriter writer, ModelMatrices.Entry matrices, ModelQuadView quad, int color, int light, int overlay) {
        Matrix3f matNormal = matrices.getNormalMatrix();
        Matrix4f matPosition = matrices.getPositionMatrix();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4 * ModelVertex.STRIDE);
            long ptr = buffer;

            // The packed transformed normal vector
            int normal = DirectionNormals.transformNormal(matNormal, quad.getLightFace());

            for (int i = 0; i < 4; i++) {
                // The position vector
                float x = quad.getX(i);
                float y = quad.getY(i);
                float z = quad.getZ(i);

                // The transformed position vector
                float xt = MatrixHelper.transformPositionX(matPosition, x, y, z);
                float yt = MatrixHelper.transformPositionY(matPosition, x, y, z);
                float zt = MatrixHelper.transformPositionZ(matPosition, x, y, z);

                ModelVertex.write(ptr, xt, yt, zt, color, quad.getTexU(i), quad.getTexV(i), overlay, light, normal);
                ptr += ModelVertex.STRIDE;
            }

            writer.push(stack, buffer, 4, ModelVertex.FORMAT);
        }
    }

    public static void writeQuadVertices(VertexBufferWriter writer, ModelMatrices.Entry matrices, ModelQuadView quad, float r, float g, float b, float[] brightnessTable, boolean colorize, int[] light, int overlay) {
        Matrix3f matNormal = matrices.getNormalMatrix();
        Matrix4f matPosition = matrices.getPositionMatrix();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4 * ModelVertex.STRIDE);
            long ptr = buffer;

            // The packed transformed normal vector
            int normal = DirectionNormals.transformNormal(matNormal, quad.getLightFace());

            for (int i = 0; i < 4; i++) {
                // The position vector
                float x = quad.getX(i);
                float y = quad.getY(i);
                float z = quad.getZ(i);

                // The transformed position vector
                float xt = MatrixHelper.transformPositionX(matPosition, x, y, z);
                float yt = MatrixHelper.transformPositionY(matPosition, x, y, z);
                float zt = MatrixHelper.transformPositionZ(matPosition, x, y, z);

                float fR;
                float fG;
                float fB;

                float brightness = brightnessTable[i];

                if (colorize) {
                    int color = quad.getColor(i);

                    float oR = ColorU8.byteToNormalizedFloat(ColorABGR.unpackRed(color));
                    float oG = ColorU8.byteToNormalizedFloat(ColorABGR.unpackGreen(color));
                    float oB = ColorU8.byteToNormalizedFloat(ColorABGR.unpackBlue(color));

                    fR = oR * brightness * r;
                    fG = oG * brightness * g;
                    fB = oB * brightness * b;
                } else {
                    fR = brightness * r;
                    fG = brightness * g;
                    fB = brightness * b;
                }

                int color = ColorABGR.pack(fR, fG, fB, 1.0F);

                ModelVertex.write(ptr, xt, yt, zt, color, quad.getTexU(i), quad.getTexV(i), overlay, light[i], normal);
                ptr += ModelVertex.STRIDE;
            }

            writer.push(stack, buffer, 4, ModelVertex.FORMAT);
        }
    }
}

