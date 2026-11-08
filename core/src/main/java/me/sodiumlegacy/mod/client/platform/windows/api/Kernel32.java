package me.sodiumlegacy.mod.client.platform.windows.api;

import org.jetbrains.annotations.Nullable;

/**
 * Kernel32 bindings (LWJGL 3 style).
 *
 * <p>The FFI machinery used by these bindings (dlopen'ed function pointers called through
 * libffi) does not exist on the MC 1.8.9 / LWJGL 2 runtime. The method bodies are therefore
 * stubbed: they throw {@link UnsupportedOperationException} as documented in
 * {@code docs/lwjgl3-shim-spec.md}. All public signatures are kept intact so that the
 * bootstrapping code (early driver checks, NVIDIA workarounds, module scanner) keeps compiling
 * and can fail gracefully.</p>
 */
public class Kernel32 {

    private static final String UNSUPPORTED =
            "Windows driver metadata diagnostics are not supported on this runtime yet (TODO 1.8.9)";

    public static void setEnvironmentVariable(String name, @Nullable String value) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    public static long getCommandLine() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    public static long getModuleHandleByNames(String[] names) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    public static long getModuleHandleByName(String name) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    public static String getModuleFileName(long phModule) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    public static int getLastError() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}
