package me.sodiumlegacy.lwjgl3shim;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;
import me.sodiumlegacy.lwjgl3shim.system.Pointer;

/**
 * LWJGL 3-style pointer array backed by 8 byte native slots.
 *
 * <p>Only the members used by Sodium's call sites are implemented. Instance[s]
 * created with {@link #wrap(long, int)} share stack/foreign memory and must not
 * be freed; instances created with {@link #PointerBuffer(int)} own a native
 * block allocated with {@code nmemCalloc}.</p>
 */
public class PointerBuffer implements Pointer {

    private final ByteBuffer bb;
    private final int capacity;

    private int pointer; // element index

    /** Wraps foreign (e.g. MemoryStack) memory; the instance does not own it. */
    public static PointerBuffer wrap(long address, int capacity) {
        return new PointerBuffer(MemoryUtil.memByteBuffer(address, capacity * Pointer.POINTER_SIZE), capacity);
    }

    /** Allocates zero-initialized native memory for {@code capacity} pointers. */
    public PointerBuffer(int capacity) {
        long address = MemoryUtil.nmemCalloc(capacity, Pointer.POINTER_SIZE);
        if (address == MemoryUtil.NULL) {
            throw new OutOfMemoryError("Failed to allocate " + capacity + " pointers");
        }
        this.bb = MemoryUtil.memByteBuffer(address, capacity * Pointer.POINTER_SIZE);
        this.capacity = capacity;
    }

    private PointerBuffer(ByteBuffer bb, int capacity) {
        this.bb = bb;
        this.capacity = capacity;
    }

    private long at(int index) {
        if (index < 0 || index >= this.capacity) {
            throw new IndexOutOfBoundsException("index: " + index + ", capacity: " + this.capacity);
        }
        return MemoryUtil.memGetAddress(MemoryUtil.memAddress0(this.bb) + index * Pointer.POINTER_SIZE);
    }

    /** Writes a pointer value at the current index and advances. */
    public PointerBuffer put(long value) {
        if (this.pointer >= this.capacity) {
            throw new BufferOverflowException();
        }
        MemoryUtil.memPutAddress(MemoryUtil.memAddress0(this.bb) + (this.pointer++) * Pointer.POINTER_SIZE, value);
        return this;
    }

    /** Writes the address of {@code value} at the current index and advances. */
    public PointerBuffer put(ByteBuffer value) {
        return put(MemoryUtil.memAddress(value));
    }

    /** Reads the pointer at the current index and advances. */
    public long get() {
        return at(this.pointer++);
    }

    /** Reads the pointer at the given index (absolute). */
    public long get(int index) {
        return at(index);
    }

    public int capacity() {
        return this.capacity;
    }

    /** Raw base address of the backing pointer slots (LWJGL 3 CustomBuffer#address0 equivalent). */
    public long address0() {
        return MemoryUtil.memAddress0(this.bb);
    }

    @Override
    public long address() {
        return address0();
    }
}



