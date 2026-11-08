package me.sodiumlegacy.mod.api.vertex.format.common;

import me.sodiumlegacy.mod.api.vertex.attributes.common.*;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatDescription;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatRegistry;
import net.minecraft.client.render.VertexFormats;

public final class ModelVertex {
    // 1.8.9 port: 1.8.9 has no overlay/light elements; entities render with POSITION_TEXTURE_COLOR_NORMAL
    // ("overlay" and "light" attributes written by write() are simply dropped during conversion).
    public static final VertexFormatDescription FORMAT = VertexFormatRegistry.instance()
            .get(VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);

    public static final int STRIDE = 36;

    private static final int OFFSET_POSITION = 0;
    private static final int OFFSET_COLOR = 12;
    private static final int OFFSET_TEXTURE = 16;
    private static final int OFFSET_OVERLAY = 24;
    private static final int OFFSET_LIGHT = 28;
    private static final int OFFSET_NORMAL = 32;

    public static void write(long ptr,
                             float x, float y, float z, int color, float u, float v, int overlay, int light, int normal) {
        PositionAttribute.put(ptr + OFFSET_POSITION, x, y, z);
        ColorAttribute.set(ptr + OFFSET_COLOR, color);
        TextureAttribute.put(ptr + OFFSET_TEXTURE, u, v);
        OverlayAttribute.set(ptr + OFFSET_OVERLAY, overlay);
        LightAttribute.set(ptr + OFFSET_LIGHT, light);
        NormalAttribute.set(ptr + OFFSET_NORMAL, normal);
    }
}
