package me.sodiumlegacy.mod.client.zoom;

import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.gui.SodiumGameOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Camera zoom, bound to key C by default in the "Miscellaneous"
 * keybinding category.
 *
 * <p>Works by dividing the rendered field of view by the configured zoom factor
 * (see {@code GameRendererMixin}).</p>
 */
public final class ZoomFeature {
    public static final KeyBinding zoomKey = new KeyBinding("key.sodiumlegacy.zoom", Keyboard.KEY_C, "key.categories.misc");

    /**
     * Smooth-zoom animation speed, in exponential-approach units per second.
     * A value of ~30 means the transition mostly completes in about 100 ms.
     */
    private static final float SMOOTH_SPEED = 30.0F;

    private static final float CLOSE_ENOUGH = 1.0E-4F;

    // Zoom progress of the previous and current frame; interpolated with the frame's tick delta.
    private static float lastProgress;
    private static float progress;

    private static boolean toggled;

    // Tracks how much time passed between frames, for the smooth-zoom animation.
    private static long lastNanoTime;

    // True once the keybinding is visible in the Controls screen (GameSettings#allKeys).
    private static boolean registeredInOptions;

    private ZoomFeature() {
    }

    /**
     * Appends the zoom keybinding to {@link GameSettings#allKeys}. On 1.8.9 the
     * {@code KeyBinding} constructor only registers the key in its own static lists
     * (used for event dispatch and options.txt persistence); the Controls screen
     * otherwise lists the bindings stored in {@code GameSettings#allKeys}.
     *
     * <p>Returns true once successfully registered. The client entrypoint may run
     * before {@code Minecraft#options} exists, so callers should retry until
     * it returns true.</p>
     */
    public static boolean registerKeyBinding() {
        GameSettings options = Minecraft.getMinecraft().gameSettings;

        if (options == null) {
            return false;
        }

        for (KeyBinding key : options.keyBindings) {
            if (key == zoomKey) {
                return true;
            }
        }

        KeyBinding[] extended = new KeyBinding[options.keyBindings.length + 1];
        System.arraycopy(options.keyBindings, 0, extended, 0, options.keyBindings.length);
        extended[extended.length - 1] = zoomKey;
        options.keyBindings = extended;

        return true;
    }

    /**
     * Advances the (de)activation animation of the zoom. Called once per frame at the
     * beginning of {@code Minecraft#runGameLoop()}, before any rendering.
     */
    public static void update() {
        // The client entrypoint can run before Minecraft exists; retry until the
        // keybind shows up in the Controls screen.
        if (!registeredInOptions) {
            registeredInOptions = registerKeyBinding();
        }

        SodiumGameOptions.MiscellaneousSettings settings = SodiumClientMod.options().misc;

        float previous = progress;

        boolean held = isZoomActive(settings);
        float target = held ? 1.0F : 0.0F;

        if (settings.smoothZoom && lastNanoTime != 0L) {
            float deltaSeconds = Math.max(0.0F, (System.nanoTime() - lastNanoTime) * 1.0E-9F);
            float amount = 1.0F - (float) Math.exp(-deltaSeconds * SMOOTH_SPEED);

            progress += (target - progress) * amount;

            if (Math.abs(target - progress) < CLOSE_ENOUGH) {
                progress = target;
            }
        } else {
            progress = target;
        }

        lastProgress = previous;
        lastNanoTime = System.nanoTime();
    }

    /**
     * The current magnification amount (1 = no zoom, up to the configured zoom factor),
     * interpolated with the given frame tick delta. Ready to divide the camera FOV and
     * the mouse sensitivity by.
     */
    public static float getZoomAmount(float tickDelta) {
        if (progress <= CLOSE_ENOUGH) {
            return 1.0F;
        }

        int factor = Math.max(1, SodiumClientMod.options().misc.zoomFactor);

        if (factor == 1) {
            return 1.0F;
        }

        float current = lastProgress + (progress - lastProgress) * tickDelta;

        return 1.0F + (factor - 1) * current;
    }

    private static boolean isZoomActive(SodiumGameOptions.MiscellaneousSettings settings) {
        // While a screen is open the keyboard event queue is not distributed to
        // keybindings on 1.8.9, so a held key would keep zooming forever until the
        // player presses and releases it again after closing the screen.
        if (Minecraft.getMinecraft().currentScreen != null) {
            toggled = false;

            return false;
        }

        if (settings.zoomMode == SodiumGameOptions.MiscellaneousSettings.ZoomMode.HOLD) {
            // isKeyDown() = held state. In MCP 1.8.9 isPressed() CONSUMES one queued click
            // (fabric's wasPressed()), so it's only true for a single frame after the key goes down.
            return zoomKey.isKeyDown();
        }

        if (zoomKey.isPressed()) {
            toggled = !toggled;
        }

        return toggled;
    }
}