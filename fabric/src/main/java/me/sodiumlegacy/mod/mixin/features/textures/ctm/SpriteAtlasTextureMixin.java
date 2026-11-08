package me.sodiumlegacy.mod.mixin.features.textures.ctm;

import me.sodiumlegacy.mod.client.ctm.CtmCompat;
import me.sodiumlegacy.mod.client.ctm.CtmManager;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the block atlas stitching so connected-texture tiles are registered before the sprites
 * are loaded and stitched, and resolved right after.
 *
 */
@Mixin(SpriteAtlasTexture.class)
public class SpriteAtlasTextureMixin {
    @Inject(method = {"method_7005"}, at = @At("HEAD")) // loadSprites: spritesToLoad is already cleared, sprites are read from it below
    private void sodiumlegacy$ctmBeforeStitch(ResourceManager manager, CallbackInfo ci) {
        if ((Object) this == CtmCompat.blockAtlas()) {
            CtmManager.beforeStitch((SpriteAtlasTexture) (Object) this);
        }
    }

    @Inject(method = {"method_7005"}, at = @At("RETURN")) // stitch finished: sprites/animatedSprites are populated
    private void sodiumlegacy$ctmAfterStitch(ResourceManager manager, CallbackInfo ci) {
        if ((Object) this == CtmCompat.blockAtlas()) {
            CtmManager.afterStitch((SpriteAtlasTexture) (Object) this);
        }
    }
}
