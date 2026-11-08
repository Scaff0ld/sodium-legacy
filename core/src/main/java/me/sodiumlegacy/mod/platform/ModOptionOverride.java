package me.sodiumlegacy.mod.platform;

/**
 * A single mixin option override expressed by another mod, in loader-neutral form.
 */
public final class ModOptionOverride {
    private final String modId;
    private final String option;
    private final boolean enabled;

    public ModOptionOverride(String modId, String option, boolean enabled) {
        this.modId = modId;
        this.option = option;
        this.enabled = enabled;
    }

    /** The id of the mod declaring the override. */
    public String getModId() {
        return this.modId;
    }

    /** The declared mixin rule name (e.g. "features.render.world.clouds"). */
    public String getOption() {
        return this.option;
    }

    /** The requested state of the rule. */
    public boolean isEnabled() {
        return this.enabled;
    }
}
