package me.sodiumlegacy.lwjgl3shim.system;

/**
 * LWJGL 3-compatible {@code Pointer} interface for the compat shim layer.
 */
public interface Pointer {
    /** The NULL pointer value. */
    long NULL = 0L;

    /** The size of a pointer in bytes (64-bit JVM). */
    int POINTER_SIZE = 8;

    /** The shift to convert a pointer count to bytes (base-8). */
    int POINTER_SHIFT = 3;

    /** Returns the address of this pointer. */
    long address();

    /** Returns the address of {@code pointer}, or {@link #NULL} when null. */
    static long get(Pointer pointer) {
        return pointer == null ? NULL : pointer.address();
    }

    /** Same as {@link #get(Pointer)} (no checks are needed in the shim). */
    static long getSafe(Pointer pointer) {
        return get(pointer);
    }
}


