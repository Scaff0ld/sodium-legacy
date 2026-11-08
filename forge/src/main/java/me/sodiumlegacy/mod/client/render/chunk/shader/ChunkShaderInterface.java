package me.sodiumlegacy.mod.client.render.chunk.shader;

import net.minecraft.client.renderer.GlStateManager;
import me.sodiumlegacy.mod.client.gl.shader.uniform.GlUniformFloat3v;
import me.sodiumlegacy.mod.client.gl.shader.uniform.GlUniformInt;
import me.sodiumlegacy.mod.client.gl.shader.uniform.GlUniformMatrix4f;
import me.sodiumlegacy.mod.client.render.texture.AtlasFilterState;
import me.sodiumlegacy.mod.client.util.TextureUtil;
import org.joml.Matrix4fc;

import java.util.EnumMap;
import java.util.Map;

/**
 * A forward-rendering shader program for chunks.
 */
public class ChunkShaderInterface {
    private static final int GL_TEXTURE0 = 0x84C0;

    private final Map<ChunkShaderTextureSlot, GlUniformInt> uniformTextures;

    private final GlUniformMatrix4f uniformModelViewMatrix;
    private final GlUniformMatrix4f uniformProjectionMatrix;
    private final GlUniformFloat3v uniformRegionOffset;

    // The fog shader component used by this program in order to setup the appropriate GL state
    private final ChunkShaderFogComponent fogShader;

    public ChunkShaderInterface(ShaderBindingContext context, ChunkShaderOptions options) {
        this.uniformModelViewMatrix = context.bindUniform("u_ModelViewMatrix", GlUniformMatrix4f::new);
        this.uniformProjectionMatrix = context.bindUniform("u_ProjectionMatrix", GlUniformMatrix4f::new);
        this.uniformRegionOffset = context.bindUniform("u_RegionOffset", GlUniformFloat3v::new);

        this.uniformTextures = new EnumMap<>(ChunkShaderTextureSlot.class);
        this.uniformTextures.put(ChunkShaderTextureSlot.BLOCK, context.bindUniform("u_BlockTex", GlUniformInt::new));
        this.uniformTextures.put(ChunkShaderTextureSlot.LIGHT, context.bindUniform("u_LightTex", GlUniformInt::new));

        this.fogShader = options.fog().getFactory().apply(context);
    }

    @Deprecated // the shader interface should not modify pipeline state
    public void setupState() {
        this.bindTexture(ChunkShaderTextureSlot.BLOCK, TextureUtil.getBlockTextureId());
        this.bindTexture(ChunkShaderTextureSlot.LIGHT, TextureUtil.getLightTextureId());

        this.fogShader.setup();

        // 1.8.9 vanilla expects the active texture unit to stay on GL_TEXTURE0 between render
        // layers; bindTexture leaves it at GL_TEXTURE1 (the lightmap slot).
        GlStateManager.setActiveTexture(GL_TEXTURE0);

        // The block atlas is bound on unit 0 now: apply the mipmap type / anisotropic filtering options.
        AtlasFilterState.applyToBoundAtlas();
    }

    @Deprecated // should be handled properly in GFX instead.
    private void bindTexture(ChunkShaderTextureSlot slot, int textureId) {
        // 1.8.9 GlStateManager tracks its active-texture state for us (no underscore-prefixed
        // methods like the modern versions).
        GlStateManager.setActiveTexture(GL_TEXTURE0 + slot.ordinal());
        GlStateManager.bindTexture(textureId);

        GlUniformInt uniform = this.uniformTextures.get(slot);
        uniform.setInt(slot.ordinal());
    }

    public void setProjectionMatrix(Matrix4fc matrix) {
        this.uniformProjectionMatrix.set(matrix);
    }

    public void setModelViewMatrix(Matrix4fc matrix) {
        this.uniformModelViewMatrix.set(matrix);
    }

    public void setRegionOffset(float x, float y, float z) {
        this.uniformRegionOffset.set(x, y, z);
    }
}

