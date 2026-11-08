package me.sodiumlegacy.mod.client.world.biome;

import me.sodiumlegacy.mod.client.world.cloned.ChunkRenderContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.biome.Biome;

/**
 * A snapshot copy of the biome data for the chunk neighborhood covered by a
 * {@link me.sodiumlegacy.mod.client.world.WorldSlice}.
 *
 * <p>Vanilla 1.8.9 stores one biome id per column (a 256-entry byte array per chunk); biomes do not
 * vary vertically. The cloned sections therefore hand us resolved biome ids at clone time (made on
 * the main thread so we never touch the live world's biome source off-thread), and this class simply
 * indexes them.</p>
 */
public class BiomeSlice {
    // The biome ids resolved into biome instances for each chunk column.
    // Arrays are indexed by the chunk's position within the 3x3 neighborhood, each holding 256 column ids.
    private final Biome[][] chunkBiomes = new Biome[3 * 3][];

    // The chunk world coordinates covered by this slice (min chunk X/Z of the neighborhood)
    private int chunkOriginX, chunkOriginZ;

    public void update(World world, ChunkRenderContext context) {
        this.chunkOriginX = context.getOrigin().getX() - 1;
        this.chunkOriginZ = context.getOrigin().getZ() - 1;

        for (int cx = 0; cx < 3; cx++) {
            for (int cz = 0; cz < 3; cz++) {
                int localIndex = (cz * 3) + cx;
                this.chunkBiomes[localIndex] = resolveBiomeArray(
                        this.chunkBiomes[localIndex],
                        context.getSections()[me.sodiumlegacy.mod.client.world.WorldSlice.getLocalSectionIndex(cx, 1, cz)]);
            }
        }
    }

    // Materializes the biome instances from the cloned (already resolved) id arrays. The id arrays never
    // contain the "unresolved" sentinel (255) -- that was taken care of while cloning the section.
    private static Biome[] resolveBiomeArray(Biome[] reuse, me.sodiumlegacy.mod.client.world.cloned.ClonedChunkSection section) {
        byte[] ids = section.getBiomeIds();

        Biome[] biomes = reuse != null ? reuse : new Biome[256];

        for (int i = 0; i < 256; i++) {
            Biome biome = Biome.byId(ids[i] & 255);
            biomes[i] = biome != null ? biome : Biome.PLAINS;
        }

        return biomes;
    }

    /**
     * Returns the biome at the given world position. Vanilla 1.8.9 does not vary biomes vertically,
     * so the Y coordinate is ignored.
     */
    public Biome getBiome(int x, int y, int z) {
        int cx = ((x >> 4) - this.chunkOriginX);
        int cz = ((z >> 4) - this.chunkOriginZ);

        // Clamp to the slice's neighborhood (3x3 chunks); out-of-slice requests resolve to the nearest chunk.
        cx = net.minecraft.util.math.MathHelper.clamp(cx, 0, 2);
        cz = net.minecraft.util.math.MathHelper.clamp(cz, 0, 2);

        Biome[] biomes = this.chunkBiomes[(cz * 3) + cx];

        if (biomes == null) {
            return Biome.PLAINS;
        }

        return biomes[(z & 15) << 4 | (x & 15)];
    }
}
