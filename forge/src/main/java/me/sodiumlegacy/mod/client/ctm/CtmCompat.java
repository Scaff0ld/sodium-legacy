package me.sodiumlegacy.mod.client.ctm;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;

/**
 * Every call into Minecraft 1.8.9 (MCP) whose name could vary between mappings lives heres.
 */
public final class CtmCompat {
    private CtmCompat() {
    }

    public static float minU(TextureAtlasSprite s) {
        return s.getMinU();
    }

    public static float maxU(TextureAtlasSprite s) {
        return s.getMaxU();
    }

    public static float minV(TextureAtlasSprite s) {
        return s.getMinV();
    }

    public static float maxV(TextureAtlasSprite s) {
        return s.getMaxV();
    }

    /**
     * Registers (or returns the already registered) sprite for the given location before stitching.
     * MCP 1.8.9: {@code TextureMap#registerSprite(ResourceLocation)}. (getAtlasSprite(String) does
     * NOT register anything: it only looks up stitched sprites and falls back to the purple/black
     * "missingno" sprite, which is what the connected textures were resolving to.)
     */
    public static TextureAtlasSprite registerSprite(TextureMap atlas, ResourceLocation id) {
        return atlas.registerSprite(id);
    }

    /** @return the stitched sprite registered under {@code name} (e.g. "minecraft:blocks/glass"), or null */
    public static TextureAtlasSprite getSprite(TextureMap atlas, String name) {
        TextureAtlasSprite sprite = atlas.getAtlasSprite(name);

        // 1.8.9 returns the missing-texture sprite instead of null for unknown names
        return sprite == atlas.getMissingSprite() ? null : sprite;
    }

    /** @return the block for a registry name such as "glass" or "minecraft:stained_glass", or null */
    public static Block blockByName(String name) {
        try {
            return Block.getBlockFromName(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return the block metadata (0-15) of a state */
    public static int metaOf(IBlockState state) {
        return state.getBlock().getMetaFromState(state);
    }

    public static String namespaceOf(ResourceLocation id) {
        return id.getResourceDomain();
    }

    public static String pathOf(ResourceLocation id) {
        return id.getResourcePath();
    }

    /** Opens a resource through the live resource manager (all active packs, highest priority wins). */
    public static InputStream openResource(ResourceLocation id) throws IOException {
        IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(id);
        return resource.getInputStream();
    }

    public static TextureMap blockAtlas() {
        return Minecraft.getMinecraft().getTextureMapBlocks();
    }

    public static Object resourceManager() {
        return Minecraft.getMinecraft().getResourceManager();
    }
}