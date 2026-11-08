package me.sodiumlegacy.mod.client.gl.buffer;

import me.sodiumlegacy.mod.client.gl.attribute.GlVertexFormat;
import me.sodiumlegacy.mod.client.util.NativeBuffer;

/**
 * Helper type for tagging the vertex format alongside the raw buffer data.
 */
public final class IndexedVertexData {
    private final GlVertexFormat<?> vertexFormat;
    private final NativeBuffer vertexBuffer;
    private final NativeBuffer indexBuffer;

    public IndexedVertexData(GlVertexFormat<?> vertexFormat,
                             NativeBuffer vertexBuffer,
                             NativeBuffer indexBuffer)
    {
        this.vertexFormat = vertexFormat;
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
    }

    public GlVertexFormat<?> vertexFormat() {
        return this.vertexFormat;
    }

    public NativeBuffer vertexBuffer() {
        return this.vertexBuffer;
    }

    public NativeBuffer indexBuffer() {
        return this.indexBuffer;
    }

    public void delete() {
        this.vertexBuffer.free();
        this.indexBuffer.free();
    }
}
