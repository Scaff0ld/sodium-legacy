package me.sodiumlegacy.mod.api.vertex.format.common;

import me.sodiumlegacy.mod.api.vertex.attributes.common.ColorAttribute;
import me.sodiumlegacy.mod.api.vertex.attributes.common.LightAttribute;
import me.sodiumlegacy.mod.api.vertex.attributes.common.PositionAttribute;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatDescription;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatRegistry;
import me.sodiumlegacy.mod.api.vertex.attributes.common.TextureAttribute;
import net.minecraft.client.render.VertexFormats;

public final class ParticleVertex {
    // 1.8.9 port: 1.8.9 has no light element; particles use POSITION_TEXTURE2_COLOR where the second
    // (short) uv coordinate is the lightmap substitution — "light" written by put() is dropped during conversion.
    public static final VertexFormatDescription FORMAT = VertexFormatRegistry.instance()
            .get(VertexFormats.POSITION_TEXTURE2_COLOR);

    public static final int STRIDE = 28;

    private static final int OFFSET_POSITION = 0;
    private static final int OFFSET_TEXTURE = 12;
    private static final int OFFSET_COLOR = 20;
    private static final int OFFSET_LIGHT = 24;

    public static void put(long ptr,
                           float x, float y, float z, float u, float v, int color, int light) {
        PositionAttribute.put(ptr + OFFSET_POSITION, x, y, z);
        TextureAttribute.put(ptr + OFFSET_TEXTURE, u, v);
        ColorAttribute.set(ptr + OFFSET_COLOR, color);
        LightAttribute.set(ptr + OFFSET_LIGHT, light);
    }
}
