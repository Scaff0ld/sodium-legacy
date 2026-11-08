package me.sodiumlegacy.mod.api.vertex.attributes;

/**
 * The generic vertex attribute types shared by every vertex format. This enum is independent of Minecraft and of
 * the loader, so it lives in {@code :core}. The link to Minecraft's {@code VertexFormatElement} lives on the
 * platform side, in {@code CommonVertexAttributeMapping}, which also verifies at startup that the byte lengths
 * declared here match the real Minecraft elements.
 *
 * <p>1.8.9 port: 1.8.9 has no "overlay" or "light" vertex elements, so {@link #OVERLAY} and {@link #LIGHT} have no
 * Minecraft element and are treated as plain 4-byte integer attributes.
 */
public enum CommonVertexAttribute {
    POSITION(12),  // 3 x float
    COLOR(4),      // 4 x unsigned byte
    TEXTURE(8),    // 2 x float
    OVERLAY(4),    // no element in 1.8.9: packed int
    LIGHT(4),      // no element in 1.8.9: packed int
    NORMAL(3);     // 3 x byte

    public static final int COUNT = CommonVertexAttribute.values().length;

    private final int byteLength;

    CommonVertexAttribute(int byteLength) {
        this.byteLength = byteLength;
    }

    public int getByteLength() {
        return this.byteLength;
    }
}
