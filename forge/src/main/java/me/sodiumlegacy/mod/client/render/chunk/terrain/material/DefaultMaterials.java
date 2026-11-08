package me.sodiumlegacy.mod.client.render.chunk.terrain.material;

import me.sodiumlegacy.mod.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.sodiumlegacy.mod.client.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumWorldBlockLayer;

public class DefaultMaterials {
    public static final Material SOLID = new Material(DefaultTerrainRenderPasses.SOLID, AlphaCutoffParameter.ZERO, true);
    public static final Material CUTOUT = new Material(DefaultTerrainRenderPasses.CUTOUT, AlphaCutoffParameter.ONE_TENTH, false);
    public static final Material CUTOUT_MIPPED = new Material(DefaultTerrainRenderPasses.CUTOUT, AlphaCutoffParameter.ONE_TENTH, true);
    public static final Material TRANSLUCENT = new Material(DefaultTerrainRenderPasses.TRANSLUCENT, AlphaCutoffParameter.ZERO, true);

    // 1.8.9 has no fluid render layer system (fluids live inside the block render layers), so
    // forFluidState remains for API parity and resolves through the owning block's layer.
    public static Material forBlockState(IBlockState state) {
        return forRenderLayer(state.getBlock().getBlockLayer());
    }

    public static Material forFluidState(IBlockState fluidBlockState) {
        return forBlockState(fluidBlockState);
    }

    public static Material forRenderLayer(EnumWorldBlockLayer layer) {
        switch (layer) {
            case SOLID: return SOLID;
            case CUTOUT: return CUTOUT;
            case CUTOUT_MIPPED: return CUTOUT_MIPPED;
            case TRANSLUCENT: return TRANSLUCENT;
        }

        throw new IllegalArgumentException("No material mapping exists for " + layer);
    }
}
