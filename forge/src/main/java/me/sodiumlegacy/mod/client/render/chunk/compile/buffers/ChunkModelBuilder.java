package me.sodiumlegacy.mod.client.render.chunk.compile.buffers;

import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFacing;
import me.sodiumlegacy.mod.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public interface ChunkModelBuilder {
    ChunkMeshBufferBuilder getVertexBuffer(ModelQuadFacing facing);

    void addSprite(TextureAtlasSprite sprite);
}
