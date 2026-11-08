package me.sodiumlegacy.mod.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureMap;

import java.lang.reflect.Field;

public class TextureUtil {

    /**
     * We deliberately do NOT go through the {@code TextureManager} by resource location: vanilla registers the
     * lightmap through {@code registerDynamicTexture("lightMap", ...)} with an auto-incrementing index
     * ({@code dynamic/lightMap_1}, {@code dynamic/lightMap_2}, ...). Hardcoding {@code lightMap_1} breaks as soon
     * as another mod registers a dynamic texture before vanilla does, or the renderer is not initialized yet when 
	 * this class loads — either way {@code getTexture(id)} returns {@code null} and crashes with an NPE outside 
	 * of the dev environment.
     *
     * Instead, the lightmap {@link DynamicTexture}'s GL id is read directly from {@code EntityRenderer}'s
     * field. Fields are SRG-named at runtime in production Forge ({@code field_78513_d}) and MCP-named in dev
     * ({@code lightmapTexture}), so the field is looked up by type first, with name fallbacks.
     */
    private static Field lightmapTextureField;

    /**
     * @return The texture id of the lightmap texture, as bound on the lightmap texture unit
     */
    public static int getLightTextureId() {
        Minecraft client = Minecraft.getMinecraft();
        EntityRenderer entityRenderer = client.entityRenderer;

        // Enable the lightmap before returning its id, so that it is always uploaded and bound like vanilla does
        entityRenderer.enableLightmap();

        try {
            return ((DynamicTexture) getLightmapTextureField().get(entityRenderer)).getGlTextureId();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read EntityRenderer.lightmapTexture", e);
        }
    }

    /**
     * @return The texture id of the block atlas, as bound on the block texture unit
     */
    public static int getBlockTextureId() {
        return Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture).getGlTextureId();
    }

    private static Field getLightmapTextureField() {
        if (lightmapTextureField == null) {
            lightmapTextureField = findLightmapTextureField();
        }
        return lightmapTextureField;
    }

    private static Field findLightmapTextureField() {
        Field byType = null;
        for (Field field : EntityRenderer.class.getDeclaredFields()) {
            if (field.getType() == DynamicTexture.class) {
                field.setAccessible(true);
                if (byType == null) {
                    byType = field;
                }
            }
        }
        if (byType != null) {
            return byType;
        }

        // Fallback to the known names: MCP (dev) then SRG (production)
        String[] names = {"lightmapTexture", "field_78513_d"};
        for (String name : names) {
            try {
                Field field = EntityRenderer.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }

        throw new IllegalStateException("Could not locate the lightmap texture field on " + EntityRenderer.class.getName());
    }
}
