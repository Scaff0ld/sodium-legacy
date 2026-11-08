package me.sodiumlegacy.lwjgl3shim.system.libffi;

/**
 * Minimal data-holder version of libffi's call interface (compile-only shim).
 * The LWJGL 3 libffi glue is not available on the MC 1.8.9 / LWJGL 2 runtime.
 */
public final class FFICIF {

    /** The calling convention (ffi_abi). */
    public long abi;

    /** Address of the return ffi_type. */
    public long rtype;

    /** Addresses of the argument ffi_types. */
    public long[] atypes;
}


