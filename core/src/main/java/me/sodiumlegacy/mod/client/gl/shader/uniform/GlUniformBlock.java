package me.sodiumlegacy.mod.client.gl.shader.uniform;

import me.sodiumlegacy.mod.client.gl.buffer.GlBuffer;
import me.sodiumlegacy.lwjgl3shim.opengl.GL32C;

public class GlUniformBlock {
    private final int binding;

    public GlUniformBlock(int uniformBlockBinding) {
        this.binding = uniformBlockBinding;
    }

    public void bindBuffer(GlBuffer buffer) {
        GL32C.glBindBufferBase(GL32C.GL_UNIFORM_BUFFER, this.binding, buffer.handle());
    }
}

