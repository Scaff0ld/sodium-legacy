package me.sodiumlegacy.mod.platform.fabric;

import me.sodiumlegacy.mod.platform.ModOptionOverride;
import me.sodiumlegacy.mod.platform.ModPlatform;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Fabric implementation of {@link ModPlatform}.
 *
 * <p>Registered through {@code META-INF/services/me.sodiumlegacy.mod.platform.ModPlatform} so
 * the rest of the mod never depends on fabric-loader directly.
 */
public final class FabricModPlatform implements ModPlatform {
    private static final Logger LOGGER = LogManager.getLogger("SodiumLegacy");

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String getModVersion() {
        ModContainer mod = FabricLoader.getInstance()
                .getModContainer("sodiumlegacy")
                .orElseThrow(NullPointerException::new);

        return mod.getMetadata()
                .getVersion()
                .getFriendlyString();
    }

    @Override
    public List<ModOptionOverride> getMixinOptionOverrides() {
        List<ModOptionOverride> overrides = new ArrayList<>();

        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = container.getMetadata();

            if (meta.containsCustomValue(MIXIN_OPTIONS_KEY)) {
                CustomValue overrides_ = meta.getCustomValue(MIXIN_OPTIONS_KEY);

                if (overrides_.getType() != CustomValue.CvType.OBJECT) {
                    LOGGER.warn("Mod '{}' contains invalid Sodium option overrides, ignoring", meta.getId());
                    continue;
                }

                for (java.util.Map.Entry<String, CustomValue> entry : overrides_.getAsObject()) {
                    String option = entry.getKey();
                    CustomValue value = entry.getValue();

                    if (value.getType() != CustomValue.CvType.BOOLEAN) {
                        LOGGER.warn("Mod '{}' attempted to override option '{}' with an invalid value, ignoring",
                                meta.getId(), option);
                        continue;
                    }

                    overrides.add(new ModOptionOverride(meta.getId(), option, value.getAsBoolean()));
                }
            }
        }

        return overrides;
    }

    @Override
    public List<Object> getFrexFlawlessFramesEntrypoints() {
        try {
            List<?> entrypoints = FabricLoader.getInstance()
                    .getEntrypoints("frex_flawless_frames", Consumer.class);

            return Collections.unmodifiableList(new ArrayList<Object>(entrypoints));
        } catch (Exception e) {
            // Do not make initialization fail if the FREX entrypoints could not be loaded (this is
            // an optional feature which no hard dependency is declared for)
            return Collections.emptyList();
        }
    }
}
