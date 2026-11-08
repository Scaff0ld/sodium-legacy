package me.sodiumlegacy.mod.client.gl.device;

import me.sodiumlegacy.lwjgl3shim.PointerBuffer;
import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;
import me.sodiumlegacy.lwjgl3shim.system.Pointer;

import java.nio.IntBuffer;

/**
 * Provides a fixed-size queue for building a draw-command list usable with
 * {@code glMultiDrawElementsBaseVertex}. (On MC 1.8.9 / LWJGL 2 that call is
 * emulated by the shim as a loop of {@code glDrawElementsBaseVertex}; see
 * {@code me.sodiumlegacy.lwjgl3shim.opengl.GL32C}.)
 */
public final class MultiDrawBatch {
    public final long pElementPointer;
    public final long pElementCount;
    public final long pBaseVertex;

    private final int capacity;

    public int size;

    public MultiDrawBatch(int capacity) {
        this.pElementPointer = MemoryUtil.nmemAlignedAlloc(32, (long) capacity * Pointer.POINTER_SIZE);
        MemoryUtil.memSet(this.pElementPointer, 0x0, (long) capacity * Pointer.POINTER_SIZE);

        this.pElementCount = MemoryUtil.nmemAlignedAlloc(32, (long) capacity * Integer.BYTES);
        this.pBaseVertex = MemoryUtil.nmemAlignedAlloc(32, (long) capacity * Integer.BYTES);

        this.capacity = capacity;
    }

    public int size() {
        return this.size;
    }

    public int capacity() {
        return this.capacity;
    }

    public void clear() {
        this.size = 0;
    }

    public void delete() {
        MemoryUtil.nmemAlignedFree(this.pElementPointer);
        MemoryUtil.nmemAlignedFree(this.pElementCount);
        MemoryUtil.nmemAlignedFree(this.pBaseVertex);
    }

    public boolean isEmpty() {
        return this.size <= 0;
    }

    public int getIndexBufferSize() {
        int elements = 0;

        for (int index = 0; index < this.size; index++) {
            elements = Math.max(elements, MemoryUtil.memGetInt(this.pElementCount + ((long) index * Integer.BYTES)));
        }

        return elements;
    }
}

