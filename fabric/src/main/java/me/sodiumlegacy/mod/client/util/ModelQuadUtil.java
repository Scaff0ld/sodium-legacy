package me.sodiumlegacy.mod.client.util;

import me.sodiumlegacy.mod.client.model.quad.ModelQuadView;
import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFacing;
import me.sodiumlegacy.mod.api.util.NormI8;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/**
 * Provides some utilities and constants for interacting with vanilla's (1.8.9) model quad vertex format.
 *
 * The vertex format used by vanilla 1.8.9 for chunk meshes and baked model quads (the BLOCK format in
 * {@code VertexFormats}) stores baked quad data in integer arrays as follows:
 *
 * Byte Index    Integer Index             Name                 Format                 Fields
 * 0 ..11        0..2                      Position             3 floats               x, y, z
 * 12..15        3                         Color                4 unsigned bytes       a, r, g, b
 * 16..23        4..5                      Block Texture        2 floats               u, v
 * 24..27        6                         Light Texture        2 shorts               u, v
 *
 * Unlike the modern format, there is no packed normal element in this layout; face shading is applied
 * separately (see the light pipelines).
 */
public class ModelQuadUtil {
    // Integer indices for vertex attributes, useful for accessing baked quad data
    public static final int POSITION_INDEX = 0,
            COLOR_INDEX = 3,
            TEXTURE_INDEX = 4,
            LIGHT_INDEX = 6;

    // Size of vertex format in 4-byte integers
    public static final int VERTEX_SIZE = 7;

    /**
     * @param vertexIndex The index of the vertex to access
     * @return The starting offset of the vertex's attributes
     */
    public static int vertexOffset(int vertexIndex) {
        return vertexIndex * VERTEX_SIZE;
    }

    public static ModelQuadFacing findNormalFace(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            return ModelQuadFacing.UNASSIGNED;
        }

        // Normalize the given vector so that the dot product test is exact for axis-aligned normals
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l == 0.0f) {
            return ModelQuadFacing.UNASSIGNED;
        }

        float nx = x / l;
        float ny = y / l;
        float nz = z / l;

        float maxDot = 0.0f;
        Direction closestFace = null;

        for (Direction face : DirectionUtil.ALL_DIRECTIONS) {
            float dot = (nx * face.getOffsetX())
                    + (ny * face.getOffsetY())
                    + (nz * face.getOffsetZ());

            if (dot > maxDot) {
                maxDot = dot;
                closestFace = face;
            }
        }

        if (closestFace != null && MathHelper.approximatelyEquals(maxDot, 1.0f)) {
            return ModelQuadFacing.fromDirection(closestFace);
        }

        return ModelQuadFacing.UNASSIGNED;
    }

    public static ModelQuadFacing findNormalFace(int normal) {
        return findNormalFace(NormI8.unpackX(normal), NormI8.unpackY(normal), NormI8.unpackZ(normal));
    }

    public static int calculateNormal(ModelQuadView quad) {
        final float x0 = quad.getX(0);
        final float y0 = quad.getY(0);
        final float z0 = quad.getZ(0);

        final float x1 = quad.getX(1);
        final float y1 = quad.getY(1);
        final float z1 = quad.getZ(1);

        final float x2 = quad.getX(2);
        final float y2 = quad.getY(2);
        final float z2 = quad.getZ(2);

        final float x3 = quad.getX(3);
        final float y3 = quad.getY(3);
        final float z3 = quad.getZ(3);

        final float dx0 = x2 - x0;
        final float dy0 = y2 - y0;
        final float dz0 = z2 - z0;
        final float dx1 = x3 - x1;
        final float dy1 = y3 - y1;
        final float dz1 = z3 - z1;

        float normX = dy0 * dz1 - dz0 * dy1;
        float normY = dz0 * dx1 - dx0 * dz1;
        float normZ = dx0 * dy1 - dy0 * dx1;

        float l = (float) Math.sqrt(normX * normX + normY * normY + normZ * normZ);

        if (l != 0) {
            normX /= l;
            normY /= l;
            normZ /= l;
        }

        return NormI8.pack(normX, normY, normZ);
    }
}
