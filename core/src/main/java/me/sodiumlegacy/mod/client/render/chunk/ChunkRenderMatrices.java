package me.sodiumlegacy.mod.client.render.chunk;

import org.joml.Matrix4fc;

/**
 * Carries the view matrices required to render a frame's chunk geometry.
 *
 * <p>On 1.8.9 there is no {@code RenderSystem#getProjectionMatrix} /
 * {@link net.minecraft MatrixStack} matrix stack to copy from; the owning renderer (SodiumWorldRenderer)
 * builds both matrices directly from the camera and passes them here.</p>
 */
public final class ChunkRenderMatrices {
    private final Matrix4fc projection;
    private final Matrix4fc modelView;

    public ChunkRenderMatrices(Matrix4fc projection, Matrix4fc modelView) {
        this.projection = projection;
        this.modelView = modelView;
    }

    public static ChunkRenderMatrices from(Matrix4fc projection, Matrix4fc modelView) {
        return new ChunkRenderMatrices(projection, modelView);
    }

    public Matrix4fc projection() {
        return this.projection;
    }

    public Matrix4fc modelView() {
        return this.modelView;
    }
}
