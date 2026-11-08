package me.sodiumlegacy.lwjgl3shim.opengl;

import org.lwjgl.opengl.GL31;

/**
 * LWJGL 3 GL31 -> LWJGL 2 GL31 delegation shim (public surface limited to
 * Sodium's call sites).
 */
public final class GL31C {

    public static final int GL_COPY_READ_BUFFER = GL31.GL_COPY_READ_BUFFER;
    public static final int GL_COPY_WRITE_BUFFER = GL31.GL_COPY_WRITE_BUFFER;

    private GL31C() {
    }

    public static void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        GL31.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }
}


