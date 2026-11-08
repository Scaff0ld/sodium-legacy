package me.sodiumlegacy.mod.mixin.features.textures.ctm;

import me.sodiumlegacy.mod.client.ctm.CtmCompat;
import me.sodiumlegacy.mod.client.ctm.CtmManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the block atlas stitching so connected-texture tiles are registered before the sprites
 * are loaded and stitched, and resolved right after.
 *
 * <p>The hook is on {@code TextureMap#loadTextureAtlas(IResourceManager)} (the loading + stitching
 * step, the one fabric's {@code method_7005} maps to). {@code loadSprites(IResourceManager,
 * IIconCreator)} must not be used: it clears the registered sprites at its start, so anything
 * registered at its HEAD is wiped before stitching.</p>
 */
@Mixin(TextureMap.class)
public class SpriteAtlasTextureMixin {
    @Inject(method = {"loadTextureAtlas"}, at = @At("HEAD")) // registered sprites are complete here and are read from it below
    private void sodiumlegacy$ctmBeforeStitch(IResourceManager manager, CallbackInfo ci) {
        if ((Object) this == CtmCompat.blockAtlas()) {
            CtmManager.beforeStitch((TextureMap) (Object) this);
        }
    }

    @Inject(method = {"loadTextureAtlas"}, at = @At("RETURN")) // stitch finished: sprites/animatedSprites are populated
    private void sodiumlegacy$ctmAfterStitch(IResourceManager manager, CallbackInfo ci) {
        if ((Object) this == CtmCompat.blockAtlas()) {
            CtmManager.afterStitch((TextureMap) (Object) this);
        }
    }
}