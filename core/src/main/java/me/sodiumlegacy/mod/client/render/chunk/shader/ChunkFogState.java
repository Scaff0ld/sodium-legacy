package me.sodiumlegacy.mod.client.render.chunk.shader;

/**
 * Captures vanilla's fixed-function fog state once per frame so it can be reused cheaply
 * everywhere else.
 *
 * <p>1.8.9 has no {@code RenderSystem} fog state; the authoritative fog source is the
 * fixed-function GL state, which is only guaranteed to hold the terrain fog values while
 * the terrain layers are being rendered (vanilla reconfigures it right before each layer).
 * This class provides two consumers:</p>
 *
 * <ul>
 *   <li><b>The chunk fog shader component</b> captures the state on the first {@code setup()}
 *       of each frame ({@link #beginFrame()} invalidates the capture) and reuses it for the
 *       remaining passes, replacing up to 5 GL reads plus scratch allocations per pass with
 *       plain field reads. It also receives the fog <em>mode</em> (LINEAR / EXP / EXP2) and
 *       density, so underwater EXP fog can be reproduced by the chunk shader instead of being
 *       silently dropped (which used to leave entities fogged by vanilla's fixed-function
 *       pipeline while the terrain rendered clear — the "dark entities underwater" mismatch).</li>
 *   <li><b>The occlusion culler</b> (via the render section manager) consumes the values
 *       captured on the <em>previous</em> frame to trim its visibility graph search against
 *       the fog end (upstream Sodium's "fog occlusion" optimization). One frame of lag is
 *       harmless: the fog state only changes on view-distance changes or underwater/blindness
 *       transitions, and a stale (shorter) end at worst hides fully fogged sections for a
 *       single frame.</li>
 * </ul>
 */
public final class ChunkFogState {
    // Fixed-function GL fog modes (see the OpenGL 1.x spec / LWJGL GL11).
    public static final int GL_FOG_MODE_LINEAR = 0x2601;
    public static final int GL_FOG_MODE_EXP = 0x0800;
    public static final int GL_FOG_MODE_EXP2 = 0x8001;

    // Per-frame uniform cache (valid after the first fog setup() of the frame).
    private static boolean capturedThisFrame;
    private static int fogMode = -1; // one of the GL_FOG_MODE_* constants above
    private static float fogStart;
    private static float fogEnd;
    private static float fogDensity;
    private static final float[] FOG_COLOR = new float[4];

    private ChunkFogState() { }

    /** Invalidates the per-frame capture; called once per frame before the layers render. */
    public static void beginFrame() {
        capturedThisFrame = false;
    }

    /** True when {@link #getFogStart()}/{@link #getFogEnd()}/{@link #getFogColor()} hold values. */
    public static boolean isCaptured() {
        return capturedThisFrame;
    }

    /**
     * Records the fog state read from GL. {@code fogMode} is the raw {@code GL_FOG_MODE} value
     * ({@code -1} when the fog pipeline is disabled entirely); {@code r}/{@code g}/{@code b} are
     * the raw fog color components. The exposed color gets its alpha (the shader's blend weight)
     * set to 1 for real fog modes and to 0 otherwise.
     */
    public static void capture(int fogMode, float start, float end, float density, float r, float g, float b) {
        capturedThisFrame = true;
        ChunkFogState.fogMode = fogMode;
        fogStart = start;
        fogEnd = end;
        fogDensity = density;

        if (isRealFogMode(fogMode)) {
            FOG_COLOR[0] = r;
            FOG_COLOR[1] = g;
            FOG_COLOR[2] = b;
            FOG_COLOR[3] = 1.0f;
        } else {
            FOG_COLOR[0] = 0.0f;
            FOG_COLOR[1] = 0.0f;
            FOG_COLOR[2] = 0.0f;
            FOG_COLOR[3] = 0.0f;
        }
    }

    /** True when the fog mode produces a blendable fixed-function fog. */
    public static boolean isRealFogMode(int fogMode) {
        return fogMode == GL_FOG_MODE_LINEAR || fogMode == GL_FOG_MODE_EXP || fogMode == GL_FOG_MODE_EXP2;
    }

    /** True when the last captured fog is LINEAR and {@link #getFogEnd()} is meaningful for trimming. */
    public static boolean isLinearFog() {
        return fogMode == GL_FOG_MODE_LINEAR;
    }

    public static int getFogMode() {
        return fogMode;
    }

    public static float getFogStart() {
        return fogStart;
    }

    public static float getFogEnd() {
        return fogEnd;
    }

    public static float getFogDensity() {
        return fogDensity;
    }

    /** The fog color with its blend weight in alpha. Do not mutate. */
    public static float[] getFogColor() {
        return FOG_COLOR;
    }
}
