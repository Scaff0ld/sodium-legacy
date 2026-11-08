package me.sodiumlegacy.lwjgl3shim.opengl;

import org.lwjgl.opengl.GL44;

/**
 * LWJGL 3 ARBBufferStorage -> LWJGL 2 delegation shim.
 *
 * <p>This shim shadows LWJGL 2's own {@code me.sodiumlegacy.lwjgl3shim.opengl.ARBBufferStorage},
 * so the function delegates to LWJGL 2's GL44 entry point of the same GL
 * function (gLWJGL 2 loads the {@code glBufferStorage} function pointer when
 * either OpenGL 4.4 core or ARB_buffer_storage is available, so the ARB path
 * resolves to the same native function).</p>
 */
public final class ARBBufferStorage {

    private ARBBufferStorage() {
    }

    public static void glBufferStorage(int target, long size, int flags) {
        GL44.glBufferStorage(target, size, flags);
    }
}



