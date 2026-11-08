package me.sodiumlegacy.mod.api.vertex.format.common;

import me.sodiumlegacy.mod.api.vertex.attributes.common.ColorAttribute;
import me.sodiumlegacy.mod.api.vertex.attributes.common.LightAttribute;
import me.sodiumlegacy.mod.api.vertex.attributes.common.PositionAttribute;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatDescription;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatRegistry;
import me.sodiumlegacy.mod.api.vertex.attributes.common.TextureAttribute;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

public final class GlyphVertex  {
    // 1.8.9 port: 1.8.9 has no uv1-light format; font glyphs are drawn with POSITION_TEXTURE_COLOR
    // (the "light" attribute written by put() is simply dropped during conversion by the vertex writer).
    public static final VertexFormatDescription FORMAT = VertexFormatRegistry.instance()
            .get(DefaultVertexFormats.POSITION_TEX_COLOR);

    public static final int STRIDE = 28;

    private static final int OFFSET_POSITION = 0;
    private static final int OFFSET_COLOR = 12;
    private static final int OFFSET_TEXTURE = 16;
    private static final int OFFSET_LIGHT = 24;

    public static void put(long ptr, float x, float y, float z, int color, float u, float v, int light) {
        PositionAttribute.put(ptr + OFFSET_POSITION, x, y, z);
        ColorAttribute.set(ptr + OFFSET_COLOR, color);
        TextureAttribute.put(ptr + OFFSET_TEXTURE, u, v);
        LightAttribute.set(ptr + OFFSET_LIGHT, light);
    }
}

