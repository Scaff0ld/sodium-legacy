package me.sodiumlegacy.mod.client.world.biome;

public interface BiomeColorView {
    int getColor(BiomeColorSource resolver, int blockX, int blockY, int blockZ);
}
