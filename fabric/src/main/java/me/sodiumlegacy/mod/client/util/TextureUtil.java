package me.sodiumlegacy.mod.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;

import java.lang.reflect.Field;

public class TextureUtil {

    /**
     * We deliberately do NOT go through the {@code TextureManager} by resource location: vanilla registers the
     * lightmap through {@code registerDynamicTexture("lightMap", ...)} with an auto-incremented index
     * ({@code dynamic/lightMap_1}, {@code dynamic/lightMap_2}, ...). Hardcoding {@code lightMap_1} breaks as soon
     * as another mod registers a dynamic texture before vanilla does, or the renderer is not initialized yet when
     * this class loads — either way {@code getTexture(id)} returns {@code null} and crashes with an NPE.
     *
     * {@code GameRenderer} maintains the GL texture id of the lightmap directly ({@code lightmapTextureId}); it
     * is read reflectively because it is private. Field names differ between the dev mappings (Yarn,
     * {@code lightmapTextureId}) and the production runtime (intermediary, {@code field_6454}), hence the
     * fallbacks.
     */
    private static Field lightmapTextureIdField;

    /**
     * @return The texture id of the lightmap texture, as bound on the lightmap texture unit
     */
    public static int getLightTextureId() {
        MinecraftClient client = MinecraftClient.getInstance();
        GameRenderer gameRenderer = client.gameRenderer;

        // Enable the lightmap before returning its id, so that it is always uploaded and bound like vanilla does
        gameRenderer.enableLightmap();

        try {
            return (int) getLightmapTextureIdField().get(gameRenderer);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read GameRenderer.lightmapTextureId", e);
        }
    }

    /**
     * @return The texture id of the block atlas, as bound on the block texture unit
     */
    public static int getBlockTextureId() {
        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        return textureManager.getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEX).getGlId();
    }

    private static Field getLightmapTextureIdField() {
        if (lightmapTextureIdField == null) {
            String[] names = {"lightmapTextureId", "field_6454"};
            for (String name : names) {
                try {
                    Field field = GameRenderer.class.getDeclaredField(name);
                    field.setAccessible(true);
                    lightmapTextureIdField = field;
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (lightmapTextureIdField == null) {
                throw new IllegalStateException("Could not locate the lightmap texture id field on " + GameRenderer.class.getName());
            }
        }
        return lightmapTextureIdField;
    }
}

