package me.sodiumlegacy.mod.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.Texture;
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
     * <p>The GL id lives on {@code GameRenderer#lightmapTexture} (a {@code NativeImageBackedTexture}, whose base
     * class {@code Texture} exposes {@code getGlId()}). Note: with current Legacy Yarn mappings,
     * {@code GameRenderer.lightmapTextureId} is the <em>resource location</em> of the dynamic texture (an
     * {@code Identifier}), not the GL id, so the id must be taken from the texture object itself. Field names
     * differ between the dev mappings (Yarn, {@code lightmapTexture}) and the production runtime (intermediary,
     * {@code field_6453}), hence the fallbacks.</p>
     */
    private static Field lightmapTextureField;

    /**
     * @return The texture id of the lightmap texture, as bound on the lightmap texture unit
     */
    public static int getLightTextureId() {
        MinecraftClient client = MinecraftClient.getInstance();
        GameRenderer gameRenderer = client.gameRenderer;

        // Enable the lightmap before returning its id, so that it is always uploaded and bound like vanilla does
        gameRenderer.enableLightmap();

        try {
            Object texture = getLightmapTextureField().get(gameRenderer);

            if (texture instanceof Texture) {
                return ((Texture) texture).getGlId();
            }

            throw new IllegalStateException("GameRenderer.lightmapTexture is not a Texture: " + texture);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read GameRenderer.lightmapTexture", e);
        }
    }

    /**
     * @return The texture id of the block atlas, as bound on the block texture unit
     */
    public static int getBlockTextureId() {
        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        return textureManager.getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEX).getGlId();
    }

    private static Field getLightmapTextureField() {
        if (lightmapTextureField == null) {
            String[] names = {"lightmapTexture", "field_6453"};
            for (String name : names) {
                try {
                    Field field = GameRenderer.class.getDeclaredField(name);
                    field.setAccessible(true);
                    lightmapTextureField = field;
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (lightmapTextureField == null) {
                throw new IllegalStateException("Could not locate the lightmap texture field on " + GameRenderer.class.getName());
            }
        }
        return lightmapTextureField;
    }
}

