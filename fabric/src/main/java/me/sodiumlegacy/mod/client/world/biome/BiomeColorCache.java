package me.sodiumlegacy.mod.client.world.biome;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.sodiumlegacy.mod.client.util.color.BoxBlur;
import me.sodiumlegacy.mod.client.util.color.BoxBlur.ColorBuffer;
import me.sodiumlegacy.mod.client.world.cloned.ChunkRenderContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;

public class BiomeColorCache {
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;
    private final BiomeSlice biomeData;

    private final Reference2ReferenceOpenHashMap<BiomeColorSource, Slice[]> slices;
    private long populateStamp;

    private final int blendRadius;

    private final ColorBuffer tempColorBuffer;

    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;

    private final int sizeXZ, sizeY;

    private final BlockPos.Mutable tempPos = new BlockPos.Mutable();

    public BiomeColorCache(BiomeSlice biomeData, int blendRadius) {
        this.biomeData = biomeData;
        this.blendRadius = blendRadius;

        this.sizeXZ = 16 + ((NEIGHBOR_BLOCK_RADIUS + this.blendRadius) * 2);
        this.sizeY = 16 + (NEIGHBOR_BLOCK_RADIUS * 2);

        this.slices = new Reference2ReferenceOpenHashMap<>();
        this.populateStamp = 1;

        this.tempColorBuffer = new ColorBuffer(this.sizeXZ, this.sizeXZ);
    }

    public void update(ChunkRenderContext context) {
        this.minX = (context.getOrigin().getMinX() - NEIGHBOR_BLOCK_RADIUS) - this.blendRadius;
        this.minY = (context.getOrigin().getMinY() - NEIGHBOR_BLOCK_RADIUS);
        this.minZ = (context.getOrigin().getMinZ() - NEIGHBOR_BLOCK_RADIUS) - this.blendRadius;

        this.maxX = (context.getOrigin().getMaxX() + NEIGHBOR_BLOCK_RADIUS) + this.blendRadius;
        this.maxY = (context.getOrigin().getMaxY() + NEIGHBOR_BLOCK_RADIUS);
        this.maxZ = (context.getOrigin().getMaxZ() + NEIGHBOR_BLOCK_RADIUS) + this.blendRadius;

        this.populateStamp++;
    }

    /**
     * Returns the blended biome-driven color for the given block position and color source.
     * (On 1.8.9, the "resolvers" of modern versions map directly to per-biome getters, handled here.)
     */
    public int getColor(BiomeColorSource source, int blockX, int blockY, int blockZ) {
        int relX = MathHelper.clamp(blockX, this.minX, this.maxX) - this.minX;
        int relY = MathHelper.clamp(blockY, this.minY, this.maxY) - this.minY;
        int relZ = MathHelper.clamp(blockZ, this.minZ, this.maxZ) - this.minZ;

        if (!this.slices.containsKey(source)) {
            this.initializeSlices(source);
        }

        Slice slice = this.slices.get(source)[relY];

        if (slice.lastPopulateStamp < this.populateStamp) {
            this.updateColorBuffers(relY, source, slice);
        }

        return slice.getBuffer().get(relX, relZ);
    }

    private void initializeSlices(BiomeColorSource source) {
        Slice[] slice = new Slice[this.sizeY];
        this.slices.put(source, slice);

        for (int y = 0; y < this.sizeY; y++) {
            slice[y] = new Slice(this.sizeXZ);
        }
    }

    private void updateColorBuffers(int relY, BiomeColorSource source, Slice slice) {
        int worldY = this.minY + relY;

        for (int worldZ = this.minZ; worldZ <= this.maxZ; worldZ++) {
            for (int worldX = this.minX; worldX <= this.maxX; worldX++) {
                Biome biome = this.biomeData.getBiome(worldX, worldY, worldZ);

                BlockPos pos = this.tempPos.setPosition(worldX, worldY, worldZ);

                int color;
                switch (source) {
                    case GRASS:
                        color = biome.getGrassColor(pos);
                        break;
                    case FOLIAGE:
                        color = biome.getFoliageColor(pos);
                        break;
                    case WATER:
                        color = biome.waterColor;
                        break;
                    default:
                        throw new AssertionError(source);
                }

                int relativeX = worldX - this.minX;
                int relativeZ = worldZ - this.minZ;

                slice.getBuffer().set(relativeX, relativeZ, color);
            }
        }

        if (this.blendRadius > 0) {
            BoxBlur.blur(slice.getBuffer(), this.tempColorBuffer, this.blendRadius);
        }

        slice.lastPopulateStamp = this.populateStamp;
    }

    private static class Slice {
        private final ColorBuffer buffer;
        private long lastPopulateStamp;

        private Slice(int size) {
            this.buffer = new ColorBuffer(size, size);
            this.lastPopulateStamp = 0;
        }

        public ColorBuffer getBuffer() {
            return this.buffer;
        }
    }
}
