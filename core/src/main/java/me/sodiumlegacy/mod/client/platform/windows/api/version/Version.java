package me.sodiumlegacy.mod.client.platform.windows.api.version;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * File version information queries (Version.dll, LWJGL 3 style).
 *
 * <p>Resolving and invoking the native function pointers requires the libffi glue of LWJGL 3,
 * which is not available on the MC 1.8.9 / LWJGL 2 runtime. The query helpers are therefore
 * stubbed and throw {@link UnsupportedOperationException}; the public signatures are kept
 * intact so that the module scanner keeps compiling.</p>
 */
public class Version {

    private static final String UNSUPPORTED =
            "Windows driver metadata diagnostics are not supported on this runtime yet (TODO 1.8.9)";

    static @Nullable QueryResult query(ByteBuffer pBlock, String subBlock) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    public static @Nullable VersionInfo getModuleFileVersion(String filename) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}
