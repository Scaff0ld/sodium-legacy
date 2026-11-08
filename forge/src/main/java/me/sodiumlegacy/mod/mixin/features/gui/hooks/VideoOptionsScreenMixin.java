package me.sodiumlegacy.mod.mixin.features.gui.hooks;

import me.sodiumlegacy.mod.client.gui.SodiumOptionsGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's Video Settings screen with sodium's own options GUI.
 * 1.8.9 opens the video settings directly as a {@link GuiVideoSettings},
 * so hooking its {@code initGui} and swapping the screen is the equivalent one-time
 * interception point.
 *
 * <p>The screen's own {@code parentGuiScreen} field IS declared in the target class, so
 * shadowing it is fine - sodium's GUI keeps it as its previous screen so Done goes to the
 * actual screen behind the video settings (fixing the interception loop where Done ->
 * GuiVideoSettings.initGui -> sodium GUI again).</p>
 */
@Mixin(GuiVideoSettings.class)
public class VideoOptionsScreenMixin {
    @Shadow
    private GuiScreen parentGuiScreen;

    @Inject(method = "initGui", at = @At("HEAD"), cancellable = true)
    private void sodium$replaceWithSodiumOptions(CallbackInfo ci) {
        // Opening via displayGuiScreen destroys this screen instance immediately; the parent link
        // therefore carries over to sodium's GUI (Done returns to the screen that opened the
        // video settings, not to this intercepted screen).
        Minecraft.getMinecraft().displayGuiScreen(SodiumOptionsGUI.createScreen(this.parentGuiScreen));

        ci.cancel();
    }
}
