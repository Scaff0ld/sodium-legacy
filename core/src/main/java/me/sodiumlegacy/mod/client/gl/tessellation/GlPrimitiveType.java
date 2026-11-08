package me.sodiumlegacy.mod.client.gl.tessellation;

import me.sodiumlegacy.lwjgl3shim.opengl.GL20C;

public enum GlPrimitiveType {
    TRIANGLES(GL20C.GL_TRIANGLES);

    private final int id;

    GlPrimitiveType(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }
}

