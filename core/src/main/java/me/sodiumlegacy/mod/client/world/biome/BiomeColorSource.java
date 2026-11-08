package me.sodiumlegacy.mod.client.world.biome;

/**
 * The subset of biome-driven colors the ported model pipeline (color providers) consumes.
 *
 * On 1.8.9 there is no vanilla {@code ColorResolver} registry (that API arrived in 1.12+ /
 * modern versions); biome colors are resolved per-block by {@code Biome#getGrassColor(BlockPos)},
 * {@code Biome#getFoliageColor(BlockPos)} and the {@code Biome#waterColor} field.
 */
public enum BiomeColorSource {
    GRASS,
    FOLIAGE,
    WATER;

    public static final BiomeColorSource[] VALUES = BiomeColorSource.values();
    public static final int COUNT = VALUES.length;
}
