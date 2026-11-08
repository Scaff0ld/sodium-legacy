package me.sodiumlegacy.mod.client.render.texture;

import net.minecraft.client.texture.Sprite;
import org.jetbrains.annotations.Nullable;

public class SpriteUtil {
    public static void markSpriteActive(@Nullable Sprite sprite) {
        // no-op: sprite-animation tracking was removed in the 1.8.9 port; vanilla's
        // TextureManager ticks every animated sprite through its own global frame counter,
        // so sections no longer need to drive sprite updates themselves.
    }

    public static boolean hasAnimation(Sprite sprite) {
        // 1.8.9's Sprite exposes its animation metadata as `AnimationMetadata meta`
        // (null when the sprite has no animation).
        return sprite.hasMeta();
    }
}
