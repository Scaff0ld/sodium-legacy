package me.sodiumlegacy.mod.client.gui.options.binding.compat;

import me.sodiumlegacy.mod.client.gui.options.binding.OptionBinding;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.Display;

/**
 * Compatibility binding for vanilla boolean options.
 *
 * 1.8.9 has no {@code SimpleOption}; instead the settings live as plain fields on
 * {@link GameSettings}. This binding therefore takes one of the vanilla
 * {@link GameSettings.Options} keys and maps the get/set calls onto the matching fields.
 */
public class VanillaBooleanOptionBinding implements OptionBinding<GameSettings, Boolean> {
    private final GameSettings.Options option;

    public VanillaBooleanOptionBinding(GameSettings.Options option) {
        this.option = option;
    }

    @Override
    public void setValue(GameSettings storage, Boolean value) {
        switch (this.option) {
            case VIEW_BOBBING:
                storage.viewBobbing = value;
                break;
            case ENABLE_VSYNC:
                if (storage.enableVsync != value) {
                    storage.enableVsync = value;
                    Display.setVSyncEnabled(value);
                }
                break;
            case USE_FULLSCREEN:
                if (storage.fullScreen != value) {
                    storage.fullScreen = value;
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported vanilla boolean option: " + this.option);
        }
    }

    @Override
    public Boolean getValue(GameSettings storage) {
        switch (this.option) {
            case VIEW_BOBBING:
                return storage.viewBobbing;
            case ENABLE_VSYNC:
                return storage.enableVsync;
            case USE_FULLSCREEN:
                return storage.fullScreen;
            default:
                return storage.getOptionOrdinalValue(this.option);
        }
    }
}

