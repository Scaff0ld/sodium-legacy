package me.sodiumlegacy.lwjgl3shim.system;

/** LWJGL 3-style handle for a loaded native/shared library (compile-only shim). */
public interface SharedLibrary {

    /** Resolves a native function pointer; 0/undefined when not found. */
    long getFunctionAddress(CharSequence name);
}


