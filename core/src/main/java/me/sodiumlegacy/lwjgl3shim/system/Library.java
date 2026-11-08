package me.sodiumlegacy.lwjgl3shim.system;

/**
 * LWJGL 3-style native library loading shim (compile-only on MC 1.8.9 / LWJGL 2).
 *
 * <p>Runtime behavior is not provided: the LWJGL 2 runtime has no API-compatible
 * loader for this; the affected Sodium platform classes (windows/unix) are
 * stubbed separately.</p>
 */
public final class Library {

    private Library() {
    }

    public static SharedLibrary loadNative(String module, String name) {
        throw new UnsupportedOperationException("Not available on MC 1.8.9 / LWJGL2 (compile-only shim)");
    }

    public static SharedLibrary loadNative(String name) {
        return loadNative(null, name);
    }
}


