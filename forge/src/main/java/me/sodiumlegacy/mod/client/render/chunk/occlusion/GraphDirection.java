package me.sodiumlegacy.mod.client.render.chunk.occlusion;

import net.minecraft.util.EnumFacing;

public class GraphDirection {
    public static final int DOWN    = 0;
    public static final int UP      = 1;
    public static final int NORTH   = 2;
    public static final int SOUTH   = 3;
    public static final int WEST    = 4;
    public static final int EAST    = 5;


    public static final int COUNT   = 6;

    private static final EnumFacing[] ENUMS;
    private static final int[] OPPOSITE;
    private static final int[] X, Y, Z;

    static {
        OPPOSITE = new int[COUNT];
        OPPOSITE[DOWN] = UP;
        OPPOSITE[UP] = DOWN;
        OPPOSITE[NORTH] = SOUTH;
        OPPOSITE[SOUTH] = NORTH;
        OPPOSITE[WEST] = EAST;
        OPPOSITE[EAST] = WEST;

        X = new int[COUNT];
        X[WEST] = -1;
        X[EAST] = 1;

        Y = new int[COUNT];
        Y[DOWN] = -1;
        Y[UP] = 1;

        Z = new int[COUNT];
        Z[NORTH] = -1;
        Z[SOUTH] = 1;

        ENUMS = new EnumFacing[COUNT];
        ENUMS[DOWN] = EnumFacing.DOWN;
        ENUMS[UP] = EnumFacing.UP;
        ENUMS[NORTH] = EnumFacing.NORTH;
        ENUMS[SOUTH] = EnumFacing.SOUTH;
        ENUMS[WEST] = EnumFacing.WEST;
        ENUMS[EAST] = EnumFacing.EAST;
    }

    public static int opposite(int EnumFacing) {
        return OPPOSITE[EnumFacing];
    }

    public static int x(int EnumFacing) {
        return X[EnumFacing];
    }

    public static int y(int EnumFacing) {
        return Y[EnumFacing];
    }

    public static int z(int EnumFacing) {
        return Z[EnumFacing];
    }

    public static EnumFacing toEnum(int EnumFacing) {
        return ENUMS[EnumFacing];
    }
}