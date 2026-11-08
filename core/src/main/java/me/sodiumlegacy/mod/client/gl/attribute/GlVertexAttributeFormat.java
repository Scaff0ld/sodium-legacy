package me.sodiumlegacy.mod.client.gl.attribute;

import me.sodiumlegacy.lwjgl3shim.opengl.GL20C;

/**
 * An enumeration over the supported data types that can be used for vertex attributes.
 */
public final class GlVertexAttributeFormat {
    public static final GlVertexAttributeFormat FLOAT = new GlVertexAttributeFormat(GL20C.GL_FLOAT, 4);
    public static final GlVertexAttributeFormat UNSIGNED_SHORT = new GlVertexAttributeFormat(GL20C.GL_UNSIGNED_SHORT, 2);
    public static final GlVertexAttributeFormat UNSIGNED_BYTE = new GlVertexAttributeFormat(GL20C.GL_UNSIGNED_BYTE, 1);
    public static final GlVertexAttributeFormat UNSIGNED_INT = new GlVertexAttributeFormat(GL20C.GL_UNSIGNED_INT, 4);

    private final int typeId;
    private final int size;

    public GlVertexAttributeFormat(int typeId, int size) {
        this.typeId = typeId;
        this.size = size;
    }

    public int typeId() {
        return this.typeId;
    }

    public int size() {
        return this.size;
    }
}

