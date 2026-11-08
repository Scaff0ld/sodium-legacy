package me.sodiumlegacy.mod.client.render.immediate.model;

import me.sodiumlegacy.mod.api.math.MatrixHelper;
import me.sodiumlegacy.mod.api.vertex.buffer.VertexBufferWriter;
import me.sodiumlegacy.mod.api.vertex.format.common.ModelVertex;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.util.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import me.sodiumlegacy.lwjgl3shim.system.MemoryStack;
import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Fast path for rendering entity model parts.
 *
 * <p>1.8.9 rewrite: there is no equivalent of the modern {@code ModelPart} vertex stream, so
 * the path drives directly from vanilla's {@link ModelBox}/{@link TexturedQuad} data. The
 * transform hierarchy replicates what {@code ModelRenderer#render(float scale)} performs with
 * {@code GlStateManager} — translate(offset*scale), push, translate(pivot*scale), rotate
 * Z/Y/X — while keeping the matrices in Java (see {@link ModelMatrices}). Coordinates in the
 * quads ({@code PositionTextureVertex#vector3D}) are already divided by 16 in vanilla, which
 * matches the normalized space assumed here.</p>
 *
 * <p>TODO(1.8.9): honor non-standard render scales (vanilla also applies a uniform
 * {@code GlStateManager.scale(scale)}) and per-part texture materialization — the lightmap
 * path in the chunk pipeline is not wired for entity models.</p>
 */
public class EntityRenderer {

    private static final int NUM_FACE_VERTICES = 4;

    private static final long SCRATCH_BUFFER = MemoryUtil.nmemAlignedAlloc(64, NUM_FACE_VERTICES * ModelVertex.STRIDE);

    /**
     * {@link ModelBox#quadList} is the only private face data needed (the box's public corner
     * fields would have to be re-baked into UVs otherwise); it is read reflectively once.
     */
    private static final Field MODEL_BOX_QUADS;

    static {
        Field field = null;

        try {
            field = ModelBox.class.getDeclaredField("quadList");
            field.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        MODEL_BOX_QUADS = field;
    }

    public static void render(ModelMatrices matrices, VertexBufferWriter writer, ModelRenderer part, int light, int overlay, int color) {
        if (!part.showModel) {
            return;
        }

        // Replicates vanilla 1.8.9 ModelRenderer#render: translate(offset) and the pivot +
        // rotation transform around it (which also applies to children), all scaled by 1/16.
        matrices.push();

        matrices.translate(part.offsetX / 16.0F, part.offsetY / 16.0F, part.offsetZ / 16.0F);

        matrices.push();
        matrices.translate(part.rotationPointX / 16.0F, part.rotationPointY / 16.0F, part.rotationPointZ / 16.0F);

        if (part.rotateAngleZ != 0.0F) {
            matrices.rotateZ(part.rotateAngleZ * (180.0F / (float) Math.PI));
        }

        if (part.rotateAngleY != 0.0F) {
            matrices.rotateY(part.rotateAngleY * (180.0F / (float) Math.PI));
        }

        if (part.rotateAngleX != 0.0F) {
            matrices.rotateX(part.rotateAngleX * (180.0F / (float) Math.PI));
        }

        if (!part.isHidden) {
            renderCuboids(matrices.peek(), writer, part.cubeList, light, overlay, color);
        }

        if (part.childModels != null) {
            for (ModelRenderer child : part.childModels) {
                render(matrices, writer, child, light, overlay, color);
            }
        }

        matrices.pop();

        matrices.pop();
    }

    private static void renderCuboids(ModelMatrices.Entry matrices, VertexBufferWriter writer, List<ModelBox> boxes, int light, int overlay, int color) {
        if (boxes == null || boxes.isEmpty()) {
            return;
        }

        for (ModelBox box : boxes) {
            TexturedQuad[] quads = getQuads(box);

            if (quads == null) {
                continue;
            }

            for (TexturedQuad quad : quads) {
                int vertexCount = emitQuads(matrices, quad, color, light, overlay);

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    writer.push(stack, SCRATCH_BUFFER, vertexCount, ModelVertex.FORMAT);
                }
            }
        }
    }

    private static TexturedQuad[] getQuads(ModelBox box) {
        try {
            return (TexturedQuad[]) MODEL_BOX_QUADS.get(box);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to read ModelBox quads", e);
        }
    }

    private static int emitQuads(ModelMatrices.Entry matrices, TexturedQuad quad, int color, int light, int overlay) {
        final Matrix4f positionMatrix = matrices.getPositionMatrix();
        final Matrix3f normalMatrix = matrices.getNormalMatrix();

        Vec3 v0 = quad.vertexPositions[0].vector3D;
        Vec3 v1 = quad.vertexPositions[1].vector3D;
        Vec3 v2 = quad.vertexPositions[2].vector3D;
        Vec3 v3 = quad.vertexPositions[3].vector3D;

        // The face normal from the quad's winding order (mirrored parts come pre-flipped by
        // vanilla), transformed by the normal matrix like any other model normal.
        float ex = (float) (v1.xCoord - v0.xCoord);
        float ey = (float) (v1.yCoord - v0.yCoord);
        float ez = (float) (v1.zCoord - v0.zCoord);
        float fx = (float) (v2.xCoord - v0.xCoord);
        float fy = (float) (v2.yCoord - v0.yCoord);
        float fz = (float) (v2.zCoord - v0.zCoord);

        float nx = ey * fz - ez * fy;
        float ny = ez * fx - ex * fz;
        float nz = ex * fy - ey * fx;

        int normal = MatrixHelper.transformNormal(normalMatrix, nx, ny, nz);

        emitVertex(SCRATCH_BUFFER, positionMatrix, v0, color, quad.vertexPositions[0], overlay, light, normal);
        emitVertex(SCRATCH_BUFFER + ModelVertex.STRIDE, positionMatrix, v1, color, quad.vertexPositions[1], overlay, light, normal);
        emitVertex(SCRATCH_BUFFER + 2 * ModelVertex.STRIDE, positionMatrix, v2, color, quad.vertexPositions[2], overlay, light, normal);
        emitVertex(SCRATCH_BUFFER + 3 * ModelVertex.STRIDE, positionMatrix, v3, color, quad.vertexPositions[3], overlay, light, normal);

        return NUM_FACE_VERTICES;
    }

    private static void emitVertex(long ptr, Matrix4f matrix, Vec3 pos, int color,
                                   net.minecraft.client.model.PositionTextureVertex tex, int overlay, int light, int normal) {
        float x = MatrixHelper.transformPositionX(matrix, (float) pos.xCoord, (float) pos.yCoord, (float) pos.zCoord);
        float y = MatrixHelper.transformPositionY(matrix, (float) pos.xCoord, (float) pos.yCoord, (float) pos.zCoord);
        float z = MatrixHelper.transformPositionZ(matrix, (float) pos.xCoord, (float) pos.yCoord, (float) pos.zCoord);

        // (u, v) are already normalized by vanilla's texel-value /16 division
        ModelVertex.write(ptr, x, y, z, color, tex.texturePositionX, tex.texturePositionY, overlay, light, normal);
    }
}

