package me.sodiumlegacy.mod.client.render.immediate.model;

import me.sodiumlegacy.mod.api.math.MatrixHelper;
import me.sodiumlegacy.mod.api.vertex.buffer.VertexBufferWriter;
import me.sodiumlegacy.mod.api.vertex.format.common.ModelVertex;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.util.math.Direction;
import org.apache.commons.lang3.ArrayUtils;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import me.sodiumlegacy.lwjgl3shim.system.MemoryStack;
import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;

/**
 * Fast path for rendering entity model parts.
 *
 * <p>The transform hierarchy replicates what {@code ModelPart#render(float scale)} performs in
 * vanilla 1.8.9 with {@code GlStateManager} — translate(offset), push, translate(pivot), rotate
 * Z/Y/X — while keeping the matrices in Java (see {@link ModelMatrices}). Coordinates are in the
 * modern-style normalized space (cuboid coordinates pre-divided by 16), pivots are divided by 16
 * likewise; vanilla multiplies both by {@code scale = 1/16} instead, which yields the same result
 * for the standard {@code 0.0625F} render scale. (TODO(1.8.9): honor non-standard render scales.)</p>
 */
public class EntityRenderer {

    private static final int NUM_CUBE_VERTICES = 8;
    private static final int NUM_CUBE_FACES = 6;
    private static final int NUM_FACE_VERTICES = 4;

    private static final int
            FACE_NEG_Y = 0, // DOWN
            FACE_POS_Y = 1, // UP
            FACE_NEG_Z = 2, // NORTH
            FACE_POS_Z = 3, // SOUTH
            FACE_NEG_X = 4, // WEST
            FACE_POS_X = 5; // EAST

    private static final int
            VERTEX_X1_Y1_Z1 = 0,
            VERTEX_X2_Y1_Z1 = 1,
            VERTEX_X2_Y2_Z1 = 2,
            VERTEX_X1_Y2_Z1 = 3,
            VERTEX_X1_Y1_Z2 = 4,
            VERTEX_X2_Y1_Z2 = 5,
            VERTEX_X2_Y2_Z2 = 6,
            VERTEX_X1_Y2_Z2 = 7;


    private static final long SCRATCH_BUFFER = MemoryUtil.nmemAlignedAlloc(64, NUM_CUBE_FACES * NUM_FACE_VERTICES * ModelVertex.STRIDE);

    private static final Vector3f[] CUBE_CORNERS = new Vector3f[NUM_CUBE_VERTICES];
    private static final int[][] CUBE_VERTICES = new int[][] {
            { VERTEX_X2_Y1_Z2, VERTEX_X1_Y1_Z2, VERTEX_X1_Y1_Z1, VERTEX_X2_Y1_Z1 },
            { VERTEX_X2_Y2_Z1, VERTEX_X1_Y2_Z1, VERTEX_X1_Y2_Z2, VERTEX_X2_Y2_Z2 },
            { VERTEX_X2_Y1_Z1, VERTEX_X1_Y1_Z1, VERTEX_X1_Y2_Z1, VERTEX_X2_Y2_Z1 },
            { VERTEX_X1_Y1_Z2, VERTEX_X2_Y1_Z2, VERTEX_X2_Y2_Z2, VERTEX_X1_Y2_Z2 },
            { VERTEX_X2_Y1_Z2, VERTEX_X2_Y1_Z1, VERTEX_X2_Y2_Z1, VERTEX_X2_Y2_Z2 },
            { VERTEX_X1_Y1_Z1, VERTEX_X1_Y1_Z2, VERTEX_X1_Y2_Z2, VERTEX_X1_Y2_Z1 },
    };

    private static final Vector3f[][] VERTEX_POSITIONS = new Vector3f[NUM_CUBE_FACES][NUM_FACE_VERTICES];
    private static final Vector3f[][] VERTEX_POSITIONS_MIRRORED = new Vector3f[NUM_CUBE_FACES][NUM_FACE_VERTICES];

    private static final Vector2f[][] VERTEX_TEXTURES = new Vector2f[NUM_CUBE_FACES][NUM_FACE_VERTICES];
    private static final Vector2f[][] VERTEX_TEXTURES_MIRRORED = new Vector2f[NUM_CUBE_FACES][NUM_FACE_VERTICES];

    private static final int[] CUBE_NORMALS = new int[NUM_CUBE_FACES];
    private static final int[] CUBE_NORMALS_MIRRORED = new int[NUM_CUBE_FACES];

