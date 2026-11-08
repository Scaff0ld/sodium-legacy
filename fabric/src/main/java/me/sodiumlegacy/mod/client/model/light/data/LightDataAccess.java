package me.sodiumlegacy.mod.client.model.light.data;

import me.sodiumlegacy.mod.client.world.WorldSlice;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;

/**
 * The light data cache is used to make accessing the light data and occlusion properties of blocks cheaper. The data
 * for each block is stored as an integer with packed fields in order to work around the lack of value types in Java.
 *
 * This code is not very pretty, but it does perform significantly faster than the vanilla implementation and has
 * good cache locality.
 *
 * Each integer contains the following fields:
 * - BL: World block light, encoded as a 4-bit unsigned integer
 * - SL: World sky light, encoded as a 4-bit unsigned integer
 * - LU: Block luminance, encoded as a 4-bit unsigned integer
 * - AO: Ambient occlusion, floating point value in the range of 0.0..1.0 encoded as a 16-bit unsigned integer with 12-bit precision
 * - EM: Emissive test, true if block uses emissive lighting (always false on 1.8.9, kept for compatibility)
 * - OP: Block opacity test, true if opaque
 * - FO: Full cube opacity test, true if opaque full cube
 * - FC: Full cube test, true if full cube
 *
 * You can use the various static pack/unpack methods to extract these values in a usable format.
 */
public abstract class LightDataAccess {
    /**
     * The fully-bright lightmap coordinate, equivalent to vanilla (1.17+)'s
     * {@code LightmapTextureManager.MAX_LIGHT_COORDINATE}. On 1.8.9 the combined light map coordinate is packed
     * as {@code (skyLight << 20) | (blockLight << 4)} with 4-bit light values, just as World#getLight does.
     */
    public static final int MAX_LIGHT_COORDINATE = 0xF000F0;

    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    protected WorldSlice world;

    public int get(int x, int y, int z, Direction d1, Direction d2) {
        return this.get(x + d1.getOffsetX() + d2.getOffsetX(),
                y + d1.getOffsetY() + d2.getOffsetY(),
                z + d1.getOffsetZ() + d2.getOffsetZ());
    }

    public int get(int x, int y, int z, Direction dir) {
        return this.get(x + dir.getOffsetX(),
                y + dir.getOffsetY(),
                z + dir.getOffsetZ());
    }

    public int get(BlockPos pos, Direction dir) {
        return this.get(pos.getX(), pos.getY(), pos.getZ(), dir);
    }

