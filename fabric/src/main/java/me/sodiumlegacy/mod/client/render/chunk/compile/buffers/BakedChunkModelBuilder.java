package me.sodiumlegacy.mod.client.render.chunk.compile.buffers;

import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFacing;
import me.sodiumlegacy.mod.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import me.sodiumlegacy.mod.client.render.chunk.data.BuiltSectionInfo;
import net.minecraft.client.texture.Sprite;

public class BakedChunkModelBuilder implements ChunkModelBuilder {
    private final ChunkMeshBufferBuilder[] vertexBuffers;

    private BuiltSectionInfo.Builder renderData;

    public BakedChunkModelBuilder(ChunkMeshBufferBuilder[] vertexBuffers) {
        this.vertexBuffers = vertexBuffers;
    }

    @Override
    public ChunkMeshBufferBuilder getVertexBuffer(ModelQuadFacing facing) {
        return this.vertexBuffers[facing.ordinal()];
    }

    @Override
    public void addSprite(Sprite sprite) {
        this.renderData.addSprite(sprite);
    }

    public void destroy() {
        for (ChunkMeshBufferBuilder builder : this.vertexBuffers) {
            builder.destroy();
        }
    }

    public void begin(BuiltSectionInfo.Builder renderData, int sectionIndex) {
        this.renderData = renderData;

        for (ChunkMeshBufferBuilder vertexBuffer : this.vertexBuffers) {
            vertexBuffer.start(sectionIndex);
        }
    }
}
