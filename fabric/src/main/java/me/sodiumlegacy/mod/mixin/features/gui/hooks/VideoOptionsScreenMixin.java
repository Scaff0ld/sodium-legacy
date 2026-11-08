package me.sodiumlegacy.mod.mixin.features.gui.hooks;

import me.sodiumlegacy.mod.client.gui.SodiumOptionsGUI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.VideoOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's Video Settings screen with sodium's own options GUI.
 * 1.8.9 opens the video settings directly as a {@link VideoOptionsScreen}, 
 * so hooking its init and swapping the screen is the equivalent one-time 
 * interception point.
 *
 * <p>{@code client} is declared on {@link Screen} (not on {@link VideoOptionsScreen}), so a
 * {@code @Shadow} of an inherited field cannot be resolved here (no refMap is loaded); the
 * client instance is fetched statically instead. The screen's own {@code parent} field IS
 * declared in the target class, so shadowing it is fine — sodium's GUI keeps it as its
 * previous screen so Done goes to the actual screen behind the video settings (fixing the
 * interception loop where Done → VideoOptionsScreen.init → sodium GUI again).</p>
 */
@Mixin(VideoOptionsScreen.class)
public class VideoOptionsScreenMixin {
    @Shadow private Screen parent;

    @Inject(method = "init()V", at = @At("HEAD"), cancellable = true)
    private void sodium$replaceWithSodiumOptions(CallbackInfo ci) {
        // Opening via setScreen destroys this screen instance immediately; the parent link
        // therefore carries over to sodium's GUI (Done returns to the screen that opened the
        // video settings, not to this intercepted screen).
        MinecraftClient.getInstance().setScreen(SodiumOptionsGUI.createScreen(this.parent));

        ci.cancel();
    }
}
