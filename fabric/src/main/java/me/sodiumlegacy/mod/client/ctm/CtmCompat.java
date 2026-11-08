package me.sodiumlegacy.mod.client.ctm;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;

/**
 * Every call into Minecraft / Legacy Yarn 1.8.9 whose name could not be verified without
 * compiling lives here, so a wrong mapping name is fixed in this single file.
 *
 * <p>Names to double-check against Legacy Yarn build 604 if the compiler complains:
 * {@code Sprite#getMinU/getMaxU/getMinV/getMaxV}, {@code SpriteAtlasTexture#registerSprite},
 * {@code Block.get(String)}, {@code Block#getMeta(BlockState)}, {@code Identifier#getNamespace/getPath},
 * {@code MinecraftClient#getResourceManager}, {@code Resource#getInputStream}.</p>
 */
public final class CtmCompat {
    private CtmCompat() {
    }

    public static float minU(Sprite s) {
        return s.getMinU();
    }

    public static float maxU(Sprite s) {
        return s.getMaxU();
    }

    public static float minV(Sprite s) {
        return s.getMinV();
    }

    public static float maxV(Sprite s) {
        return s.getMaxV();
    }

    /** Registers (or returns the already registered) sprite for the given id before stitching. */
    public static Sprite registerSprite(SpriteAtlasTexture atlas, Identifier id) {
        // Legacy Yarn 1.8.9: SpriteAtlasTexture#getSprite(Identifier) is vanilla's
        // registerSprite (TextureMap#getAtlasSprite slot for pre-stitch registration) --
        // it creates the Sprite and puts it into spritesToLoad if absent.
        return atlas.getSprite(id);
    }

    /** @return the stitched sprite registered under {@code name} ("ns:path"), or null */
    public static Sprite getSprite(SpriteAtlasTexture atlas, String name) {
        return atlas.getSprite(name);
    }

    /** @return the block for a registry name such as "glass" or "minecraft:stained_glass", or null */
    public static Block blockByName(String name) {
        try {
            return Block.get(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return the block metadata (0-15) of a state */
    public static int metaOf(BlockState state) {
        return state.getBlock().getMeta(state);
    }

    public static String namespaceOf(Identifier id) {
        return id.getNamespace();
    }

    public static String pathOf(Identifier id) {
        return id.getPath();
    }

    /** Opens a resource through the live resource manager (all active packs, highest priority wins). */
    public static InputStream openResource(Identifier id) throws IOException {
        return MinecraftClient.getInstance().getResourceManager().getResource(id).getInputStream();
    }

    public static SpriteAtlasTexture blockAtlas() {
        return MinecraftClient.getInstance().getSpriteAtlasTexture();
    }

    public static Object resourceManager() {
        return MinecraftClient.getInstance().getResourceManager();
    }
}
