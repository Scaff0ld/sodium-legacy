package me.sodiumlegacy.mod.mixin.features.render.entity.cull;

import me.sodiumlegacy.mod.client.render.SodiumWorldRenderer;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a second visibility pass for entities against sodium's section visibility graph.
 *
 * <p>The 1.8.9 {@code EntityRenderer#shouldRender(T, CameraView, double, double, double)} has no
 * injected frustum check to hook after, so we instead modify the final return value: if the
 * renderer decided the entity is visible, re-check against sodium's culler in case the terrain
 * sections it would appear in front of are all hidden.</p>
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void preShouldRender(T entity, CameraView cameraView, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();

        if (renderer == null) {
            return;
        }

        boolean vanillaVisible = cir.getReturnValue();

        // If the entity isn't culled already by other means, try to perform a second pass
        boolean sodiumVisible = !vanillaVisible || renderer.isEntityVisible(entity);

        if (!sodiumVisible) {
            cir.setReturnValue(false);
        }
    }
}
