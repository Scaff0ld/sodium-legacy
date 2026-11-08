package me.sodiumlegacy.lwjgl3shim.opengl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLSync;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;
import me.sodiumlegacy.lwjgl3shim.system.Pointer;

/**
 * LWJGL 3 GL32 -> LWJGL 2 delegation shim. LWJGL 2 is monolithic, so members
 * map to the LWJGL 2 class that actually hosts them (buffer mapping on
 * GL15/GL30, sync functions as native entry points taking the raw pointer).
 *
 * <p><em>Porting note:</em> LWJGL 2 2.9.4+legacyfabric.15 has no
 * {@code glMultiDrawElementsBaseVertex} binding, so
 * {@link #nglMultiDrawElementsBaseVertex(int, long, int, long, int, long)}
 * emulates the call as a loop over LWJGL 2's single
 * {@code glDrawElementsBaseVertex} entry points.</p>
 */
public final class GL32C {

    // constants

    public static final int GL_UNSIGNED_BYTE = GL11.GL_UNSIGNED_BYTE;
    public static final int GL_UNSIGNED_SHORT = GL11.GL_UNSIGNED_SHORT;
    public static final int GL_UNSIGNED_INT = GL11.GL_UNSIGNED_INT;
    public static final int GL_TEXTURE0 = GL13.GL_TEXTURE0;

    public static final int GL_SYNC_GPU_COMMANDS_COMPLETE = GL32.GL_SYNC_GPU_COMMANDS_COMPLETE;
    public static final int GL_SYNC_STATUS = GL32.GL_SYNC_STATUS;
    public static final int GL_SIGNALED = GL32.GL_SIGNALED;
    public static final int GL_SYNC_FLUSH_COMMANDS_BIT = GL32.GL_SYNC_FLUSH_COMMANDS_BIT;

    public static final int GL_UNIFORM_BUFFER = GL31.GL_UNIFORM_BUFFER;

    private GL32C() {
    }

    // sync objects

    public static GLSync glFenceSync(int condition, int flags) {
        return GL32.glFenceSync(condition, flags);
    }

    public static void glDeleteSync(GLSync sync) {
        GL32.glDeleteSync(sync);
    }

    public static void glWaitSync(GLSync sync, int flags, long timeout) {
        GL32.glWaitSync(sync, flags, timeout);
    }

    public static int glClientWaitSync(GLSync sync, int flags, long timeout) {
        return GL32.glClientWaitSync(sync, flags, timeout);
    }

    /**
     * Single-value {@code glGetSynciv} convenience (LWJGL 3 shape). Writes the
     * value to element 0 of {@code params} and also returns it; Sodium passes
     * a single-int buffer.
     */
    public static int glGetSynci(GLSync sync, int pname, IntBuffer params) {
        int value = GL32.glGetSynci(sync, pname);
        params.put(0, value);
        return value;
    }

    // buffer mappin

    public static ByteBuffer glMapBufferRange(int target, long offset, long length, int access) {
        ByteBuffer mapped = GL30.glMapBufferRange(target, offset, length, access, null);

        // The raw native entry point returns a BIG_ENDIAN buffer; LWJGL 2's high-level wrapper
        // (and LWJGL 3) apply native order. Without this, typed views (asIntBuffer/asShortBuffer,
        // e.g. SharedQuadIndexBuffer) write byte-swapped data into GPU memory.
        return mapped == null ? null : mapped.order(java.nio.ByteOrder.nativeOrder());
    }

    public static boolean glUnmapBuffer(int target) {
        // glUnmapBuffer lives on GL15 in LWJGL 2's monolithic layout
        return GL15.glUnmapBuffer(target);
    }

    public static void glFlushMappedBufferRange(int target, long offset, long length) {
        GL30.glFlushMappedBufferRange(target, offset, length);
    }

    // uniform blocks

    public static void glBindBufferBase(int target, int index, int buffer) {
        GL30.glBindBufferBase(target, index, buffer);
    }

    public static int glGetUniformBlockIndex(int program, CharSequence uniformBlockName) {
        return GL31.glGetUniformBlockIndex(program, uniformBlockName);
    }

    public static void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        GL31.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }

    // multi draw

    /**
     * LWJGL 2 2.9.4+legacyfabric.15 has no {@code glMultiDrawElementsBaseVertex}
     * binding (GL14 only exposes {@code glMultiDrawArrays};
     * GL32 only single {@code glDrawElementsBaseVertex}). Since an element
     * array buffer is always bound for these draws (state is set by the
     * tessellation binding), the multi-draw is emulated as a per-call loop of
     * LWJGL 2's GL32
     * {@code glDrawElementsBaseVertex(mode, count, type, indicesOffset, baseVertex)},
     * which preserves base-vertex semantics. Parameters are the raw LWJGL 3
     * pointers into the caller's {@code MultiDrawBatch} native arrays.
     */
    public static void nglMultiDrawElementsBaseVertex(int mode, long pElementCount, int type,
                                                     long pElementPointer, int drawCount, long pBaseVertex) {
        for (int i = 0; i < drawCount; i++) {
            int count = MemoryUtil.memGetInt(pElementCount + (4L * i));
            if (count <= 0) {
                continue;
            }

            long indicesOffset = MemoryUtil.memGetAddress(pElementPointer
                    + ((long) Pointer.POINTER_SIZE * i));
            int baseVertex = MemoryUtil.memGetInt(pBaseVertex + (4L * i));

            GL32.glDrawElementsBaseVertex(mode, count, type, indicesOffset, baseVertex);
        }
    }
}




