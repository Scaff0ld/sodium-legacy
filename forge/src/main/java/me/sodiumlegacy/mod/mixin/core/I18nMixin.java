package me.sodiumlegacy.mod.mixin.core;

import me.sodiumlegacy.mod.client.util.SodiumLang;
import net.minecraft.client.resources.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets vanilla translation lookups resolve sodium's own lang keys (see {@link SodiumLang}
 * for why the 1.8.9 language pipeline cannot see Fabric mod {@code lang/*.json} bundles).
 *
 * <p>Only keys present in sodium's bundles take over (all prefixed with {@code sodiumlegacy.});
 * every other lookup (vanilla {@code key.categories.*}, {@code options.*}, ...) falls through to
 * the vanilla {@code TranslationStorage}. This is what makes the zoom keybinding show up as
 * "Zoom" instead of the raw key in the Controls screen.</p>
 */
@Mixin(I18n.class)
public abstract class I18nMixin {
    @Inject(method = "format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void sodiumTranslate(String key, Object[] args, CallbackInfoReturnable<String> cir) {
        String text = SodiumLang.lookupOrNull(key);

        if (text != null) {
            cir.setReturnValue(args.length == 0 ? text : String.format(text, args));
        }
    }
}
