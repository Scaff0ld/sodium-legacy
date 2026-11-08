package me.sodiumlegacy.mod.mixin.features.textures.ctm;

import me.sodiumlegacy.mod.client.ctm.CtmCompat;
import me.sodiumlegacy.mod.client.ctm.CtmManager;
import net.minecraft.client.resource.FallbackResourceManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceImpl;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;

/**
 * Serves the connected-texture tiles (generated glass tiles and PNGs copied out of resource
 * packs) to the atlas loader from memory, so they never need to exist as files.
 *
 * <p>VERIFY: {@code FallbackResourceManager}, {@code SimpleResource} (5-arg constructor:
 * pack name, id, stream, mcmeta stream, metadata serializer) and {@code Resource} names.</p>
 */
@Mixin(FallbackResourceManager.class)
public class FallbackResourceManagerMixin {
    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void sodiumlegacy$serveCtmTile(Identifier id, CallbackInfoReturnable<Resource> cir) {
        if (!"minecraft".equals(CtmCompat.namespaceOf(id))) {
            return;
        }

        byte[] png = CtmManager.getServedResource(CtmCompat.pathOf(id));

        if (png != null) {
            cir.setReturnValue(new ResourceImpl("sodiumlegacy", id, new ByteArrayInputStream(png), null, null));
        }
    }
}
