package me.sodiumlegacy.lwjgl3shim.glfw;

/**
 * LWJGL 3 GLFW native binding shim (compile-only); no GLFW exists on the
 * MC 1.8.9 / LWJGL 2 runtime, so nothing real can be provided.
 */
public final class GLFWNativeWin32 {

    private GLFWNativeWin32() {
    }

    public static long glfwGetWin32Window(long window) {
        throw new UnsupportedOperationException("Not available on MC 1.8.9 / LWJGL2 (compile-only shim)");
    }
}


