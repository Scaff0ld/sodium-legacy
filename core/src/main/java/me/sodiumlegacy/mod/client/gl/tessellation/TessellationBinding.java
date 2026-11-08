package me.sodiumlegacy.mod.client.gl.tessellation;

import me.sodiumlegacy.mod.client.gl.attribute.GlVertexAttributeBinding;
import me.sodiumlegacy.mod.client.gl.buffer.GlBuffer;
import me.sodiumlegacy.mod.client.gl.buffer.GlBufferTarget;

public final class TessellationBinding {
    private final GlBufferTarget target;
    private final GlBuffer buffer;
    private final GlVertexAttributeBinding[] attributeBindings;

    public TessellationBinding(GlBufferTarget target,
                               GlBuffer buffer,
                               GlVertexAttributeBinding[] attributeBindings)
    {
        this.target = target;
        this.buffer = buffer;
        this.attributeBindings = attributeBindings;
    }

    public GlBufferTarget target() {
        return this.target;
    }

    public GlBuffer buffer() {
        return this.buffer;
    }

    public GlVertexAttributeBinding[] attributeBindings() {
        return this.attributeBindings;
    }

    public static TessellationBinding forVertexBuffer(GlBuffer buffer, GlVertexAttributeBinding[] attributes) {
        return new TessellationBinding(GlBufferTarget.ARRAY_BUFFER, buffer, attributes);
    }

    public static TessellationBinding forElementBuffer(GlBuffer buffer) {
        return new TessellationBinding(GlBufferTarget.ELEMENT_BUFFER, buffer, new GlVertexAttributeBinding[0]);
    }
}
