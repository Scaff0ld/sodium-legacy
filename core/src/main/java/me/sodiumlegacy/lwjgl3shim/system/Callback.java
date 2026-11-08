package me.sodiumlegacy.lwjgl3shim.system;

/**
 * Base class for LWJGL 3-style callbacks (compile-only shim). Only the surface
 * used by Sodium's message-box callback types is implemented; there is no
 * native callback trampoline on the MC 1.8.9 / LWJGL 2 runtime.
 */
public abstract class Callback implements Pointer {

    protected final long functionPointer;

    protected Callback(long functionPointer) {
        this.functionPointer = functionPointer;
    }

    /** The native function pointer backing this callback. */
    @Override
    public long address() {
        return this.functionPointer;
    }

    /** No native resource corresponds to a shim callback; nothing to release. */
    public void free() {
        // no-op
    }
}


