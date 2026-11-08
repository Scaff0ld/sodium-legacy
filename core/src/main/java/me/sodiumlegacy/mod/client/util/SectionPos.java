package me.sodiumlegacy.mod.client.util;

/**
 * A coordinate helper for chunk section coordinates.
 *
 * Minecraft 1.8.9 stores chunks as 16 x 16 x 16 sections (a 256-high chunk is divided into 16
 * sections along the Y axis); it does not provide a {@code ChunkSectionPos} class like the 1.14+
 * versions do, so this utility provides the same packing/unpacking helpers with identical
 * semantics for 16-wide, 16-high sections.
 */
public final class SectionPos {
    private static final int SECTION_BITS = 4;
    private static final int SECTION_SIZE = 16;

    private final int x;
    private final int y;
    private final int z;

    private SectionPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static SectionPos from(int x, int y, int z) {
        return new SectionPos(x, y, z);
    }

    /**
     * @return The section coordinate that contains the given block coordinate
     */
    public static int getSectionCoord(int blockCoord) {
        return blockCoord >> SECTION_BITS;
    }

    public static long asLong(int x, int y, int z) {
        long l = 0L;
        // NOTE: every operand must be widened to long BEFORE shifting. With int operands Java
        // masks the shift count to 5 bits ("<< 42" behaves as "<< 10"), which made the fields
        // overlap and collapsed thousands of distinct sections onto a handful of keys.
        l |= ((long) x & 0x3FFFFFL) << 42;
        l |= ((long) y & 0xFFFFFL) << 20;
        l |= (long) z & 0xFFFFFL;
        return l;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public int getMinX() {
        return this.getX() << SECTION_BITS;
    }

    public int getMinY() {
        return this.getY() << SECTION_BITS;
    }

    public int getMinZ() {
        return this.getZ() << SECTION_BITS;
    }

    public int getMaxX() {
        return this.getMinX() + SECTION_SIZE - 1;
    }

    public int getMaxY() {
        return this.getMinY() + SECTION_SIZE - 1;
    }

    public int getMaxZ() {
        return this.getMinZ() + SECTION_SIZE - 1;
    }

    public long asLong() {
        return asLong(this.x, this.y, this.z);
    }
}
