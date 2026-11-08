package me.sodiumlegacy.mod.client.world.cloned;

import me.sodiumlegacy.mod.client.util.SectionPos;
import me.sodiumlegacy.mod.client.util.BlockBox;

public class ChunkRenderContext {
    private final SectionPos origin;
    private final ClonedChunkSection[] sections;
    private final BlockBox volume;

    public ChunkRenderContext(SectionPos origin, ClonedChunkSection[] sections, BlockBox volume) {
        this.origin = origin;
        this.sections = sections;
        this.volume = volume;
    }

    public ClonedChunkSection[] getSections() {
        return this.sections;
    }

    public SectionPos getOrigin() {
        return this.origin;
    }

    public BlockBox getVolume() {
        return this.volume;
    }
}
