package me.sodiumlegacy.mod.client.gl.buffer;

import me.sodiumlegacy.mod.client.gl.GlObject;
import me.sodiumlegacy.lwjgl3shim.opengl.GL20C;

public abstract class GlBuffer extends GlObject {
    private GlBufferMapping activeMapping;

    protected GlBuffer() {
        this.setHandle(GL20C.glGenBuffers());
    }

    public GlBufferMapping getActiveMapping() {
        return this.activeMapping;
    }

    public void setActiveMapping(GlBufferMapping mapping) {
        this.activeMapping = mapping;
    }
}

