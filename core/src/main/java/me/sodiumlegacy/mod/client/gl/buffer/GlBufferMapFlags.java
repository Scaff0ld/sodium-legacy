package me.sodiumlegacy.mod.client.gl.buffer;

import me.sodiumlegacy.mod.client.gl.util.EnumBit;
import me.sodiumlegacy.lwjgl3shim.opengl.GL30C;
import me.sodiumlegacy.lwjgl3shim.opengl.GL33C;
import me.sodiumlegacy.lwjgl3shim.opengl.GL44C;

public enum GlBufferMapFlags implements EnumBit {
    READ(GL30C.GL_MAP_READ_BIT),
    WRITE(GL30C.GL_MAP_WRITE_BIT),
    PERSISTENT(GL44C.GL_MAP_PERSISTENT_BIT),
    INVALIDATE_BUFFER(GL30C.GL_MAP_INVALIDATE_BUFFER_BIT),
    INVALIDATE_RANGE(GL30C.GL_MAP_INVALIDATE_RANGE_BIT),
    EXPLICIT_FLUSH(GL30C.GL_MAP_FLUSH_EXPLICIT_BIT),
    COHERENT(GL44C.GL_MAP_COHERENT_BIT),
    UNSYNCHRONIZED(GL33C.GL_MAP_UNSYNCHRONIZED_BIT);

    private final int bit;

    GlBufferMapFlags(int bit) {
        this.bit = bit;
    }

    @Override
    public int getBits() {
        return this.bit;
    }
}

