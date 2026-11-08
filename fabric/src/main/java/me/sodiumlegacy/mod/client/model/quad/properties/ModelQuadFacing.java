package me.sodiumlegacy.mod.client.model.quad.properties;

import net.minecraft.util.math.Direction;

public enum ModelQuadFacing {
    POS_X,
    POS_Y,
    POS_Z,
    NEG_X,
    NEG_Y,
    NEG_Z,
    UNASSIGNED;

    public static final ModelQuadFacing[] VALUES = ModelQuadFacing.values();

    public static final int COUNT = VALUES.length;

    public static final int NONE = 0;
    public static final int ALL = (1 << COUNT) - 1;

    public static ModelQuadFacing fromDirection(Direction dir) {
        switch (dir) {
            case DOWN:   return NEG_Y;
            case UP:     return POS_Y;
            case NORTH:  return NEG_Z;
            case SOUTH:  return POS_Z;
            case WEST:   return NEG_X;
            case EAST:   return POS_X;
            default: throw new AssertionError(dir);
        }
    }

    public ModelQuadFacing getOpposite() {
        switch (this) {
            case POS_Y: return NEG_Y;
            case NEG_Y: return POS_Y;
            case POS_X: return NEG_X;
            case NEG_X: return POS_X;
            case POS_Z: return NEG_Z;
            case NEG_Z: return POS_Z;
            default: return UNASSIGNED;
        }
    }
}
