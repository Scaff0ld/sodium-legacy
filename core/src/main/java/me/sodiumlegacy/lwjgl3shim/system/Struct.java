package me.sodiumlegacy.lwjgl3shim.system;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Minimal LWJGL 3-style struct base for the Sodium -> MC 1.8.9 port.
 *
 * <p>Only the form used by Sodium's custom layouts is supported: a flat member
 * list declared through {@link #__member(int)} calls nested inside a single
 * {@link #__struct(Layout...)} during static initialization.</p>
 */
public abstract class Struct {

    private static final ThreadLocal<Deque<Layout>> PENDING = new ThreadLocal<Deque<Layout>>() {
        @Override
        protected Deque<Layout> initialValue() {
            return new ArrayDeque<Layout>(16);
        }
    };

    protected final long address;
    protected final ByteBuffer container;

    protected Struct(long address, ByteBuffer container) {
        this.address = address;
        this.container = container;
    }

    public abstract int sizeof();

    /** Declares a member of the given size; the alignment defaults to the size. */
    public static Layout __member(int size) {
        return __member(size, size);
    }

    /**
     * Declares a member of the given size and alignment. The returned value is
     * a placeholder; members are assembled by {@link #__struct(Layout...)}.
     */
    public static Layout __member(int size, int alignment) {
        PENDING.get().push(Layout.leaf(size, alignment));
        return null;
    }

    /**
     * Composes a struct layout from the last {@code members.length} members
     * declared via {@link #__member(int)}. Offsets use natural alignment
     * (like LWJGL 3's struct builder), and the total size is padded up to the
     * struct alignment.
     */
    public static Layout __struct(Layout... members) {
        int count = members.length; // only the count matters for accounting
        Deque<Layout> pending = PENDING.get();
        if (pending.size() != count) {
            pending.clear();
            throw new IllegalStateException("__member/__struct count mismatch: expected " + count
                    + " pending members, got " + pending.size() + " (only flat layouts are supported)");
        }
        Layout[] leaves = new Layout[count];
        for (int i = count - 1; i >= 0; i--) {
            leaves[i] = pending.pop();
        }
        return Layout.struct(leaves);
    }

    /** Immutable computed struct layout. */
    public static final class Layout {
        private final int size;
        private final int alignment;
        private final int[] offsets;

        private Layout(int size, int alignment, int[] offsets) {
            this.size = size;
            this.alignment = alignment;
            this.offsets = offsets;
        }

        private static Layout leaf(int size, int alignment) {
            return new Layout(size, alignment, new int[] {0});
        }

        private static Layout struct(Layout[] leaves) {
            int count = leaves.length;
            int[] offsets = new int[count];
            int cursor = 0;
            int alignment = 1;

            for (int i = 0; i < count; i++) {
                alignment = Math.max(alignment, leaves[i].alignment);
                cursor = alignUp(cursor, leaves[i].alignment);
                offsets[i] = cursor;
                cursor += leaves[i].size;
            }
            return new Layout(alignUp(cursor, alignment), alignment, offsets);
        }

        private static int alignUp(int value, int alignment) {
            return (value + alignment - 1) / alignment * alignment;
        }

        /** The total (aligned) size of the struct in bytes. */
        public int getSize() {
            return this.size;
        }

        /** The struct's alignment in bytes. */
        public int getAlignment() {
            return this.alignment;
        }

        /** The byte offset of member {@code i}. */
        public int offsetof(int i) {
            return this.offsets[i];
        }
    }
}


