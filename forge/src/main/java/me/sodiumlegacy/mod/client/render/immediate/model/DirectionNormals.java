package me.sodiumlegacy.mod.client.render.immediate.model;

import me.sodiumlegacy.mod.api.util.NormI8;
import net.minecraft.util.EnumFacing;
import org.joml.Matrix3f;

/**
 * Platform side of {@code MatrixHelper}: the one transform that needs Minecraft's {@link EnumFacing}.
 */
final class DirectionNormals {
    private DirectionNormals() {
    }

    /**
     * Returns the transformed normal vector for a given unit vector (EnumFacing). This is significantly faster
     * than transforming the vector directly (i.e. with {@link Matrix3f#transform(org.joml.Vector3f)}), as it can
     * simply extract the values from the provided matrix (rather than transforming the vertices.)
     *
     * @param matrix The transformation matrix
     * @param EnumFacing The unit vector (EnumFacing) to use
     * @return A transformed normal in packed format
     */
    static int transformNormal(Matrix3f matrix, EnumFacing EnumFacing) {
        switch (EnumFacing) {
            case DOWN:  return NormI8.pack(-matrix.m10(), -matrix.m11(), -matrix.m12());
            case UP:    return NormI8.pack( matrix.m10(),  matrix.m11(),  matrix.m12());
            case NORTH: return NormI8.pack(-matrix.m20(), -matrix.m21(), -matrix.m22());
            case SOUTH: return NormI8.pack( matrix.m20(),  matrix.m21(),  matrix.m22());
            case WEST:  return NormI8.pack(-matrix.m00(), -matrix.m01(), -matrix.m02());
            case EAST:  return NormI8.pack( matrix.m00(),  matrix.m01(),  matrix.m02());
            default: throw new AssertionError(EnumFacing);
        }
    }
}
