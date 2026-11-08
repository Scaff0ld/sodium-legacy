package me.sodiumlegacy.mod.api.vertex.format.common;

import me.sodiumlegacy.mod.api.vertex.attributes.common.ColorAttribute;
import me.sodiumlegacy.mod.api.vertex.attributes.common.NormalAttribute;
import me.sodiumlegacy.mod.api.vertex.attributes.common.PositionAttribute;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatDescription;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatRegistry;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

public final class LineVertex  {
    // 1.8.9 port: 1.8.9 has no LINES format; colored debug/outlined geometry is drawn with POSITION_COLOR
    // (the "normal" attribute written by put() is simply dropped during conversion by the vertex writer).
    public static final VertexFormatDescription FORMAT = VertexFormatRegistry.instance()
            .get(DefaultVertexFormats.POSITION_COLOR);

    public static final int STRIDE = 20;

    private static final int OFFSET_POSITION = 0;
    private static final int OFFSET_COLOR = 12;
    private static final int OFFSET_NORMAL = 16;

    public static void put(long ptr,
                           float x, float y, float z, int color, int normal) {
        PositionAttribute.put(ptr + OFFSET_POSITION, x, y, z);
        ColorAttribute.set(ptr + OFFSET_COLOR, color);
        NormalAttribute.set(ptr + OFFSET_NORMAL, normal);
    }
}

