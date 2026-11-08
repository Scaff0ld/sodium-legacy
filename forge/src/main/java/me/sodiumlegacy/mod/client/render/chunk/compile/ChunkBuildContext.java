package me.sodiumlegacy.mod.client.render.chunk.compile;

import me.sodiumlegacy.mod.client.render.chunk.vertex.format.ChunkVertexType;
import me.sodiumlegacy.mod.client.render.chunk.compile.pipeline.BlockRenderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

public class ChunkBuildContext {
    public final ChunkBuildBuffers buffers;
    public final BlockRenderCache cache;

    public ChunkBuildContext(WorldClient world, ChunkVertexType vertexType) {
        this.buffers = new ChunkBuildBuffers(vertexType);
        this.cache = new BlockRenderCache(Minecraft.getMinecraft(), world);
    }

    public void cleanup() {
        this.buffers.destroy();
        this.cache.cleanup();
    }
}
