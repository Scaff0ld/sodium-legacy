package me.sodiumlegacy.mod.api.memory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Port note: the upstream implementation used {@code sun.misc.Unsafe} directly, which is not
 * visible under {@code --release 8}. This shim obtains the Unsafe instance via reflection and
 * calls methods reflectively, keeping identical semantics on Java 8.
 */
public class MemoryIntrinsics {
    private static final Object UNSAFE;
    private static final Method COPY_MEMORY;

    static {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);

            UNSAFE = field.get(null);
            COPY_MEMORY = unsafeClass.getMethod("copyMemory", long.class, long.class, long.class);
            COPY_MEMORY.setAccessible(true);
        } catch (NoSuchFieldException | NoSuchMethodException | ClassNotFoundException | IllegalAccessException e) {
            throw new RuntimeException("Couldn't obtain reference to sun.misc.Unsafe", e);
        }
    }

    /**
     * Copies the number of bytes specified by {@param length} between off-heap buffers {@param src} and {@param dst}.
     * <p>
     * WARNING: This function makes no attempt to verify that the parameters are correct. If you pass invalid pointers
     * or read/write memory outside a buffer, the JVM will likely crash!
     *
     * @param src The source pointer to begin copying from
     * @param dst The destination pointer to begin copying into
     * @param length The number of bytes to copy
     */
    public static void copyMemory(long src, long dst, int length) {
        try {
            COPY_MEMORY.invoke(UNSAFE, src, dst, (long) length);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("copyMemory failed", e);
        }
    }
}
