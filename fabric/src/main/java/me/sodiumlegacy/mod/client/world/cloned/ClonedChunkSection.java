package me.sodiumlegacy.mod.client.world.cloned;

import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import me.sodiumlegacy.mod.client.util.SectionPos;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.LightType;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * A copied snapshot of a single chunk section's state, taken on the main thread, for use by
 * off-thread chunk build tasks.
 */
public class ClonedChunkSection {
    private static final int SECTION_BLOCK_COUNT = 16 * 16 * 16;
    private static final int BIOME_COLUMN_COUNT = 256;

    private final SectionPos pos;

    // 4096 block states indexed by (y << 8 | z << 4 | x), or null when the section is empty
    private final @Nullable BlockState[] blockData;

    // 256 biome ids indexed by (z << 4 | x), fully resolved (no 255 sentinels); byte[256] always present
    private final byte[] biomeIds;

    // Light arrays indexed by LightType.ordinal(); entries are deep copies safe for off-thread reads
    private final @Nullable ChunkNibbleArray[] lightDataArrays;

    private final @Nullable Int2ReferenceMap<BlockEntity> blockEntityMap;

    private long lastUsedTimestamp = Long.MAX_VALUE;

    public ClonedChunkSection(World world, Chunk chunk, @Nullable ChunkSection section, SectionPos pos) {
        this.pos = pos;

        BlockState[] blockData = null;
        Int2ReferenceMap<BlockEntity> blockEntityMap = null;

        if (section != null && !section.isEmpty()) {
            blockData = copyBlockStates(section);
            blockEntityMap = copyBlockEntities(chunk, pos);
        }

        this.blockData = blockData;
        this.blockEntityMap = blockEntityMap;

        this.lightDataArrays = copyLightData(world, section);

        this.biomeIds = copyBiomeIds(world, chunk);
    }

    private static BlockState[] copyBlockStates(ChunkSection section) {
        char[] ids = section.getBlockStates();
        BlockState[] states = new BlockState[SECTION_BLOCK_COUNT];

        for (int i = 0; i < states.length; i++) {
            BlockState state = Block.BLOCK_STATES.fromId(ids[i]);
            states[i] = state != null ? state : Blocks.AIR.getDefaultState();
        }

        return states;
    }

    private static ChunkNibbleArray[] copyLightData(World world, @Nullable ChunkSection section) {
        ChunkNibbleArray[] arrays = new ChunkNibbleArray[LightType.values().length];

        if (section != null) {
            ChunkNibbleArray blockLight = section.getBlockLight();
            ChunkNibbleArray skyLight = section.getSkyLight();

            if (blockLight != null) {
                arrays[LightType.BLOCK.ordinal()] = deepCopy(blockLight);
            }

            if (skyLight != null) {
                arrays[LightType.SKY.ordinal()] = deepCopy(skyLight);
            }
        }

        boolean hasSkyLight = !world.dimension.hasNoSkylight();

        // Dimensions without sky-light should not get a default-initialized sky array
        if (arrays[LightType.BLOCK.ordinal()] == null) {
            arrays[LightType.BLOCK.ordinal()] = new ChunkNibbleArray(new byte[2048]);
        }

        if (hasSkyLight && arrays[LightType.SKY.ordinal()] == null) {
            byte[] bytes = new byte[2048];
            Arrays.fill(bytes, (byte) 255);
            arrays[LightType.SKY.ordinal()] = new ChunkNibbleArray(bytes);
        }

        return arrays;
    }

    private static ChunkNibbleArray deepCopy(ChunkNibbleArray array) {
        byte[] bytes = array.getValue();
        return new ChunkNibbleArray(Arrays.copyOf(bytes, bytes.length));
    }

    /**
     * Snapshot-copies and fully resolves the chunk's biome array (biome ids are resolved to actual
     * values while on the main thread, so the render thread never touches the biome source).
     */
    private static byte[] copyBiomeIds(World world, Chunk chunk) {
        byte[] source = chunk.getBiomeArray();
        byte[] copied = new byte[BIOME_COLUMN_COUNT];

        BlockPos.Mutable pos = new BlockPos.Mutable();
        int baseX = chunk.getChunkPos().x << 4;
        int baseZ = chunk.getChunkPos().z << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int index = (z << 4) | x;

                int id = source[index] & 255;

                if (id == 255) {
                    // Unresolved sentinel: ask the world (main thread) to compute the biome
                    pos.setPosition(baseX + x, 72, baseZ + z);
                    Biome biome = world.getBiome(pos);
                    id = biome != null ? biome.id : Biome.PLAINS.id;
                }

                copied[index] = (byte) (id & 0xFF);
            }
        }

        return copied;
    }

    @Nullable
    private static Int2ReferenceMap<BlockEntity> copyBlockEntities(Chunk chunk, SectionPos chunkCoord) {
        int minX = chunkCoord.getMinX(), minY = chunkCoord.getMinY(), minZ = chunkCoord.getMinZ();
        int maxX = chunkCoord.getMaxX(), maxY = chunkCoord.getMaxY(), maxZ = chunkCoord.getMaxZ();

        Int2ReferenceOpenHashMap<BlockEntity> blockEntities = null;

        if (chunk.getBlockEntities().isEmpty()) {
            return null;
        }

        // Copy the block entities from the chunk into our cloned section
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockEntity entity = entry.getValue();

            if (pos.getX() >= minX && pos.getX() <= maxX &&
                    pos.getY() >= minY && pos.getY() <= maxY &&
                    pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                if (blockEntities == null) {
                    blockEntities = new Int2ReferenceOpenHashMap<>();
                }

                blockEntities.put(me.sodiumlegacy.mod.client.world.WorldSlice.getLocalBlockIndex(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15), entity);
            }
        }

        if (blockEntities != null) {
            blockEntities.trim();
        }

        return blockEntities;
    }

    public SectionPos getPosition() {
        return this.pos;
    }

    public @Nullable BlockState[] getBlockData() {
        return this.blockData;
    }

    public byte[] getBiomeIds() {
        return this.biomeIds;
    }

    public @Nullable Int2ReferenceMap<BlockEntity> getBlockEntityMap() {
        return this.blockEntityMap;
    }

    public @Nullable ChunkNibbleArray getLightArray(LightType lightType) {
        return this.lightDataArrays[lightType.ordinal()];
    }

    public long getLastUsedTimestamp() {
        return this.lastUsedTimestamp;
    }

    public void setLastUsedTimestamp(long timestamp) {
        this.lastUsedTimestamp = timestamp;
    }
}
