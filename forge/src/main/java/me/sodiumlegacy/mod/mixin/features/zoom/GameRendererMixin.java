package me.sodiumlegacy.mod.mixin.features.zoom;

import me.sodiumlegacy.mod.client.zoom.ZoomFeature;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements the OptiFine-style zoom inside the world rendering pass.
 *
 * <p>{@code getFOVModifier} is the only place the camera's field of view is computed, 
 * so dividing the resulting value is enough to magnify the view.</p>
 */
@Mixin(EntityRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
    private void applyZoomFov(float tickDelta, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        float zoomAmount = ZoomFeature.getZoomAmount(tickDelta);

        if (zoomAmount != 1.0F) {
            cir.setReturnValue(cir.getReturnValueF() / zoomAmount);
        }
    }
}
