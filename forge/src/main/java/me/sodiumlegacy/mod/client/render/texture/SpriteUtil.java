package me.sodiumlegacy.mod.client.render.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

public class SpriteUtil {
    public static void markSpriteActive(@Nullable TextureAtlasSprite TextureAtlasSprite) {
        // no-op: TextureAtlasSprite-animation tracking was removed in the 1.8.9 port; vanilla's
        // TextureManager ticks every animated TextureAtlasSprite through its own global frame counter,
        // so sections no longer need to drive TextureAtlasSprite updates themselves.
    }

    public static boolean hasAnimation(TextureAtlasSprite TextureAtlasSprite) {
        // 1.8.9's TextureAtlasSprite exposes its animation metadata as `AnimationMetadata meta`
        // (null when the TextureAtlasSprite has no animation).
        return TextureAtlasSprite.hasAnimationMetadata();
    }
}
