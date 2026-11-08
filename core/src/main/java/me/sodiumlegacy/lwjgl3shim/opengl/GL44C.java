package me.sodiumlegacy.lwjgl3shim.opengl;

import org.lwjgl.opengl.GL44;

/**
 * LWJGL 3 GL44 -> LWJGL 2 delegation shim (public surface limited to Sodium's
 * call sites).
 */
public final class GL44C {

    public static final int GL_MAP_PERSISTENT_BIT = GL44.GL_MAP_PERSISTENT_BIT;
    public static final int GL_MAP_COHERENT_BIT = GL44.GL_MAP_COHERENT_BIT;
    public static final int GL_CLIENT_STORAGE_BIT = GL44.GL_CLIENT_STORAGE_BIT;

    private GL44C() {
    }

    public static void glBufferStorage(int target, long size, int flags) {
        GL44.glBufferStorage(target, size, flags);
    }
}


