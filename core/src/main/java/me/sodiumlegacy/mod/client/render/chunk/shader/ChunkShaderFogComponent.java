package me.sodiumlegacy.mod.client.render.chunk.shader;

import me.sodiumlegacy.mod.client.gl.shader.uniform.GlUniformFloat;
import me.sodiumlegacy.mod.client.gl.shader.uniform.GlUniformFloat4v;
import me.sodiumlegacy.mod.client.gl.shader.uniform.GlUniformInt;

/**
 * These shader implementations try to remain compatible with the deprecated fixed function pipeline by manually
 * copying the state into each shader's uniforms. The shader code itself is a straight-forward implementation of the
 * fog functions themselves from the fixed-function pipeline, except that they use the distance from the camera
 * rather than the z-buffer to produce better looking fog that doesn't move with the player's view angle.
 *
 * Minecraft itself will actually try to enable distance-based fog by using the proprietary NV_fog_distance extension,
 * but as the name implies, this only works on graphics cards produced by NVIDIA. The shader implementation however does
 * not depend on any vendor-specific extensions and is written using very simple GLSL code.
 */
public abstract class ChunkShaderFogComponent {
    public abstract void setup();

    public static class None extends ChunkShaderFogComponent {
        public None(ShaderBindingContext context) {

        }

        @Override
        public void setup() {

        }
    }

    public static class Smooth extends ChunkShaderFogComponent {
        private final GlUniformFloat4v uFogColor;

        private final GlUniformInt uFogShape;
        private final GlUniformFloat uFogStart;
        private final GlUniformFloat uFogEnd;

        public Smooth(ShaderBindingContext context) {
            this.uFogColor = context.bindUniform("u_FogColor", GlUniformFloat4v::new);
            this.uFogShape = context.bindUniform("u_FogShape", GlUniformInt::new);
            this.uFogStart = context.bindUniform("u_FogStart", GlUniformFloat::new);
            this.uFogEnd = context.bindUniform("u_FogEnd", GlUniformFloat::new);
        }

        private static final int GL_LINEAR = me.sodiumlegacy.lwjgl3shim.opengl.GL11C.GL_LINEAR;
        private static final int FOG_SHAPE_SPHERICAL = 0; // see fog.glsl

        // LWJGL 2's BufferChecks requires query buffers with remaining >= 16 elements
        // (the maximum a glGetFloatv query can return), even though GL_FOG_COLOR only
        // writes 4. So the scratch buffer must be 16 floats, never limited to 4.
        private final java.nio.FloatBuffer fogColorBuffer = java.nio.ByteBuffer
                .allocateDirect(16 * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer();

        @Override
        public void setup() {
            // 1.8.9 has no RenderSystem fog state; the authoritative fog source is vanilla's
            // fixed-function GL state. GameRenderer.renderFog configures it right before the
            // terrain layers render: a LINEAR fog (start = viewDistance * 0.75, end =
            // viewDistance) above ground, and an EXP fog (water/lava/underground) otherwise.
            // GL_FOG_MODE is an integer-typed GL state; glGetFloat is invalid for it and
            // produces GL_INVALID_ENUM (0x500) in core profile contexts. Use glGetInteger.
            int fogMode = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FOG_MODE);

            if (fogMode == GL_LINEAR) {
                this.uFogStart.setFloat(me.sodiumlegacy.lwjgl3shim.opengl.GL11C.glGetFloat(me.sodiumlegacy.lwjgl3shim.opengl.GL11C.GL_FOG_START));
                this.uFogEnd.setFloat(me.sodiumlegacy.lwjgl3shim.opengl.GL11C.glGetFloat(me.sodiumlegacy.lwjgl3shim.opengl.GL11C.GL_FOG_END));

                this.fogColorBuffer.clear();
                me.sodiumlegacy.lwjgl3shim.opengl.GL11C.glGetFloatv(me.sodiumlegacy.lwjgl3shim.opengl.GL11C.GL_FOG_COLOR, this.fogColorBuffer);
                // The shader uses fogColor.a as the blend weight; fixed-function fog has no
                // per-color alpha, so the weight is always full. Reads use absolute indices
                // (glGetFloatv leaves position at 4, and absolute getters ignore position).
                this.uFogColor.set(new float[] {
                        this.fogColorBuffer.get(0),
                        this.fogColorBuffer.get(1),
                        this.fogColorBuffer.get(2),
                        1.0F
                });

                this.uFogShape.setInt(FOG_SHAPE_SPHERICAL);
            } else {
                // EXP-mode fog (water/lava/underground/blindness) is not modeled by the linear
                // shader component yet. Push the range beyond any reachable distance so no fog
                // is blended instead of leaving unset (passthrough-by-luck) uniforms behind.
                this.uFogStart.setFloat(1.0e9F);
                this.uFogEnd.setFloat(1.0e10F);
                this.uFogColor.set(new float[] { 0.0F, 0.0F, 0.0F, 0.0F });
                this.uFogShape.setInt(FOG_SHAPE_SPHERICAL);
            }
        }
    }

}


