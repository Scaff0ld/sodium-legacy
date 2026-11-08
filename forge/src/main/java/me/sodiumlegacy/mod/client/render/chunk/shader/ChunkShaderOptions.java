package me.sodiumlegacy.mod.client.render.chunk.shader;

import me.sodiumlegacy.mod.client.gl.shader.ShaderConstants;
import me.sodiumlegacy.mod.client.render.chunk.terrain.TerrainRenderPass;
import me.sodiumlegacy.mod.client.render.chunk.vertex.format.ChunkVertexType;

import java.util.Objects;

public final class ChunkShaderOptions {
    private final ChunkFogMode fog;
    private final TerrainRenderPass pass;
    private final ChunkVertexType vertexType;

    public ChunkShaderOptions(ChunkFogMode fog, TerrainRenderPass pass, ChunkVertexType vertexType) {
        this.fog = fog;
        this.pass = pass;
        this.vertexType = vertexType;
    }

    public ChunkFogMode fog() {
        return this.fog;
    }

    public TerrainRenderPass pass() {
        return this.pass;
    }

    public ChunkVertexType vertexType() {
        return this.vertexType;
    }

    public ShaderConstants constants() {
        ShaderConstants.Builder constants = ShaderConstants.builder();
        constants.addAll(this.fog.getDefines());

        if (this.pass.supportsFragmentDiscard()) {
            constants.add("USE_FRAGMENT_DISCARD");
        }

        constants.add("USE_VERTEX_COMPRESSION"); // TODO: allow compact vertex format to be disabled
        constants.add("VERT_POS_SCALE", String.valueOf(this.vertexType.getPositionScale()));
        constants.add("VERT_POS_OFFSET", String.valueOf(this.vertexType.getPositionOffset()));
        constants.add("VERT_TEX_SCALE", String.valueOf(this.vertexType.getTextureScale()));

        return constants.build();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ChunkShaderOptions &&
                Objects.equals(this.fog, ((ChunkShaderOptions) obj).fog) &&
                Objects.equals(this.pass, ((ChunkShaderOptions) obj).pass) &&
                Objects.equals(this.vertexType, ((ChunkShaderOptions) obj).vertexType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.fog, this.pass, this.vertexType);
    }
}
