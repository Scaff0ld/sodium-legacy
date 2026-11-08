package me.sodiumlegacy.mod.client.world.cloned;

import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap;
import me.sodiumlegacy.mod.client.util.SectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public class ClonedChunkSectionCache {
    private static final int MAX_CACHE_SIZE = 512; /* number of entries */
    private static final long MAX_CACHE_DURATION = TimeUnit.SECONDS.toNanos(5); /* number of nanoseconds */

    // Vanilla 1.8.9 worlds span block Y 0..255, i.e. 16 sections
    private static final int MAX_SECTION_Y = 15;

    private final World world;

    private final Long2ReferenceLinkedOpenHashMap<ClonedChunkSection> positionToEntry = new Long2ReferenceLinkedOpenHashMap<>();

    private long time; // updated once per frame to be the elapsed time since application start

    public ClonedChunkSectionCache(World world) {
        this.world = world;
        this.time = getMonotonicTimeSource();
    }

    public void cleanup() {
        this.time = getMonotonicTimeSource();
        this.positionToEntry.values()
                .removeIf(entry -> this.time > (entry.getLastUsedTimestamp() + MAX_CACHE_DURATION));
    }

    @Nullable
    public ClonedChunkSection acquire(int x, int y, int z) {
        long pos = SectionPos.asLong(x, y, z);
        ClonedChunkSection section = this.positionToEntry.getAndMoveToLast(pos);

        if (section == null) {
            section = this.clone(x, y, z);

            while (this.positionToEntry.size() >= MAX_CACHE_SIZE) {
                this.positionToEntry.removeFirst();
            }

            this.positionToEntry.putAndMoveToLast(pos, section);
        } else {
            // 1.8.9 port: a cached "empty" clone may have been taken while this chunk was still
            // being (re)delivered — it would poison every build that reads it (meshes full of
            // AIR) for up to MAX_CACHE_DURATION. If the live section now has content, drop the
            // stale snapshot and re-clone.
            if (section.getBlockData() == null && y >= 0 && y <= MAX_SECTION_Y) {
                ChunkSection live = this.world.getChunk(x, z).getBlockStorage()[y];

                if (live != null && !live.isEmpty()) {
                    section = this.clone(x, y, z);
                    this.positionToEntry.putAndMoveToLast(pos, section);
                }
            }
        }

        section.setLastUsedTimestamp(this.time);

        return section;
    }

    @NotNull
    private ClonedChunkSection clone(int x, int y, int z) {
        // World.getChunk always returns a (possibly empty) Chunk instance in 1.8.9
        Chunk chunk = this.world.getChunk(x, z);

        @Nullable ChunkSection section = null;

        if (y >= 0 && y <= MAX_SECTION_Y) {
            section = chunk.getBlockStorage()[y];
        }

        return new ClonedChunkSection(this.world, chunk, section, SectionPos.from(x, y, z));
    }

    public void invalidate(int x, int y, int z) {
        this.positionToEntry.remove(SectionPos.asLong(x, y, z));
    }

    private static long getMonotonicTimeSource() {
        return System.nanoTime();
    }
}
