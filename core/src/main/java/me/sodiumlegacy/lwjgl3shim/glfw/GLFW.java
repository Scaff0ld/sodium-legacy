package me.sodiumlegacy.lwjgl3shim.glfw;

import org.lwjgl.Sys;

/**
 * LWJGL 3 GLFW shim (compile-only surface + minimal live behavior).
 *
 * <p>Sodium only needs {@link #glfwGetTime()} for its console renderer and
 * {@code glfwWindowHint} / {@code glfwCreateWindow} for window-creation
 * mixins, which are stubbed separately on the MC 1.8.9 port (LWJGL 2 has no
 * GLFW). Constants carry the LWJGL 3/GLFW numeric values.</p>
 */
public final class GLFW {

    public static final int GLFW_TRUE = 1;
    public static final int GLFW_FALSE = 0;

    public static final int GLFW_KEY_ESCAPE = 0x0100;
    public static final int GLFW_KEY_P = 0x0050;

    public static final int GLFW_MOD_SHIFT = 0x0001;

    public static final int GLFW_CONTEXT_NO_ERROR = 0x00022006;

    private GLFW() {
    }

    /** Backed by LWJGL 2's hi-res timer, in seconds. */
    public static double glfwGetTime() {
        return Sys.getTime() / (double) Sys.getTimerResolution();
    }

    public static long glfwCreateWindow(int width, int height, CharSequence title, long monitor, long share) {
        throw new UnsupportedOperationException("Not available on MC 1.8.9 / LWJGL2 (compile-only shim)");
    }

    public static void glfwWindowHint(int hint, int value) {
        throw new UnsupportedOperationException("Not available on MC 1.8.9 / LWJGL2 (compile-only shim)");
    }
}


