package me.sodiumlegacy.lwjgl3shim.system;

import me.sodiumlegacy.lwjgl3shim.system.libffi.FFICIF;

/**
 * API helpers (LWJGL 3 shim). Library loading / function resolution / libffi
 * CIF creation cannot work on the MC 1.8.9 / LWJGL 2 runtime; the affected
 * Sodium platform classes (windows/unix) are stubbed separately.
 */
public final class APIUtil {

    private APIUtil() {
    }

    /** Loads a shared library (LWJGL 3 behavior: by module or plain name). */
    public static SharedLibrary apiCreateLibrary(String name) {
        return Library.loadNative(null, name);
    }

    /** Resolves a function pointer from a shared library. */
    public static long apiGetFunctionAddress(SharedLibrary library, CharSequence name) {
        throw new UnsupportedOperationException("Not available on MC 1.8.9 / LWJGL2 (compile-only shim)");
    }

    /**
     * Creates a libffi call interface. Signature mirrors the LWJGL 3 helper:
     * the abi and ffi_type addresses are long placeholder constants in this shim.
     */
    public static FFICIF apiCreateCIF(long abi, long rtype, long... atypes) {
        throw new UnsupportedOperationException("Not available on MC 1.8.9 / LWJGL2 (compile-only shim)");
    }

    /**
     * Frees an nmemAlloc'd array of nmemAlloc'd pointers (whatever the array
     * elements reference is released element-wise; the array itself must live
     * in caller-owned memory such as a MemoryStack frame).
     */
    public static void apiArrayFree(long address, int count) {
        for (int i = 0; i < count; i++) {
            long ptr = MemoryUtil.memGetAddress(address + i * Pointer.POINTER_SIZE);
            if (ptr != MemoryUtil.NULL) {
                MemoryUtil.nmemFree(ptr);
            }
        }
    }
}


