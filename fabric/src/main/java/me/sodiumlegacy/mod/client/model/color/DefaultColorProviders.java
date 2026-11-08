package me.sodiumlegacy.mod.client.model.color;

import me.sodiumlegacy.mod.client.model.quad.blender.BlendedColorProvider;
import me.sodiumlegacy.mod.client.world.biome.BiomeColorSource;
import me.sodiumlegacy.mod.client.world.WorldSlice;
import net.minecraft.block.BlockState;

public class DefaultColorProviders {
    public static class GrassColorProvider<T> extends BlendedColorProvider<T> {
        public static final ColorProvider<BlockState> BLOCKS = new GrassColorProvider<>();

        private GrassColorProvider() {

        }

        @Override
        protected int getColor(WorldSlice world, int x, int y, int z) {
            return world.getColor(BiomeColorSource.GRASS, x, y, z);
        }
    }

    public static class FoliageColorProvider<T> extends BlendedColorProvider<T> {
        public static final ColorProvider<BlockState> BLOCKS = new FoliageColorProvider<>();

        private FoliageColorProvider() {

        }

        @Override
        protected int getColor(WorldSlice world, int x, int y, int z) {
            return world.getColor(BiomeColorSource.FOLIAGE, x, y, z);
        }
    }

    public static class WaterColorProvider<T> extends BlendedColorProvider<T> {
        public static final ColorProvider<BlockState> BLOCKS = new WaterColorProvider<>();

        private WaterColorProvider() {

        }

        @Override
        protected int getColor(WorldSlice world, int x, int y, int z) {
            return world.getColor(BiomeColorSource.WATER, x, y, z);
        }
    }
}
