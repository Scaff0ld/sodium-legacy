package me.sodiumlegacy.mod.client.gui.options.binding.compat;

import me.sodiumlegacy.mod.client.gui.options.binding.OptionBinding;
import net.minecraft.client.option.GameOptions;
import org.lwjgl.opengl.Display;

/**
 * Compatibility binding for vanilla boolean options.
 *
 * 1.8.9 has no {@code SimpleOption}; instead the settings live as plain fields on
 * {@link GameOptions}. This binding therefore takes one of the vanilla
 * {@link GameOptions.Option} keys and maps the get/set calls onto the matching fields.
 */
public class VanillaBooleanOptionBinding implements OptionBinding<GameOptions, Boolean> {
    private final GameOptions.Option option;

    public VanillaBooleanOptionBinding(GameOptions.Option option) {
        this.option = option;
    }

    @Override
    public void setValue(GameOptions storage, Boolean value) {
        switch (this.option) {
            case VIEW_BOBBING:
                storage.bobView = value;
                break;
            case ENABLE_VSYNC:
                if (storage.vsync != value) {
                    storage.vsync = value;
                    Display.setVSyncEnabled(value);
                }
                break;
            case USE_FULLSCREEN:
                if (storage.fullscreen != value) {
                    storage.fullscreen = value;
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported vanilla boolean option: " + this.option);
        }
    }

    @Override
    public Boolean getValue(GameOptions storage) {
        switch (this.option) {
            case VIEW_BOBBING:
                return storage.bobView;
            case ENABLE_VSYNC:
                return storage.vsync;
            case USE_FULLSCREEN:
                return storage.fullscreen;
            default:
                return storage.getIntVideoOptions(this.option);
        }
    }
}
