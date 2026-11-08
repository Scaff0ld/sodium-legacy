package me.sodiumlegacy.mod.mixin.features.world;

import me.sodiumlegacy.mod.client.dynamiclights.DynamicLights;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes dynamic lights affect everything vanilla lights through the world's combined light
 * ({@code (skyLight << 20) | (blockLight << 4)}): the first-person hand and held item, other
 * players, dropped items, mobs, block entities and particles. Terrain does not go through here (the
 * chunk builders read the light through {@code WorldSlice}), only per-frame entity-style rendering.
 *
 * <p>{@code require = 0}: if this method name does not match the mappings the mixin is skipped
 * instead of crashing the game; terrain lighting keeps working, only the item/entities stay unlit.</p>
 */
@Mixin(World.class)
public abstract class WorldLightMixin {
    @Inject(method = "getCombinedLight(Lnet/minecraft/util/BlockPos;I)I", at = @At("RETURN"), cancellable = true)
    private void sodium$addDynamicLight(BlockPos pos, int minLight, CallbackInfoReturnable<Integer> cir) {
        // Only the client world has dynamic lights
        if (!((Object) this instanceof WorldClient)) {
            return;
        }

        int dynamic = DynamicLights.getLevel(pos.getX(), pos.getY(), pos.getZ());

        if (dynamic <= 0) {
            return;
        }

        int packed = cir.getReturnValueI();
        int block = (packed >> 4) & 15;

        if (dynamic > block) {
            cir.setReturnValue((packed & ~(15 << 4)) | (dynamic << 4));
        }
    }
}
