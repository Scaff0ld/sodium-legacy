package me.sodiumlegacy.lwjgl3shim.system;

import me.sodiumlegacy.lwjgl3shim.system.libffi.FFICIF;

/**
 * Base interface for LWJGL 3-style callbacks (compile-only shim).
 */
public interface CallbackI extends Pointer {

    /** The libffi call interface used to invoke this callback. */
    FFICIF getCallInterface();

    /** Entry point invoked by the native libffi trampoline. */
    void callback(long ret, long args);
}