    static {
        for (int cornerIndex = 0; cornerIndex < NUM_CUBE_VERTICES; cornerIndex++) {
            CUBE_CORNERS[cornerIndex] = new Vector3f();
        }

        for (int quadIndex = 0; quadIndex < NUM_CUBE_FACES; quadIndex++) {
            for (int vertexIndex = 0; vertexIndex < NUM_FACE_VERTICES; vertexIndex++) {
                VERTEX_TEXTURES[quadIndex][vertexIndex] = new Vector2f();
                VERTEX_POSITIONS[quadIndex][vertexIndex] = CUBE_CORNERS[CUBE_VERTICES[quadIndex][vertexIndex]];
            }
        }

        for (int quadIndex = 0; quadIndex < NUM_CUBE_FACES; quadIndex++) {
            for (int vertexIndex = 0; vertexIndex < NUM_FACE_VERTICES; vertexIndex++) {
                VERTEX_TEXTURES_MIRRORED[quadIndex][vertexIndex] = VERTEX_TEXTURES[quadIndex][3 - vertexIndex];
                VERTEX_POSITIONS_MIRRORED[quadIndex][vertexIndex] = VERTEX_POSITIONS[quadIndex][3 - vertexIndex];
            }
        }
    }

    public static void render(ModelMatrices matrices, VertexBufferWriter writer, ModelPart part, int light, int overlay, int color) {
        ModelPartData accessor = ModelPartData.from(part);

        if (!accessor.isVisible()) {
            return;
        }

        ModelCuboid[] cuboids = accessor.getCuboids();
        ModelPart[] children = accessor.getChildren();

        if (ArrayUtils.isEmpty(cuboids) && ArrayUtils.isEmpty(children)) {
            return;
        }

        // Replicates vanilla 1.8.9 ModelPart#render: translate(offset) first, then the pivot +
        // rotation transform around it (which also applies to children).
        matrices.push();

        matrices.translate(part.offsetX, part.offsetY, part.offsetZ);

        matrices.push();
        matrices.translate(part.pivotX / 16.0f, part.pivotY / 16.0f, part.pivotZ / 16.0f);

        if (part.posZ != 0.0f) {
            matrices.rotateZ(part.posZ * (180.0f / (float) Math.PI));
        }

        if (part.posY != 0.0f) {
            matrices.rotateY(part.posY * (180.0f / (float) Math.PI));
        }

        if (part.posX != 0.0f) {
            matrices.rotateX(part.posX * (180.0f / (float) Math.PI));
        }

        if (!accessor.isHidden()) {
            renderCuboids(matrices.peek(), writer, cuboids, light, overlay, color);
        }

        renderChildren(matrices, writer, light, overlay, color, children);

        matrices.pop();

        matrices.pop();
    }

    private static void renderChildren(ModelMatrices matrices, VertexBufferWriter writer, int light, int overlay, int color, ModelPart[] children) {
        for (ModelPart part : children) {
            render(matrices, writer, part, light, overlay, color);
        }
    }

