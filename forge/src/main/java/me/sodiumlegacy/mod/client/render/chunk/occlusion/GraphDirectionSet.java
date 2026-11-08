package me.sodiumlegacy.mod.client.render.chunk.occlusion;

public class GraphDirectionSet {
    public static final int NONE    = 0;
    public static final int ALL     = (1 << GraphDirection.COUNT) - 1;

    public static int of(int EnumFacing) {
        return 1 << EnumFacing;
    }

    public static boolean contains(int set, int EnumFacing) {
        return (set & GraphDirectionSet.of(EnumFacing)) != 0;
    }
}
