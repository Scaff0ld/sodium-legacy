package me.sodiumlegacy.mod.client.render.chunk.terrain;

import net.minecraft.util.EnumWorldBlockLayer;

public class DefaultTerrainRenderPasses {
    public static final TerrainRenderPass SOLID = new TerrainRenderPass(EnumWorldBlockLayer.SOLID, false, false);
    public static final TerrainRenderPass CUTOUT = new TerrainRenderPass(EnumWorldBlockLayer.CUTOUT_MIPPED, false, true);
    public static final TerrainRenderPass TRANSLUCENT = new TerrainRenderPass(EnumWorldBlockLayer.TRANSLUCENT, true, false);


    public static final TerrainRenderPass[] ALL = new TerrainRenderPass[] { SOLID, CUTOUT, TRANSLUCENT };
}
