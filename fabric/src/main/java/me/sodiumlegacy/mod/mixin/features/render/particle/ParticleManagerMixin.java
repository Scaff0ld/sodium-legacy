package me.sodiumlegacy.mod.mixin.features.render.particle;

import me.sodiumlegacy.mod.client.particle.ParticleFilter;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-particle on/off switches (see {@link ParticleFilter}).
 *
 * <p>Three seams cover everything vanilla 1.8.9 spawns:</p>
 * <ul>
 *   <li>{@code addParticle(int, double x6, int[])}: the id-based entry point used by the world,
 *       entities and particle emitters (crits). Returning {@code null} is what vanilla itself does
 *       for an unknown id, so every caller already handles it.</li>
 *   <li>{@code addBlockBreakParticles} / {@code addBlockBreakingParticles}: block break and block
 *       hit particles, which are created directly and never use an id.</li>
 *   <li>{@code addParticle(Particle)}: only used to drop firework particles, which are also
 *       created directly.</li>
 * </ul>
 */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    @Inject(method = "addParticle(IDDDDDD[I)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    private void sodium$filterParticleById(int id, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int[] args, CallbackInfoReturnable<Particle> cir) {
        if (ParticleFilter.isIdBlocked(id)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true)
    private void sodium$filterBlockBreak(CallbackInfo ci) {
        if (ParticleFilter.isBlockBreakBlocked()) {
            ci.cancel();
        }
    }

    @Inject(method = "addBlockBreakingParticles", at = @At("HEAD"), cancellable = true)
    private void sodium$filterBlockBreaking(CallbackInfo ci) {
        if (ParticleFilter.isBlockBreakingBlocked()) {
            ci.cancel();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void sodium$filterFirework(Particle particle, CallbackInfo ci) {
        if (ParticleFilter.isFireworkParticleBlocked(particle)) {
            ci.cancel();
        }
    }
}
