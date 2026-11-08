package me.sodiumlegacy.mod.client.world;

import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import me.sodiumlegacy.mod.client.util.SectionPos;
import me.sodiumlegacy.mod.client.world.biome.BiomeColorCache;
import me.sodiumlegacy.mod.client.world.biome.BiomeColorSource;
import me.sodiumlegacy.mod.client.world.biome.BiomeColorView;
import me.sodiumlegacy.mod.client.world.biome.BiomeSlice;
import me.sodiumlegacy.mod.client.world.cloned.ChunkRenderContext;
import me.sodiumlegacy.mod.client.world.cloned.ClonedChunkSection;
import me.sodiumlegacy.mod.client.world.cloned.ClonedChunkSectionCache;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * <p>Takes a slice of world state (block states, biome and light data arrays) and copies the data for use in off-thread
 * operations. This allows chunk build tasks to see a consistent snapshot of chunk data at the exact moment the task was
 * created.</p>
 *
 * <p>World slices are not safe to use from multiple threads at once, but the data they contain is safe from modification
 * by the main client thread.</p>
 *
 * <p>Object pooling should be used to avoid huge allocations as this class contains many large arrays.</p>
 */
public final class WorldSlice implements BiomeColorView {
    private static final LightType[] LIGHT_TYPES = LightType.values();

    // The number of blocks in a section.
    private static final int SECTION_BLOCK_COUNT = 16 * 16 * 16;

    // The radius of blocks around the origin chunk that should be copied.
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;

    // The radius of chunks around the origin chunk that should be copied. With a 2-block neighbor
    // radius, every sampled position falls within ±16 blocks, so one chunk of padding is sufficient
    // (the upstream computed this with MathHelper#roundUpToMultiple, which does not exist on 1.8.9).
    private static final int NEIGHBOR_CHUNK_RADIUS = 1;

    // The number of sections on each axis of this slice.
    private static final int SECTION_ARRAY_LENGTH = 1 + (NEIGHBOR_CHUNK_RADIUS * 2);

    // The size of the (Local Section -> Resource) arrays.
    private static final int SECTION_ARRAY_SIZE = SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH;

    // The number of bits needed for each local X/Y/Z coordinate.
    private static final int LOCAL_XYZ_BITS = 4;

    // The default block state used for out-of-bounds access
    private static final BlockState EMPTY_BLOCK_STATE = Blocks.AIR.getDefaultState();

    // The world this slice has copied data from
    private final World world;

    // The accessor used for fetching biome data from the slice
    private final BiomeSlice biomeSlice;

    // The biome blend cache
    private final BiomeColorCache biomeColors;

    // (Local Section -> Block States) table.
    private final BlockState[][] blockArrays;

    // (Local Section -> Light Arrays) table.
    private final @Nullable ChunkNibbleArray[][] lightArrays;

    // (Local Section -> Block Entity) table.
    private final @Nullable Int2ReferenceMap<BlockEntity>[] blockEntityArrays;

    // The starting point from which this slice captures blocks
    private int originX, originY, originZ;

    // The bounds of the volume that this WorldSlice contains (inclusive)
    private int minX, minY, minZ, maxX, maxY, maxZ;

