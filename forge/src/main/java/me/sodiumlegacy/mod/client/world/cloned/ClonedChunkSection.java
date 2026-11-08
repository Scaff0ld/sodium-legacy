package me.sodiumlegacy.mod.client.world.cloned;

import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import me.sodiumlegacy.mod.client.util.SectionPos;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.EnumSkyBlock;
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
    private final @Nullable IBlockState[] blockData;

    // 256 BiomeGenBase ids indexed by (z << 4 | x), fully resolved (no 255 sentinels); byte[256] always present
    private final byte[] biomeIds;

    // Light arrays indexed by EnumSkyBlock.ordinal(); entries are deep copies safe for off-thread reads
    private final @Nullable NibbleArray[] lightDataArrays;

    private final @Nullable Int2ReferenceMap<TileEntity> blockEntityMap;

    private long lastUsedTimestamp = Long.MAX_VALUE;

    public ClonedChunkSection(World world, Chunk chunk, @Nullable ExtendedBlockStorage section, SectionPos pos) {
        this.pos = pos;

        IBlockState[] blockData = null;
        Int2ReferenceMap<TileEntity> blockEntityMap = null;

        if (section != null && !section.isEmpty()) {
            blockData = copyBlockStates(section);
            blockEntityMap = copyBlockEntities(chunk, pos);
        }

        this.blockData = blockData;
        this.blockEntityMap = blockEntityMap;

        this.lightDataArrays = copyLightData(world, section);

        this.biomeIds = copyBiomeIds(world, chunk);
    }

    private static IBlockState[] copyBlockStates(ExtendedBlockStorage section) {
        char[] ids = section.getData();
        IBlockState[] states = new IBlockState[SECTION_BLOCK_COUNT];

        for (int i = 0; i < states.length; i++) {
            IBlockState state = Block.BLOCK_STATE_IDS.getByValue(ids[i]);
            states[i] = state != null ? state : Blocks.air.getDefaultState();
        }

        return states;
    }

    private static NibbleArray[] copyLightData(World world, @Nullable ExtendedBlockStorage section) {
        NibbleArray[] arrays = new NibbleArray[EnumSkyBlock.values().length];

        if (section != null) {
            NibbleArray blockLight = section.getBlocklightArray();
            NibbleArray skyLight = section.getSkylightArray();

            if (blockLight != null) {
                arrays[EnumSkyBlock.BLOCK.ordinal()] = deepCopy(blockLight);
            }

            if (skyLight != null) {
                arrays[EnumSkyBlock.SKY.ordinal()] = deepCopy(skyLight);
            }
        }

        boolean hasSkyLight = !world.provider.getHasNoSky();

        // Dimensions without sky-light should not get a default-initialized sky array
        if (arrays[EnumSkyBlock.BLOCK.ordinal()] == null) {
            arrays[EnumSkyBlock.BLOCK.ordinal()] = new NibbleArray(new byte[2048]);
        }

        if (hasSkyLight && arrays[EnumSkyBlock.SKY.ordinal()] == null) {
            byte[] bytes = new byte[2048];
            Arrays.fill(bytes, (byte) 255);
            arrays[EnumSkyBlock.SKY.ordinal()] = new NibbleArray(bytes);
        }

        return arrays;
    }

    private static NibbleArray deepCopy(NibbleArray array) {
        byte[] bytes = array.getData();
        return new NibbleArray(Arrays.copyOf(bytes, bytes.length));
    }

    /**
     * Snapshot-copies and fully resolves the chunk's BiomeGenBase array (BiomeGenBase ids are resolved to actual
     * values while on the main thread, so the render thread never touches the BiomeGenBase source).
     */
    private static byte[] copyBiomeIds(World world, Chunk chunk) {
        byte[] source = chunk.getBiomeArray();
        byte[] copied = new byte[BIOME_COLUMN_COUNT];

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getChunkCoordIntPair().chunkXPos << 4;
        int baseZ = chunk.getChunkCoordIntPair().chunkZPos << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int index = (z << 4) | x;

                int id = source[index] & 255;

                if (id == 255) {
                    // Unresolved sentinel: ask the world (main thread) to compute the BiomeGenBase
                    pos.set(baseX + x, 72, baseZ + z);
                    BiomeGenBase biome = world.getBiomeGenForCoords(pos);
                    id = biome.biomeID;
                }

                copied[index] = (byte) (id & 0xFF);
            }
        }

        return copied;
    }

    @Nullable
    private static Int2ReferenceMap<TileEntity> copyBlockEntities(Chunk chunk, SectionPos chunkCoord) {
        int minX = chunkCoord.getMinX(), minY = chunkCoord.getMinY(), minZ = chunkCoord.getMinZ();
        int maxX = chunkCoord.getMaxX(), maxY = chunkCoord.getMaxY(), maxZ = chunkCoord.getMaxZ();

        Int2ReferenceOpenHashMap<TileEntity> blockEntities = null;

        if (chunk.getTileEntityMap().isEmpty()) {
            return null;
        }

        // Copy the block entities from the chunk into our cloned section
        for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet()) {
            BlockPos pos = entry.getKey();
            TileEntity entity = entry.getValue();

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

    public @Nullable IBlockState[] getBlockData() {
        return this.blockData;
    }

    public byte[] getBiomeIds() {
        return this.biomeIds;
    }

    public @Nullable Int2ReferenceMap<TileEntity> getBlockEntityMap() {
        return this.blockEntityMap;
    }

    public @Nullable NibbleArray getLightArray(EnumSkyBlock lightType) {
        return this.lightDataArrays[lightType.ordinal()];
    }

    public long getLastUsedTimestamp() {
        return this.lastUsedTimestamp;
    }

    public void setLastUsedTimestamp(long timestamp) {
        this.lastUsedTimestamp = timestamp;
    }
}

