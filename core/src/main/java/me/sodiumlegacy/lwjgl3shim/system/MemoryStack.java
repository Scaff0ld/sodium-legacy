package me.sodiumlegacy.lwjgl3shim.system;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import me.sodiumlegacy.lwjgl3shim.PointerBuffer;
import me.sodiumlegacy.lwjgl3shim.system.Pointer;

/**
 * LWJGL 3-compatible stack allocator for the Sodium -> MC 1.8.9 port.
 *
 * <p>Backed by a fixed 1 MiB native block per thread. Frames are pushed with
 * {@link #stackPush()} (usable in try-with-resources) and popped again by
 * {@link #close()}. Allocation aligns to 8 bytes by default and cannot span
 * frame/pop boundaries; when the block is exhausted the stack (pointer + frame
 * markers) is reset and an {@link OutOfMemoryError} is thrown.</p>
 */
public class MemoryStack implements AutoCloseable {

    private static final int CAPACITY = 1024 * 1024; // 1 MiB per thread
    private static final int DEFAULT_ALIGNMENT = 8;  // default alignment for malloc()/nmalloc(long)
    private static final ThreadLocal<MemoryStack> THREAD_STACK = new ThreadLocal<MemoryStack>() {
        @Override
        protected MemoryStack initialValue() {
            return new MemoryStack();
        }
    };

    private final long base; // EMPTY base of this thread's stack block
    private final long top;

    private long pointer;
    private long[] frameMarks = new long[16];
    private int frameCount;

    private MemoryStack() {
        this.base = MemoryUtil.nmemAlloc(CAPACITY);
        if (this.base == MemoryUtil.NULL) {
            throw new OutOfMemoryError("Failed to allocate " + CAPACITY + " bytes for the per-thread memory stack");
        }
        this.top = this.base + CAPACITY;
        this.pointer = this.base;
    }

    /** Enters a new frame on this thread's stack and returns the shared instance. */
    public static MemoryStack stackPush() {
        MemoryStack stack = THREAD_STACK.get();
        if (stack.frameCount == stack.frameMarks.length) {
            long[] grown = new long[stack.frameMarks.length << 1];
            System.arraycopy(stack.frameMarks, 0, grown, 0, stack.frameMarks.length);
            stack.frameMarks = grown;
        }
        stack.frameMarks[stack.frameCount++] = stack.pointer;
        return stack;
    }

    /** Pops the most recently pushed frame (restores the stack pointer). */
    @Override
    public void close() {
        if (this.frameCount == 0) {
            throw new IllegalStateException("No pushed MemoryStack frame");
        }
        this.pointer = this.frameMarks[--this.frameCount];
    }

    /** Reserves {@code size} bytes aligned to 8 bytes and returns the raw address. */
    public long nmalloc(long size) {
        return this.nmalloc(DEFAULT_ALIGNMENT, size);
    }

    /** Reserves {@code size} bytes with the given alignment and returns the raw address. */
    public long nmalloc(int alignment, long size) {
        if (this.frameCount == 0) {
            throw new IllegalStateException("MemoryStack allocation outside a pushed frame");
        }
        if (size < 0) {
            throw new IllegalArgumentException("negative size: " + size);
        }

        long address = (this.pointer + (alignment - 1)) & ~(long) (alignment - 1);
        long next = address + size;

        if (next > this.top) {
            long remaining = this.top - this.pointer;
            // Clear the state before throwing so callers can recover deterministically.
            this.pointer = this.base;
            this.frameCount = 0;
            throw new OutOfMemoryError("MemoryStack overflow: requested " + size
                    + " bytes, remaining capacity " + remaining + " bytes (" + CAPACITY + " total)");
        }

        this.pointer = next;
        return address;
    }

    /** Reserves and zero-fills {@code num * size} bytes; returns the raw address. */
    public long ncalloc(int alignment, long num, long size) {
        long bytes = num * size;
        long address = this.nmalloc(alignment, bytes);
        MemoryUtil.memSet(address, 0, bytes);
        return address;
    }

    /** Reserves {@code size} bytes and wraps them in a ByteBuffer. */
    public ByteBuffer malloc(int size) {
        return MemoryUtil.memByteBuffer(this.nmalloc(DEFAULT_ALIGNMENT, size), size);
    }

    /** Reserves {@code size} bytes with the given alignment and wraps them in a ByteBuffer. */
    public ByteBuffer malloc(int alignment, int size) {
        return MemoryUtil.memByteBuffer(this.nmalloc(alignment, size), size);
    }

    /** Reserves uninitialized space for {@code count} pointers. */
    public PointerBuffer mallocPointer(int count) {
        return PointerBuffer.wrap(this.nmalloc(Pointer.POINTER_SIZE, count * Pointer.POINTER_SIZE), count);
    }

    /** Reserves zero-initialized space for {@code count} pointers. */
    public PointerBuffer callocPointer(int count) {
        long address = this.nmalloc(Pointer.POINTER_SIZE, count * Pointer.POINTER_SIZE);
        MemoryUtil.memSet(address, 0, (long) count * Pointer.POINTER_SIZE);
        return PointerBuffer.wrap(address, count);
    }

    /** Reserves zero-initialized space for {@code count} ints. */
    public java.nio.IntBuffer callocInt(int count) {
        int size = count * 4;
        long address = this.nmalloc(4, size);
        MemoryUtil.memSet(address, 0, size);
        return MemoryUtil.memByteBuffer(address, size).asIntBuffer();
    }

    /** Reserves zero-initialized space for {@code count} floats. */
    public java.nio.FloatBuffer callocFloat(int count) {
        int size = count * 4;
        long address = this.nmalloc(4, size);
        MemoryUtil.memSet(address, 0, size);
        return MemoryUtil.memByteBuffer(address, size).asFloatBuffer();
    }

    /** Encodes {@code text} as null-terminated UTF-8 in a stack buffer. */
    public ByteBuffer UTF8(CharSequence text) {
        byte[] encoded = text.toString().getBytes(StandardCharsets.UTF_8);
        int size = encoded.length + 1; // null terminator
        ByteBuffer buffer = this.malloc(size);
        buffer.put(encoded).put((byte) 0).flip();
        return buffer;
    }
}