    public static ChunkRenderContext prepare(World world, SectionPos origin, ClonedChunkSectionCache sectionCache) {
        // 1.8.9 worlds span block Y 0..255 (16 sections)
        if (origin.getY() < 0 || origin.getY() > 15) {
            return null;
        }

        Chunk chunk = world.getChunk(origin.getX(), origin.getZ());
        ChunkSection section = chunk.getBlockStorage()[origin.getY()];

        // If the chunk section is absent or empty, simply terminate now. There will never be anything in this chunk
        // section to render, so we need to signal that a chunk render task shouldn't created. This saves a considerable
        // amount of time in queueing instant build tasks and greatly accelerates how quickly the world can be loaded.
        if (section == null || section.isEmpty()) {
            return null;
        }

        BlockBox volume = new BlockBox(origin.getMinX() - NEIGHBOR_BLOCK_RADIUS,
                origin.getMinY() - NEIGHBOR_BLOCK_RADIUS,
                origin.getMinZ() - NEIGHBOR_BLOCK_RADIUS,
                origin.getMaxX() + NEIGHBOR_BLOCK_RADIUS,
                origin.getMaxY() + NEIGHBOR_BLOCK_RADIUS,
                origin.getMaxZ() + NEIGHBOR_BLOCK_RADIUS);

        // The min/max bounds of the chunks copied by this slice
        final int minChunkX = origin.getX() - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkY = origin.getY() - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkZ = origin.getZ() - NEIGHBOR_CHUNK_RADIUS;

        final int maxChunkX = origin.getX() + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkY = origin.getY() + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkZ = origin.getZ() + NEIGHBOR_CHUNK_RADIUS;

        ClonedChunkSection[] sections = new ClonedChunkSection[SECTION_ARRAY_SIZE];

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    sections[getLocalSectionIndex(chunkX - minChunkX, chunkY - minChunkY, chunkZ - minChunkZ)] =
                            sectionCache.acquire(chunkX, chunkY, chunkZ);
                }
            }
        }

        return new ChunkRenderContext(origin, sections, volume);
    }

    @SuppressWarnings("unchecked")
    public WorldSlice(World world) {
        this.world = world;

        this.blockArrays = new BlockState[SECTION_ARRAY_SIZE][SECTION_BLOCK_COUNT];
        this.lightArrays = new ChunkNibbleArray[SECTION_ARRAY_SIZE][LIGHT_TYPES.length];

        this.blockEntityArrays = new Int2ReferenceMap[SECTION_ARRAY_SIZE];

        this.biomeSlice = new BiomeSlice();
        // 1.8.9 has no biome blend radius option; use vanilla's smooth lighting heuristic: blend a
        // little when fancy graphics are enabled. (TODO: revisit in the settings phase.)
        this.biomeColors = new BiomeColorCache(this.biomeSlice, MinecraftClient.getInstance().options.fancyGraphics ? 3 : 0);

        for (BlockState[] blockArray : this.blockArrays) {
            Arrays.fill(blockArray, EMPTY_BLOCK_STATE);
        }
    }

    public void copyData(ChunkRenderContext context) {
        this.originX = (context.getOrigin().getX() - NEIGHBOR_CHUNK_RADIUS) << 4;
        this.originY = (context.getOrigin().getY() - NEIGHBOR_CHUNK_RADIUS) << 4;
        this.originZ = (context.getOrigin().getZ() - NEIGHBOR_CHUNK_RADIUS) << 4;

        SectionPos origin = context.getOrigin();
        this.minX = origin.getMinX() - NEIGHBOR_BLOCK_RADIUS;
        this.minY = origin.getMinY() - NEIGHBOR_BLOCK_RADIUS;
        this.minZ = origin.getMinZ() - NEIGHBOR_BLOCK_RADIUS;
        this.maxX = origin.getMaxX() + NEIGHBOR_BLOCK_RADIUS;
        this.maxY = origin.getMaxY() + NEIGHBOR_BLOCK_RADIUS;
        this.maxZ = origin.getMaxZ() + NEIGHBOR_BLOCK_RADIUS;

        for (int x = 0; x < SECTION_ARRAY_LENGTH; x++) {
            for (int y = 0; y < SECTION_ARRAY_LENGTH; y++) {
                for (int z = 0; z < SECTION_ARRAY_LENGTH; z++) {
                    this.copySectionData(context, getLocalSectionIndex(x, y, z));
                }
            }
        }

        this.biomeSlice.update(this.world, context);
        this.biomeColors.update(context);
    }

    private void copySectionData(ChunkRenderContext context, int sectionIndex) {
        ClonedChunkSection section = context.getSections()[sectionIndex];

        Objects.requireNonNull(section, "Chunk section must be non-null");

        this.unpackBlockData(this.blockArrays[sectionIndex], section);

        this.lightArrays[sectionIndex][LightType.BLOCK.ordinal()] = section.getLightArray(LightType.BLOCK);
        this.lightArrays[sectionIndex][LightType.SKY.ordinal()] = section.getLightArray(LightType.SKY);

        this.blockEntityArrays[sectionIndex] = section.getBlockEntityMap();
    }

    private void unpackBlockData(BlockState[] blockArray, ClonedChunkSection section) {
        BlockState[] data = section.getBlockData();

        if (data == null) {
            Arrays.fill(blockArray, EMPTY_BLOCK_STATE);
            return;
        }

        System.arraycopy(data, 0, blockArray, 0, SECTION_BLOCK_COUNT);
    }

    public void reset() {
        // erase any pointers to resources we no longer need
        // no point in cleaning the pre-allocated arrays (such as block state storage) since we hold the
        // only reference.
        for (int sectionIndex = 0; sectionIndex < SECTION_ARRAY_SIZE; sectionIndex++) {
            Arrays.fill(this.lightArrays[sectionIndex], null);

            this.blockEntityArrays[sectionIndex] = null;
        }
    }

    public BlockState getBlockState(BlockPos pos) {
        return this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState getBlockState(int x, int y, int z) {
        if (!this.contains(x, y, z)) {
            return EMPTY_BLOCK_STATE;
        }

        int relX = x - this.originX;
        int relY = y - this.originY;
        int relZ = z - this.originZ;

        return this.blockArrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)]
                [getLocalBlockIndex(relX & 15, relY & 15, relZ & 15)];
    }

    public BlockEntity getBlockEntity(BlockPos pos) {
        return this.getBlockEntity(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockEntity getBlockEntity(int x, int y, int z) {
        if (!this.contains(x, y, z)) {
            return null;
        }

        int relX = x - this.originX;
        int relY = y - this.originY;
        int relZ = z - this.originZ;

        Int2ReferenceMap<BlockEntity> blockEntities = this.blockEntityArrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)];

        if (blockEntities == null) {
            return null;
        }

        return blockEntities.get(getLocalBlockIndex(relX & 15, relY & 15, relZ & 15));
    }

    // NOTE: not an @Override — 1.8.9 World queries light as a single combined level; this
    // per-type accessor is sodium's own API consumed by LightDataAccess.
    public int getLightLevel(LightType type, BlockPos pos) {
        if (!this.contains(pos.getX(), pos.getY(), pos.getZ())) {
            return 0;
        }

        int relX = pos.getX() - this.originX;
        int relY = pos.getY() - this.originY;
        int relZ = pos.getZ() - this.originZ;

        ChunkNibbleArray lightArray = this.lightArrays[getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)][type.ordinal()];

        int level = lightArray == null
                // If the array is null, it means the dimension for the current world does not support that light type
                ? 0
                : lightArray.get(relX & 15, relY & 15, relZ & 15);

        if (type == LightType.BLOCK) {
            // Dynamic lights (held / dropped torches...) add to the world's block light.
            int dynamic = me.sodiumlegacy.mod.client.dynamiclights.DynamicLights.getLevel(pos.getX(), pos.getY(), pos.getZ());

            if (dynamic > level) {
                level = dynamic;
            }
        }

        return level;
    }

    /**
     * 1.8.9 port: BlockView adapter support. Returns the biome at the given world position
     * (clamped to the slice's 3x3 chunk column neighborhood).
     */
    public Biome getBiome(BlockPos pos) {
        return this.biomeSlice.getBiome(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public int getColor(BiomeColorSource source, int x, int y, int z) {
        return this.biomeColors.getColor(source, x, y, z);
    }

    private boolean contains(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX
                && y >= this.minY && y <= this.maxY
                && z >= this.minZ && z <= this.maxZ;
    }

    public static int getLocalBlockIndex(int x, int y, int z) {
        return (y << LOCAL_XYZ_BITS << LOCAL_XYZ_BITS) | (z << LOCAL_XYZ_BITS) | x;
    }

    public static int getLocalSectionIndex(int x, int y, int z) {
        return (y * SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH) + (z * SECTION_ARRAY_LENGTH) + x;
    }
}