    public int get(BlockPos pos) {
        return this.get(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Returns the light data for the block at the given position. The property fields can then be accessed using
     * the various unpack methods below.
     */
    public abstract int get(int x, int y, int z);

    protected int compute(int x, int y, int z) {
        BlockPos pos = this.pos.setPosition(x, y, z);
        WorldSlice world = this.world;

        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // 1.8.9 has no per-block "emissive lighting" hook (introduced in 1.17)
        boolean em = false;
        boolean op = block.getOpacity() != 0;
        boolean fo = block.isFullBlock();
        boolean fc = block.isFullCube();

        int lu = block.getLightLevel();

        // OPTIMIZE: Do not calculate light data if the block is full and opaque and does not emit light.
        int bl;
        int sl;
        if (fo && lu == 0) {
            bl = 0;
            sl = 0;
        } else {
            bl = world.getLightLevel(LightType.BLOCK, pos);
            sl = world.getLightLevel(LightType.SKY, pos);
        }

        // FIX: Do not apply AO from blocks that emit light
        float ao;
        if (lu == 0) {
            ao = block.getAmbientOcclusionLightLevel();
        } else {
            ao = 1.0f;
        }

        return packFC(fc) | packFO(fo) | packOP(op) | packEM(em) | packAO(ao) | packLU(lu) | packSL(sl) | packBL(bl);
    }

    public static int packBL(int blockLight) {
        return blockLight & 0xF;
    }

    /**
     * Returns the per-face directional brightness applied by vanilla 1.8.9. The game hard-codes these multipliers
     * per facing (0.5 for DOWN, 1.0 for UP, 0.8 for NORTH/SOUTH and 0.6 for WEST/EAST); when the face is not
     * shaded, no brightness modification is applied.
     */
    public static float getBrightness(Direction face, boolean shade) {
        if (!shade) {
            return 1.0f;
        }

        switch (face) {
            case DOWN:
                return 0.5f;
            case UP:
                return 1.0f;
            case NORTH:
            case SOUTH:
                return 0.8f;
            default:
                return 0.6f;
        }
    }

    public static int unpackBL(int word) {
        return word & 0xF;
    }

    public static int packSL(int skyLight) {
        return (skyLight & 0xF) << 4;
    }

    public static int unpackSL(int word) {
        return (word >>> 4) & 0xF;
    }

    public static int packLU(int luminance) {
        return (luminance & 0xF) << 8;
    }

    public static int unpackLU(int word) {
        return (word >>> 8) & 0xF;
    }

    public static int packAO(float ao) {
        int aoi = (int) (ao * 4096.0f);
        return (aoi & 0xFFFF) << 12;
    }

    public static float unpackAO(int word) {
        int aoi = (word >>> 12) & 0xFFFF;
        return aoi * (1.0f / 4096.0f);
    }

    public static int packEM(boolean emissive) {
        return (emissive ? 1 : 0) << 28;
    }

    public static boolean unpackEM(int word) {
        return ((word >>> 28) & 0b1) != 0;
    }

    public static int packOP(boolean opaque) {
        return (opaque ? 1 : 0) << 29;
    }

    public static boolean unpackOP(int word) {
        return ((word >>> 29) & 0b1) != 0;
    }

    public static int packFO(boolean opaque) {
        return (opaque ? 1 : 0) << 30;
    }

    public static boolean unpackFO(int word) {
        return ((word >>> 30) & 0b1) != 0;
    }

    public static int packFC(boolean fullCube) {
        return (fullCube ? 1 : 0) << 31;
    }

    public static boolean unpackFC(int word) {
        return ((word >>> 31) & 0b1) != 0;
    }

    /**
     * Packs a block position into a single long for use as a hash table key.
     * (The 1.8.9 fork does not provide {@code BlockPos#asLong(int, int, int)}, so this is provided here.)
     */
    public static long packLong(int x, int y, int z) {
        return (((long) x & 0x3FFFFFF) << 38) | (((long) z & 0x3FFFFFF) << 12) | ((long) y & 0xFFF);
    }

    /**
     * Packs a block light and sky light level (both in the range 0..15) into the combined light map coordinate
     * used by the game's chunk vertex format. In vanilla 1.8.9 (and this port's fork), this packing matches
     * {@code World#getLight}: {@code (skyLight << 20) | (blockLight << 4)}.
     */
    public static int packLightmap(int blockLight, int skyLight) {
        return ((skyLight & 0xF) << 20) | ((blockLight & 0xF) << 4);
    }

    /**
     * Computes the combined lightmap using block light, sky light, and luminance values.
     *
     * <p>This method's logic is equivalent to vanilla's combined light lookup
     * ({@code Block#getBrightness(BlockView, BlockPos)} = {@code world.getLight(pos, luminance)}), but
     * without the emissive check.
     */
    public static int getLightmap(int word) {
        return packLightmap(Math.max(unpackBL(word), unpackLU(word)), unpackSL(word));
    }

    /**
     * Like {@link #getLightmap(int)}, but checks {@link #unpackEM(int)} first and returns
     * the {@link #MAX_LIGHT_COORDINATE fullbright lightmap} if emissive. (On 1.8.9 no block is ever
     * emissive, so this is always equivalent to {@link #getLightmap(int)}.)
     */
    public static int getEmissiveLightmap(int word) {
        if (unpackEM(word)) {
            return MAX_LIGHT_COORDINATE;
        } else {
            return getLightmap(word);
        }
    }

    public WorldSlice getWorld() {
        return this.world;
    }
}
