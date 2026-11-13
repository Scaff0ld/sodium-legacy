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
            // No fog uniforms: record that the current fog can't be used for trimming or
            // uniform caching, so consumers know the state is not valid fog.
            if (!ChunkFogState.isCaptured()) {
                ChunkFogState.capture(-1, 1.0e9F, 1.0e10F, 0.0F, 0.0F, 0.0F, 0.0F);
            }
        }
    }

    public static class Smooth extends ChunkShaderFogComponent {
        private final GlUniformFloat4v uFogColor;

        private final GlUniformInt uFogShape;
        private final GlUniformInt uFogMode;
        private final GlUniformFloat uFogStart;
        private final GlUniformFloat uFogEnd;
        private final GlUniformFloat uFogDensity;

        public Smooth(ShaderBindingContext context) {
            this.uFogColor = context.bindUniform("u_FogColor", GlUniformFloat4v::new);
            this.uFogShape = context.bindUniform("u_FogShape", GlUniformInt::new);
            this.uFogMode = context.bindUniform("u_FogMode", GlUniformInt::new);
            this.uFogStart = context.bindUniform("u_FogStart", GlUniformFloat::new);
            this.uFogEnd = context.bindUniform("u_FogEnd", GlUniformFloat::new);
            this.uFogDensity = context.bindUniform("u_FogDensity", GlUniformFloat::new);
        }

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
            // terrain layers render: a LINEAR fog above ground, and an EXP fog
            // (water/lava/underground) otherwise. GL_FOG_MODE is an integer-typed GL state;
            // glGetFloat is invalid for it and produces GL_INVALID_ENUM (0x500) in core profile
            // contexts. Use glGetInteger.
            //
            // The state is read at most once per frame (see ChunkFogState) and reused for the
            // remaining passes, replacing per-pass GL reads and scratch float[] allocations.
            if (!ChunkFogState.isCaptured()) {
                int fogMode = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FOG_MODE);

                if (fogMode == ChunkFogState.GL_FOG_MODE_LINEAR) {
                    this.fogColorBuffer.clear();
                    me.sodiumlegacy.lwjgl3shim.opengl.GL11C.glGetFloatv(
                            me.sodiumlegacy.lwjgl3shim.opengl.GL11C.GL_FOG_COLOR, this.fogColorBuffer);
                    // Reads use absolute indices (glGetFloatv leaves position at 4, and absolute
                    // getters ignore position).
                    ChunkFogState.capture(fogMode,
                            me.sodiumlegacy.lwjgl3shim.opengl.GL11C.glGetFloat(me.sodiumlegacy.lwjgl3shim.opengl.GL11C.GL_FOG_START),
                            me.sodiumlegacy.lwjgl3shim.opengl.GL11C.glGetFloat(me.sodiumlegacy.lwjgl3shim.opengl.GL11C.GL_FOG_END),
                            0.0F, // GL_FOG_DENSITY is meaningless for LINEAR fog
                            this.fogColorBuffer.get(0),
                            this.fogColorBuffer.get(1),
                            this.fogColorBuffer.get(2));
                } else if (ChunkFogState.isRealFogMode(fogMode)) {
                    // EXP/EXP2 fog (water/lava/underground/blindness): the distance falloff is
                    // driven by the fog density, and the fog color is what vanilla blends toward.
                    this.fogColorBuffer.clear();
                    me.sodiumlegacy.lwjgl3shim.opengl.GL11C.glGetFloatv(
                            me.sodiumlegacy.lwjgl3shim.opengl.GL11C.GL_FOG_COLOR, this.fogColorBuffer);
                    ChunkFogState.capture(fogMode,
                            0.0F, // GL_FOG_START/END are meaningless for EXP fog
                            0.0F,
                            me.sodiumlegacy.lwjgl3shim.opengl.GL11C.glGetFloat(org.lwjgl.opengl.GL11.GL_FOG_DENSITY),
                            this.fogColorBuffer.get(0),
                            this.fogColorBuffer.get(1),
                            this.fogColorBuffer.get(2));
                } else {
                    // Unknown/absent fog mode: record an unreachable linear range so the shader
                    // applies no fog and the culler does not trim.
                    ChunkFogState.capture(-1, 1.0e9F, 1.0e10F, 0.0F, 0.0F, 0.0F, 0.0F);
                }
            }

            this.uFogMode.setInt(toShaderFogMode(ChunkFogState.getFogMode()));
            this.uFogStart.setFloat(ChunkFogState.getFogStart());
            this.uFogEnd.setFloat(ChunkFogState.getFogEnd());
            this.uFogDensity.setFloat(ChunkFogState.getFogDensity());
            this.uFogColor.set(ChunkFogState.getFogColor());
            this.uFogShape.setInt(FOG_SHAPE_SPHERICAL);
        }

        /**
         * Translates a fixed-function GL fog mode into the shader's {@code u_FogMode} encoding
         * (see fog.glsl). Unknown modes map to LINEAR; the capture records an unreachable
         * start/end for them, so they blend nothing.
         */
        private static int toShaderFogMode(int glFogMode) {
            if (glFogMode == ChunkFogState.GL_FOG_MODE_EXP) {
                return 1;
            }

            if (glFogMode == ChunkFogState.GL_FOG_MODE_EXP2) {
                return 2;
            }

            return 0;
        }
    }

}


