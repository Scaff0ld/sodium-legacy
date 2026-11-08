package me.sodiumlegacy.mod.client.gl.shader;

import me.sodiumlegacy.lwjgl3shim.opengl.GL20C;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
    VERTEX(GL20C.GL_VERTEX_SHADER),
    FRAGMENT(GL20C.GL_FRAGMENT_SHADER);

    public final int id;

    ShaderType(int id) {
        this.id = id;
    }
}