    private static void renderCuboids(ModelMatrices.Entry matrices, VertexBufferWriter writer, ModelCuboid[] cuboids, int light, int overlay, int color) {
        prepareNormals(matrices);

        for (ModelCuboid cuboid : cuboids) {
            prepareVertices(matrices, cuboid);

            int vertexCount = emitQuads(cuboid, color, overlay, light);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                writer.push(stack, SCRATCH_BUFFER, vertexCount, ModelVertex.FORMAT);
            }
        }
    }

    private static int emitQuads(ModelCuboid cuboid, int color, int overlay, int light) {
        final Vector3f[][] positions = cuboid.mirror ? VERTEX_POSITIONS_MIRRORED : VERTEX_POSITIONS;
        final Vector2f[][] textures = cuboid.mirror ? VERTEX_TEXTURES_MIRRORED : VERTEX_TEXTURES;
        final int[] normals = cuboid.mirror ? CUBE_NORMALS_MIRRORED : CUBE_NORMALS;

        int vertexCount = 0;

        long ptr = SCRATCH_BUFFER;

        for (int quadIndex = 0; quadIndex < NUM_CUBE_FACES; quadIndex++) {
            if (!cuboid.shouldDrawFace(quadIndex)) {
                continue;
            }

            emitVertex(ptr, positions[quadIndex][0], color, textures[quadIndex][0], overlay, light, normals[quadIndex]);
            ptr += ModelVertex.STRIDE;

            emitVertex(ptr, positions[quadIndex][1], color, textures[quadIndex][1], overlay, light, normals[quadIndex]);
            ptr += ModelVertex.STRIDE;

            emitVertex(ptr, positions[quadIndex][2], color, textures[quadIndex][2], overlay, light, normals[quadIndex]);
            ptr += ModelVertex.STRIDE;

            emitVertex(ptr, positions[quadIndex][3], color, textures[quadIndex][3], overlay, light, normals[quadIndex]);
            ptr += ModelVertex.STRIDE;

            vertexCount += 4;
        }

        return vertexCount;
    }

    private static void emitVertex(long ptr, Vector3f pos, int color, Vector2f tex, int overlay, int light, int normal) {
        ModelVertex.write(ptr, pos.x, pos.y, pos.z, color, tex.x, tex.y, overlay, light, normal);
    }

    private static void prepareVertices(ModelMatrices.Entry matrices, ModelCuboid cuboid) {
        buildVertexPosition(CUBE_CORNERS[VERTEX_X1_Y1_Z1], cuboid.x1, cuboid.y1, cuboid.z1, matrices.getPositionMatrix());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X2_Y1_Z1], cuboid.x2, cuboid.y1, cuboid.z1, matrices.getPositionMatrix());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X2_Y2_Z1], cuboid.x2, cuboid.y2, cuboid.z1, matrices.getPositionMatrix());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X1_Y2_Z1], cuboid.x1, cuboid.y2, cuboid.z1, matrices.getPositionMatrix());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X1_Y1_Z2], cuboid.x1, cuboid.y1, cuboid.z2, matrices.getPositionMatrix());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X2_Y1_Z2], cuboid.x2, cuboid.y1, cuboid.z2, matrices.getPositionMatrix());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X2_Y2_Z2], cuboid.x2, cuboid.y2, cuboid.z2, matrices.getPositionMatrix());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X1_Y2_Z2], cuboid.x1, cuboid.y2, cuboid.z2, matrices.getPositionMatrix());

        buildVertexTexCoord(VERTEX_TEXTURES[FACE_NEG_Y], cuboid.u1, cuboid.v0, cuboid.u2, cuboid.v1);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_POS_Y], cuboid.u2, cuboid.v1, cuboid.u3, cuboid.v0);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_NEG_Z], cuboid.u1, cuboid.v1, cuboid.u2, cuboid.v2);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_POS_Z], cuboid.u4, cuboid.v1, cuboid.u5, cuboid.v2);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_NEG_X], cuboid.u2, cuboid.v1, cuboid.u4, cuboid.v2);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_POS_X], cuboid.u0, cuboid.v1, cuboid.u1, cuboid.v2);
    }

    private static void prepareNormals(ModelMatrices.Entry matrices) {
        CUBE_NORMALS[FACE_NEG_Y] = DirectionNormals.transformNormal(matrices.getNormalMatrix(), Direction.DOWN);
        CUBE_NORMALS[FACE_POS_Y] = DirectionNormals.transformNormal(matrices.getNormalMatrix(), Direction.UP);
        CUBE_NORMALS[FACE_NEG_Z] = DirectionNormals.transformNormal(matrices.getNormalMatrix(), Direction.NORTH);
        CUBE_NORMALS[FACE_POS_Z] = DirectionNormals.transformNormal(matrices.getNormalMatrix(), Direction.SOUTH);
        CUBE_NORMALS[FACE_POS_X] = DirectionNormals.transformNormal(matrices.getNormalMatrix(), Direction.WEST);
        CUBE_NORMALS[FACE_NEG_X] = DirectionNormals.transformNormal(matrices.getNormalMatrix(), Direction.EAST);

        // When mirroring is used, the normals for EAST and WEST are swapped.
        CUBE_NORMALS_MIRRORED[FACE_NEG_Y] = CUBE_NORMALS[FACE_NEG_Y];
        CUBE_NORMALS_MIRRORED[FACE_POS_Y] = CUBE_NORMALS[FACE_POS_Y];
        CUBE_NORMALS_MIRRORED[FACE_NEG_Z] = CUBE_NORMALS[FACE_NEG_Z];
        CUBE_NORMALS_MIRRORED[FACE_POS_Z] = CUBE_NORMALS[FACE_POS_Z];
        CUBE_NORMALS_MIRRORED[FACE_POS_X] = CUBE_NORMALS[FACE_NEG_X]; // mirrored
        CUBE_NORMALS_MIRRORED[FACE_NEG_X] = CUBE_NORMALS[FACE_POS_X]; // mirrored
    }

    private static void buildVertexPosition(Vector3f vector, float x, float y, float z, Matrix4f matrix) {
        vector.x = MatrixHelper.transformPositionX(matrix, x, y, z);
        vector.y = MatrixHelper.transformPositionY(matrix, x, y, z);
        vector.z = MatrixHelper.transformPositionZ(matrix, x, y, z);
    }

    private static void buildVertexTexCoord(Vector2f[] uvs, float u1, float v1, float u2, float v2) {
        uvs[0].set(u2, v1);
        uvs[1].set(u1, v1);
        uvs[2].set(u1, v2);
        uvs[3].set(u2, v2);
    }
}

