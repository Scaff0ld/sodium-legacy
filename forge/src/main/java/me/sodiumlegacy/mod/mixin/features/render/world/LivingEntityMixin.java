package me.sodiumlegacy.mod.mixin.features.render.world;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes the vanilla 1.8.9 "mouse delay" bug: clicks land on the path of a fast horizontal camera
 * flick instead of where the crosshair is.
 *
 * <p>{@code EntityLivingBase#getLook(float)} builds the look vector from {@code rotationYawHead} /
 * {@code prevRotationYawHead}, but the mouse only changes {@code rotationYaw}, every frame. The
 * local player's head yaw is only copied from {@code rotationYaw} once per tick (20 Hz), so the
 * aim used for picking the targeted block/entity (and therefore for clicking) can be up to 50 ms
 * behind the camera.</p>
 *
 * <p>For the local player we use the look vector of {@link Entity} instead (same approach as the
 * fabric module), which is based on the real {@code rotationYaw}/{@code rotationPitch} and covers
 * both the {@code partialTicks == 1} and the interpolated branch. {@code rotationYawHead} itself is
 * untouched, so the player model, and therefore F5, looks exactly like vanilla. Every other living
 * entity keeps vanilla behavior.</p>
 */
@Mixin(EntityLivingBase.class)
public abstract class LivingEntityMixin extends Entity {
    public LivingEntityMixin(World world) {
        super(world);
    }

    @Inject(method = "getLook", at = @At("HEAD"), cancellable = true)
    private void sodium$useRealYawForLocalPlayer(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if ((Object) this == Minecraft.getMinecraft().getRenderViewEntity()) {
            cir.setReturnValue(super.getLook(partialTicks));
        }
    }
}