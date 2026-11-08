package me.sodiumlegacy.mod.mixin.features.render.world;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes the vanilla 1.8.9 "mouse delay" bug: clicks land on the path of a fast horizontal camera
 * flick instead of where the crosshair is.
 *
 * <p>{@code LivingEntity#getRotationVector(float)} builds the look vector from {@code headYaw} /
 * {@code prevHeadYaw}, but the mouse only changes {@code yaw}, every frame. The local player's head
 * yaw is only copied from {@code yaw} once per tick (20 Hz), so the aim used for picking the
 * targeted block/entity (and therefore for clicking) can be up to 50 ms behind the camera.</p>
 *
 * <p>For the local player we use the look vector of {@link Entity} instead, which is based on the
 * real {@code yaw}/{@code pitch}. Both branches of the vanilla method are covered (the
 * {@code tickDelta == 1} one used when a click is processed, and the interpolated one used while
 * rendering the targeted block outline). {@code headYaw} itself is untouched, so the player model,
 * and therefore F5, looks exactly like vanilla. Every other living entity keeps vanilla behavior,
 * since for mobs the head direction is the real look direction.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    public LivingEntityMixin(World world) {
        super(world);
    }

    @Inject(method = "getRotationVector(F)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void sodium$useRealYawForLocalPlayer(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            cir.setReturnValue(super.getRotationVector(tickDelta));
        }
    }
}
