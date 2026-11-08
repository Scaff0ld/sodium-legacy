package me.sodiumlegacy.mod.platform;

import java.nio.file.Path;

/**
 * Loader-agnostic services provided by the mod loading platform (Fabric, Forge, ...).
 *
 * The implementation is resolved lazily through {@link java.util.ServiceLoader}, so it is
 * available as early as the mixin config plugin bootstrap (before any mod entrypoint runs).
 * Each loader ships its own implementation registered in
 * {@code META-INF/services/me.sodiumlegacy.mod.platform.ModPlatform}.
 */
public interface ModPlatform {
    /** Manifest key under which other mods may override mixin options (as a boolean object). */
    String MIXIN_OPTIONS_KEY = "sodiumlegacy:options";

    /**
     * The resolved platform implementation.
     *
     * @throws IllegalStateException if no implementation was registered on the classpath
     */
    static ModPlatform get() {
        ModPlatform platform = Holder.INSTANCE;

        if (platform == null) {
            throw new IllegalStateException("No ModPlatform implementation has been registered");
        }

        return platform;
    }

    /** Directory where the mod stores persistent configuration files. */
    Path getConfigDir();

    /** The version string of this mod, as declared in the loader's manifest. */
    String getModVersion();

    /**
     * Mixin option overrides declared by other mods, e.g. via a manifest custom value.
     * Only boolean overrides of existing option keys are reported; the precedence rules
     * are applied by the mixin configuration.
     *
     * @return a (possibly empty) list of overrides, never null
     */
    java.util.List<ModOptionOverride> getMixinOptionOverrides();

    /**
     * FREX "flawless frames" entrypoint objects (each implementing
     * {@code Consumer<Function<String, Consumer<Boolean>>>}). Optional feature; the
     * default implementation is suitable for loaders without an entrypoint registry.
     */
    default java.util.List<Object> getFrexFlawlessFramesEntrypoints() {
        return java.util.Collections.emptyList();
    }

    final class Holder {
        private static final ModPlatform INSTANCE;

        static {
            ModPlatform impl = null;

            try {
                java.util.ServiceLoader<ModPlatform> loader = java.util.ServiceLoader.load(ModPlatform.class);

                for (ModPlatform candidate : loader) {
                    if (impl != null) {
                        throw new IllegalStateException("Multiple ModPlatform implementations registered: "
                                + impl.getClass().getName() + ", " + candidate.getClass().getName());
                    }

                    impl = candidate;
                }
            } catch (Throwable t) {
                RuntimeException e = new RuntimeException("Failed to resolve ModPlatform implementation", t);
                e.printStackTrace();
                throw e;
            }

            INSTANCE = impl;
        }

        private Holder() {
        }
    }
}
