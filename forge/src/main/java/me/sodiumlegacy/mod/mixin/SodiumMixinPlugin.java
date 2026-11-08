package me.sodiumlegacy.mod.mixin;

import me.sodiumlegacy.mod.client.data.config.MixinConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Forge-side copy of {@code me.sodiumlegacy.mod.mixin.SodiumMixinPlugin} (:core).
 *
 * <p>The shared :core version compiles against Mixin 0.8.5 (Fabric runtime uses
 * {@code org.objectweb.asm.tree.ClassNode}), but Forge 1.8.9 ships Mixin 0.7.11, which bundles a
 * <em>relocated</em> ASM as {@code org.spongepowered.asm.lib.*} and invokes
 * {@code preApply/postApply} with that type. Those two signatures can never be implemented by the
 * same class, so the Forge build excludes the :core copy and uses this one instead (same FQN, so
 * {@code sodiumlegacy.mixins.json} needs no change).</p>
 */
@SuppressWarnings("unused")
public class SodiumMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE_ROOT = "me.sodiumlegacy.mod.mixin.";

    private final Logger logger = LogManager.getLogger("SodiumLegacy");
    private MixinConfig config;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            this.config = MixinConfig.load(new File("./config/sodiumlegacy-mixins.properties"));
        } catch (Exception e) {
            throw new RuntimeException("Could not load configuration file for Sodium", e);
        }

        this.logger.info("Loaded configuration file for Sodium: {} options available, {} override(s) found",
                this.config.getOptionCount(), this.config.getOptionOverrideCount());
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(MIXIN_PACKAGE_ROOT)) {
            this.logger.error("Expected mixin '{}' to start with package root '{}', treating as foreign and " +
                    "disabling!", mixinClassName, MIXIN_PACKAGE_ROOT);

            return false;
        }

        String mixin = mixinClassName.substring(MIXIN_PACKAGE_ROOT.length());
        MixinOption option = this.config.getEffectiveOptionForMixin(mixin);

        if (option == null) {
            this.logger.error("No rules matched mixin '{}', treating as foreign and disabling!", mixin);

            return false;
        }

        if (option.isOverridden()) {
            String source = "[unknown]";

            if (option.isUserDefined()) {
                source = "user configuration";
            } else if (option.isModDefined()) {
                source = "mods [" + String.join(", ", option.getDefiningMods()) + "]";
            }

            if (option.isEnabled()) {
                this.logger.warn("Force-enabling mixin '{}' as rule '{}' (added by {}) enables it", mixin,
                        option.getName(), source);
            } else {
                this.logger.warn("Force-disabling mixin '{}' as rule '{}' (added by {}) disables it and children", mixin,
                        option.getName(), source);
            }
        }

        return option.isEnabled();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override // 0.7.11 API: ClassNode is the relocated org.spongepowered.asm.lib.tree.ClassNode
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
