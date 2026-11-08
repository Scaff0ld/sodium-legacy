package me.sodiumlegacy.mod.client.model.color;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

// TODO: Make the registry a global somewhere that is only initialized once after content load
public class ColorProviderRegistry {
    private final Reference2ReferenceMap<Block, ColorProvider<BlockState>> blocks = new Reference2ReferenceOpenHashMap<>();

    /**
     * 1.8.9 has no vanilla BlockColors registry or pluggable per-block color providers; tinting is computed
     * inside each Block instead. Sodium provides its own resolvers for the blocks which are colored from biome
     * data in vanilla 1.8.9, enabling per-vertex blended colors.
     */
    public ColorProviderRegistry() {
        this.installOverrides();
    }

    private void installOverrides() {
        // The 1.8.9 equivalents of the modern biome-tinted blocks (the 1.8.9 block ids differ from later versions)
        this.registerBlocks(DefaultColorProviders.GrassColorProvider.BLOCKS,
                Blocks.GRASS, Blocks.TALLGRASS, Blocks.DOUBLE_PLANT, Blocks.SUGARCANE);

        this.registerBlocks(DefaultColorProviders.FoliageColorProvider.BLOCKS,
                Blocks.LEAVES, Blocks.LEAVES2, Blocks.VINE);

        this.registerBlocks(DefaultColorProviders.WaterColorProvider.BLOCKS,
                Blocks.WATER, Blocks.FLOWING_WATER);
    }

    private void registerBlocks(ColorProvider<BlockState> resolver, Block... blocks) {
        for (Block block : blocks) {
            this.blocks.put(block, resolver);
        }
    }

    public ColorProvider<BlockState> getColorProvider(Block block) {
        return this.blocks.get(block);
    }
}
