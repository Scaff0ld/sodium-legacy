package me.sodiumlegacy.mod.mixin.features.options.overlays;

import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.gui.console.ConsoleHooks;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;isFancyGraphicsEnabled()Z"))
    private boolean redirectFancyGraphicsVignette() {
        return SodiumClientMod.options().quality.enableVignette;
    }

    /**
     * Draws sodium's on-screen console messages at the end of the HUD pass, which keeps them
     * inside the 2D/scaled GUI rendering state (upstream 0.5 hooks the 1.20 render loop through
     * {@code ConsoleHooks}; 1.8.9's fixed-function frame has no equivalent hook, and the HUD is
     * the context that provides the scaled matrices the text renderer expects).
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void drawConsole(CallbackInfo ci) {
        ConsoleHooks.render(System.nanoTime() / 1_000_000_000.0D);
    }
}
