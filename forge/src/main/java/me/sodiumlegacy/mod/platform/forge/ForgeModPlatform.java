package me.sodiumlegacy.mod.platform.forge;

import me.sodiumlegacy.mod.platform.ModOptionOverride;
import me.sodiumlegacy.mod.platform.ModPlatform;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * The Forge 1.8.9 implementation of {@link ModPlatform}.
 *
 * <p>Registered through {@code META-INF/services/me.sodiumlegacy.mod.platform.ModPlatform} so
 * the rest of the mod never depends on FML directly (the mixin configuration plugin resolves
 * this before any mod state event runs).</p>
 */
public final class ForgeModPlatform implements ModPlatform {
    private static volatile String cachedVersion;

    @Override
    public Path getConfigDir() {
        // Loader.instance() might not be ready at the earliest bootstrap point; fall back
        // to the vanilla conventions (working directory / "config").
        try {
            return Loader.instance().getConfigDir().toPath();
        } catch (Throwable t) {
            return Paths.get("config");
        }
    }

    @Override
    public String getModVersion() {
        if (cachedVersion != null) {
            return cachedVersion;
        }

        synchronized (ForgeModPlatform.class) {
            if (cachedVersion == null) {
                cachedVersion = readVersion();
            }
        }

        return cachedVersion;
    }

    /**
     * The version is stamped into {@code sodiumlegacy-forge.properties} at build time; the
     * runtime container's version may be the literal {@code ${version}} when the mcmod.info
     * substitution didn't run (dev classpath).
     */
    private static String readVersion() {
        ModContainer mod = Loader.instance().getIndexedModList().get("sodiumlegacy");

        if (mod != null) {
            String version = mod.getVersion();

            if (version != null && !version.contains("${")) {
                return version;
            }
        }

        try (InputStream in = ForgeModPlatform.class.getResourceAsStream("/sodiumlegacy-forge.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);

                String version = props.getProperty("version");
                if (version != null && !version.contains("${")) {
                    return version;
                }
            }
        } catch (Exception ignored) {
        }

        return "unknown";
    }

    /**
     * TODO(1.8.9): Forge 1.8.9's {@code ModMetadata} has no custom-value section other mods can
     * declare overrides in; until a manifest convention is agreed on, no overrides resolve.
     */
    @Override
    public List<ModOptionOverride> getMixinOptionOverrides() {
        return Collections.emptyList();
    }
}
