package me.sodiumlegacy.mod.client.gl.array;

import me.sodiumlegacy.mod.client.gl.GlObject;
import me.sodiumlegacy.lwjgl3shim.opengl.GL30C;

/**
 * Provides Vertex Array functionality on supported platforms.
 */
public class GlVertexArray extends GlObject {
    public static final int NULL_ARRAY_ID = 0;

    public GlVertexArray() {
        this.setHandle(GL30C.glGenVertexArrays());
    }
}

