package me.sodiumlegacy.mod.client.platform.unix;

import org.jetbrains.annotations.Nullable;

/**
 * libc bindings (LWJGL 3 style).
 *
 * <p>Invoking native function pointers (and even resolving {@code libc.so.6} through the shim's
 * loader) is not possible on the MC 1.8.9 / LWJGL 2 runtime, so {@code setenv} cannot be called
 * from Java here. The method body is therefore stubbed and throws
 * {@link UnsupportedOperationException}; the public signature is kept intact so that the NVIDIA
 * workarounds keep compiling and can fail gracefully.</p>
 */
public class Libc {

    private static final String UNSUPPORTED =
            "Native environment variable helpers are not supported on this runtime yet (TODO 1.8.9)";

    public static void setEnvironmentVariable(String name, @Nullable String value) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}
