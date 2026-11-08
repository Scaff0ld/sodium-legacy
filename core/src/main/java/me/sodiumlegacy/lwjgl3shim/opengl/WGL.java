package me.sodiumlegacy.lwjgl3shim.opengl;

/**
 * LWJGL 3 WGL shim (compile-only). LWJGL 2 has no WGL class (verified via
 * javap), so the guarantee cannot be delegated; Sodium's context-replacement
 * detection mixin resolves the call site to a stubbed path at runtime.
 */
public final class WGL {

    private WGL() {
    }

    public static long wglGetCurrentContext() {
        throw new UnsupportedOperationException("Not available on MC 1.8.9 / LWJGL2 (compile-only shim)");
    }
}

