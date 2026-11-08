package me.sodiumlegacy.mod.client.gl.tessellation;

import me.sodiumlegacy.mod.client.gl.attribute.GlVertexAttributeBinding;
import me.sodiumlegacy.mod.client.gl.device.CommandList;
import me.sodiumlegacy.lwjgl3shim.opengl.GL20C;
import me.sodiumlegacy.lwjgl3shim.opengl.GL30C;

public abstract class GlAbstractTessellation implements GlTessellation {
    protected final GlPrimitiveType primitiveType;
    protected final TessellationBinding[] bindings;

    protected GlAbstractTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings) {
        this.primitiveType = primitiveType;
        this.bindings = bindings;
    }

    @Override
    public GlPrimitiveType getPrimitiveType() {
        return this.primitiveType;
    }

    protected void bindAttributes(CommandList commandList) {
        for (TessellationBinding binding : this.bindings) {
            commandList.bindBuffer(binding.target(), binding.buffer());

            for (GlVertexAttributeBinding attrib : binding.attributeBindings()) {
                if (attrib.isIntType()) {
                    GL30C.glVertexAttribIPointer(attrib.getIndex(), attrib.getCount(), attrib.getFormat(),
                            attrib.getStride(), attrib.getPointer());
                } else {
                    GL20C.glVertexAttribPointer(attrib.getIndex(), attrib.getCount(), attrib.getFormat(), attrib.isNormalized(),
                            attrib.getStride(), attrib.getPointer());
                }
                GL20C.glEnableVertexAttribArray(attrib.getIndex());
            }
        }
    }
}

