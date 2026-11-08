package me.sodiumlegacy.lwjgl3shim.system.libffi;

/**
 * Minimal placeholder constants for libffi (compile-only shim).
 *
 * <p>Only the members referenced by Sodium's callback interfaces are defined.
 * The real libffi glue of LWJGL 3 is not available on the MC 1.8.9 / LWJGL 2
 * runtime; ffi_type entries are placeholder addresses and function bodies in
 * {@link me.sodiumlegacy.lwjgl3shim.system.APIUtil} throw
 * {@code UnsupportedOperationException}.</p>
 */
public final class LibFFI {

    /** ffi_abi placeholder for the platform's default ABI (matches LWJGL 3's constant type/int value usage). */
    public static final int FFI_DEFAULT_ABI = 2;

    /** FFIType(void) placeholder address. */
    public static final long ffi_type_void = me.sodiumlegacy.lwjgl3shim.system.MemoryUtil.NULL;

    /** FFIType(void*) placeholder address. */
    public static final long ffi_type_pointer = me.sodiumlegacy.lwjgl3shim.system.MemoryUtil.NULL;

    private LibFFI() {
    }
}


