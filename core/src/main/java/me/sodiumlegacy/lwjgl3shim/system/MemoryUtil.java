package me.sodiumlegacy.lwjgl3shim.system;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;

import me.sodiumlegacy.lwjgl3shim.PointerBuffer;

/**
 * LWJGL 3 -> JDK 8 / LWJGL 2 compatibility shim for the Sodium 0.5.x -> MC 1.8.9 port.
 *
 * <p>Memory operations are backed by {@code sun.misc.Unsafe}, obtained via pure
 * reflection since the class is not visible to javac under {@code --release 8}.
 * All reflective Method/Constructor/Field handles are created once in static
 * initialization; per-call reflection overhead is acceptable for this phase.</p>
 *
 * <p>Native memory is wrapped into {@link java.nio.ByteBuffer} instances through
 * a reflective call to the package-private constructor
 * {@code java.nio.DirectByteBuffer(long address, int capacity)}; every such
 * buffer is given LITTLE_ENDIAN byte order (matching LWJGL 3 allocations on the
 * platforms we target).</p>
 */
public final class MemoryUtil {

    /** The null pointer value. */
    public static final long NULL = 0x0L;

    private static final Throwable INIT_ERROR;

    private static final Object UNSAFE;
    private static final Method ALLOCATE_MEMORY;   // long allocateMemory(long)
    private static final Method FREE_MEMORY;       // void freeMemory(long)
    private static final Method SET_MEMORY;        // void setMemory(long, long, byte)
    private static final Method COPY_MEMORY;       // void copyMemory(long, long, long)
    private static final Method PUT_BYTE;          // void putByte(long, byte)
    private static final Method PUT_SHORT;         // void putShort(long, short)
    private static final Method PUT_INT;           // void putInt(long, int)
    private static final Method PUT_FLOAT;         // void putFloat(long, float)
    private static final Method PUT_LONG;          // void putLong(long, long)
    private static final Method GET_BYTE;          // byte getByte(long)
    private static final Method GET_SHORT;         // short getShort(long)
    private static final Method GET_INT;           // int getInt(long)
    private static final Method GET_FLOAT;         // float getFloat(long)
    private static final Method GET_LONG;          // long getLong(long)

    private static final Class<?> DIRECT_BB_CLASS;
    private static final Constructor<?> DIRECT_BB_CTOR; // java.nio.DirectByteBuffer(long, int)
    private static final Field DIRECT_BB_ADDRESS;       // java.nio.DirectByteBuffer#address

    static {
        Throwable error = null;
        Object unsafe = null;
        Method allocateMemory = null, freeMemory = null, setMemory = null, copyMemory = null;
        Method putByte = null, putShort = null, putInt = null, putFloat = null, putLong = null;
        Method getShort = null, getInt = null, getFloat = null, getLong = null;
        Method getByte = null;
        Class<?> dbbClass = null;
        Constructor<?> dbbCtor = null;
        Field dbbAddress = null;

        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = theUnsafe.get(null);

            allocateMemory = unsafeClass.getMethod("allocateMemory", long.class);
            freeMemory = unsafeClass.getMethod("freeMemory", long.class);
            setMemory = unsafeClass.getMethod("setMemory", long.class, long.class, byte.class);
            copyMemory = unsafeClass.getMethod("copyMemory", long.class, long.class, long.class);
            putByte = unsafeClass.getMethod("putByte", long.class, byte.class);
            putShort = unsafeClass.getMethod("putShort", long.class, short.class);
            putInt = unsafeClass.getMethod("putInt", long.class, int.class);
            putFloat = unsafeClass.getMethod("putFloat", long.class, float.class);
            putLong = unsafeClass.getMethod("putLong", long.class, long.class);
            getShort = unsafeClass.getMethod("getShort", long.class);
            getInt = unsafeClass.getMethod("getInt", long.class);
            getFloat = unsafeClass.getMethod("getFloat", long.class);
            getLong = unsafeClass.getMethod("getLong", long.class);
            getByte = unsafeClass.getMethod("getByte", long.class);

            dbbClass = Class.forName("java.nio.DirectByteBuffer");
            dbbCtor = dbbClass.getDeclaredConstructor(long.class, int.class);
            dbbCtor.setAccessible(true);
            // JDK 8: java.nio.Buffer itself declares the package-private "address" field;
            // DirectByteBuffer only inherits it, so getDeclaredField on DirectByteBuffer 
			// throws NoSuchFieldException. Walk the hierarchy instead.
            dbbAddress = getInheritedField(dbbClass, "address");
            dbbAddress.setAccessible(true);
        } catch (Throwable t) {
            error = t;
        }

        INIT_ERROR = error;
        UNSAFE = unsafe;
        ALLOCATE_MEMORY = allocateMemory;
        FREE_MEMORY = freeMemory;
        SET_MEMORY = setMemory;
        COPY_MEMORY = copyMemory;
        PUT_BYTE = putByte;
        PUT_SHORT = putShort;
        PUT_INT = putInt;
        PUT_FLOAT = putFloat;
        PUT_LONG = putLong;
        GET_BYTE = getByte;
        GET_SHORT = getShort;
        GET_INT = getInt;
        GET_FLOAT = getFloat;
        GET_LONG = getLong;
        DIRECT_BB_CLASS = dbbClass;
        DIRECT_BB_CTOR = dbbCtor;
        DIRECT_BB_ADDRESS = dbbAddress;
    }

    private MemoryUtil() {
    }

    /** Fails fast if the reflective Unsafe bridge could not be set up. */
    private static void checkInit() {
        if (INIT_ERROR != null) {
            throw new UnsupportedOperationException("MemoryUtil shim failed to initialize: " + INIT_ERROR, INIT_ERROR);
        }
    }

    /**
     * Finds a declared field on the given class or, failing that, anywhere in its
     * superclass chain. Needed because on JDK 8 the {@code address} field of direct
     * buffers is declared on {@link java.nio.Buffer}, not on {@code DirectByteBuffer}.
     */
    private static Field getInheritedField(Class<?> start, String name) throws NoSuchFieldException {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name + " (not found in hierarchy of " + start.getName() + ")");
    }

    private static RuntimeException rethrow(Exception e) {
        if (e instanceof InvocationTargetException) {
            Throwable cause = ((InvocationTargetException) e).getCause();
            if (cause instanceof RuntimeException) {
                return (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
        }
        return new RuntimeException("MemoryUtil reflection failure", e);
    }

    private static long invokeAlloc(long size) {
        try {
            return (Long) ALLOCATE_MEMORY.invoke(UNSAFE, size);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    private static void invokeFree(long ptr) {
        try {
            FREE_MEMORY.invoke(UNSAFE, ptr);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    private static void invokeSetMemory(long ptr, long bytes, byte value) {
        try {
            SET_MEMORY.invoke(UNSAFE, ptr, bytes, value);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    private static void invokeCopyMemory(long src, long dst, long bytes) {
        try {
            COPY_MEMORY.invoke(UNSAFE, src, dst, bytes);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    private static void putLong(long ptr, long value) {
        try {
            PUT_LONG.invoke(UNSAFE, ptr, value);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    private static long getLong(long ptr) {
        try {
            return (Long) GET_LONG.invoke(UNSAFE, ptr);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    private static short getShort(long ptr) {
        try {
            return (Short) GET_SHORT.invoke(UNSAFE, ptr);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    private static void putShort(long ptr, short value) {
        try {
            PUT_SHORT.invoke(UNSAFE, ptr, value);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    // size in bytes

    public static int memLengthUTF16(CharSequence value, boolean nullTerminated) {
        // 2 bytes per char, plus an optional 2-byte terminator
        return (value.length() + (nullTerminated ? 1 : 0)) << 1;
    }

    // allocation

    public static long nmemAlloc(long size) {
        checkInit();
        try {
            return (Long) ALLOCATE_MEMORY.invoke(UNSAFE, size);
        } catch (Exception e) {
            // LWJGL 3 contract: allocation failure is reported as a 0 return value
            Throwable cause = e instanceof InvocationTargetException ? ((InvocationTargetException) e).getCause() : null;
            if (cause instanceof OutOfMemoryError) {
                return NULL;
            }
            throw rethrow(e);
        }
    }

    public static long nmemCalloc(long num, long size) {
        checkInit();
        long total = num * size;
        long ptr = nmemAlloc(total);
        if (ptr != NULL) {
            invokeSetMemory(ptr, total, (byte) 0);
        }
        return ptr;
    }

    public static long nmemAlignedAlloc(long alignment, long size) {
        checkInit();
        // Allocate size + alignment + 8 bytes; the raw allocation pointer is
        // stored in an 8 byte header just before the aligned address so that
        // nmemAlignedFree can recover and release the original block.
        long raw = nmemAlloc(size + alignment + 8L);
        if (raw == NULL) {
            return NULL;
        }
        long aligned = (raw + 8L + (alignment - 1L)) & ~(alignment - 1L);
        putLong(aligned - 8L, raw);
        return aligned;
    }

    /** Wraps a natively aligned allocation into a ByteBuffer. */
    public static ByteBuffer memAlignedAlloc(long alignment, long size) {
        long ptr = nmemAlignedAlloc(alignment, size);
        // LWJGL 3 returns null when the allocation fails
        return ptr == NULL ? null : memByteBuffer(ptr, (int) size);
    }

    public static void nmemAlignedFree(long ptr) {
        checkInit();
        long raw = getLong(ptr - 8L);
        invokeFree(raw);
    }

    public static void memAlignedFree(ByteBuffer buffer) {
        if (buffer != null) {
            nmemAlignedFree(memAddress0(buffer));
        }
    }

    public static void nmemFree(long ptr) {
        checkInit();
        if (ptr != NULL) {
            invokeFree(ptr);
        }
    }

    public static void memFree(long ptr) {
        nmemFree(ptr);
    }

    public static void memFree(ByteBuffer buffer) {
        if (buffer != null) {
            nmemFree(memAddress0(buffer));
        }
    }

    // raw memory access

    public static void memSet(long dest, int value, long bytes) {
        checkInit();
        invokeSetMemory(dest, bytes, (byte) value);
    }

    public static void memCopy(long src, long dst, long bytes) {
        checkInit();
        if (bytes != 0) {
            invokeCopyMemory(src, dst, bytes);
        }
    }

    public static void memCopy(ByteBuffer src, ByteBuffer dst) {
        memCopy(memAddress0(src), memAddress0(dst), src.remaining());
    }

    public static void memPutByte(long ptr, byte value) {
        checkInit();
        try {
            PUT_BYTE.invoke(UNSAFE, ptr, value);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    public static void memPutShort(long ptr, short value) {
        checkInit();
        putShort(ptr, value);
    }

    public static void memPutLong(long ptr, long value) {
        checkInit();
        putLong(ptr, value);
    }

    public static byte memGetByte(long ptr) {
        checkInit();
        try {
            return (Byte) GET_BYTE.invoke(UNSAFE, ptr);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    public static short memGetShort(long ptr) {
        checkInit();
        return getShort(ptr);
    }

    public static long memGetLong(long ptr) {
        checkInit();
        return getLong(ptr);
    }

    public static void memPutInt(long ptr, int value) {
        checkInit();
        try {
            PUT_INT.invoke(UNSAFE, ptr, value);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    public static void memPutFloat(long ptr, float value) {
        checkInit();
        try {
            PUT_FLOAT.invoke(UNSAFE, ptr, value);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    /** Stores a pointer-sized (64 bit) value. */
    public static void memPutAddress(long ptr, long value) {
        checkInit();
        putLong(ptr, value);
    }

    public static float memGetFloat(long ptr) {
        checkInit();
        try {
            return (Float) GET_FLOAT.invoke(UNSAFE, ptr);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    public static int memGetInt(long ptr) {
        checkInit();
        try {
            return (Integer) GET_INT.invoke(UNSAFE, ptr);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    /** Reads a pointer-sized (64 bit) value. */
    public static long memGetAddress(long ptr) {
        checkInit();
        return getLong(ptr);
    }

    // buffer wrapping / addressing

    /**
     * Wraps a native memory block into a LITTLE_ENDIAN direct ByteBuffer.
     * The buffer does not own the memory; freeing is the caller's duty.
     */
    public static ByteBuffer memByteBuffer(long address, int size) {
        checkInit();
        try {
            ByteBuffer buffer = (ByteBuffer) DIRECT_BB_CTOR.newInstance(address, size);
            return buffer.order(ByteOrder.LITTLE_ENDIAN);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    /** Raw base address of a direct buffer, independent of its common position. */
    public static long memAddress0(java.nio.Buffer buffer) {
        checkInit();
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        if (!DIRECT_BB_CLASS.isInstance(buffer)) {
            throw new IllegalArgumentException("Not a direct buffer");
        }
        try {
            return (Long) DIRECT_BB_ADDRESS.get(buffer);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    public static long memAddress(ByteBuffer buffer) {
        return memAddress0(buffer);
    }

    public static long memAddress(ByteBuffer buffer, int byteOffset) {
        return memAddress0(buffer) + byteOffset;
    }

    // LWJGL 3 provides one overload per buffer type for memAddress.

    public static long memAddress(ShortBuffer buffer) {
        return memAddress0(buffer);
    }

    public static long memAddress(IntBuffer buffer) {
        return memAddress0(buffer);
    }

    public static long memAddress(IntBuffer buffer, int byteOffset) {
        return memAddress0(buffer) + byteOffset;
    }

    public static long memAddress(LongBuffer buffer) {
        return memAddress0(buffer);
    }

    public static long memAddress(FloatBuffer buffer) {
        return memAddress0(buffer);
    }

    public static long memAddress(FloatBuffer buffer, int byteOffset) {
        return memAddress0(buffer) + byteOffset;
    }

    public static long memAddress(DoubleBuffer buffer) {
        return memAddress0(buffer);
    }

    public static long memAddress(PointerBuffer buffer) {
        return buffer.address0();
    }

    public static long memAddress(PointerBuffer buffer, int byteOffset) {
        return buffer.address0() + byteOffset;
    }

    /** Address of a position-relative buffer; 0 when the buffer is null. */
    public static long memAddressSafe(ByteBuffer buffer) {
        return buffer == null ? NULL : memAddress0(buffer);
    }

    /**
     * Creates a view of {@code size} bytes at {@code buffer.position() + offset}.
     * The returned buffer shares nothing else with the source.
     */
    public static ByteBuffer memSlice(ByteBuffer buffer, int offset, int size) {
        int position = buffer.position() + offset;
        ByteBuffer duplicate = (ByteBuffer) buffer.duplicate().limit(position + size).position(position);
        return duplicate.slice();
    }

    /**
     * Grows (or shrinks) a ByteBuffer allocated by this class. Content up to
     * {@code min(src.remaining(), newCapacity)} bytes is preserved.
     */
    public static ByteBuffer memRealloc(ByteBuffer src, int newCapacity) {
        long dst = nmemAlloc(newCapacity);
        if (dst == NULL) {
            throw new OutOfMemoryError("Failed to reallocate " + newCapacity + " bytes");
        }

        if (src != null) {
            long srcAddress = memAddress0(src);
            int bytes = Math.min(src.remaining(), newCapacity);
            if (bytes > 0) {
                memCopy(srcAddress, dst, bytes);
            }
            nmemFree(srcAddress);
        }

        return memByteBuffer(dst, newCapacity)/* position 0, limit capacity */;
    }

    // string encoding / decoding

    public static ByteBuffer memUTF8(CharSequence text, boolean nullTerminated) {
        byte[] encoded = text.toString().getBytes(StandardCharsets.UTF_8);
        int size = encoded.length + (nullTerminated ? 1 : 0);

        // Aligned like LWJGL 3's nmemAlloc-based encodes
        long ptr = nmemAlloc(size);
        ByteBuffer buffer = memByteBuffer(ptr, size);
        buffer.put(encoded);
        if (nullTerminated) {
            buffer.put((byte) 0);
        }
        buffer.flip();
        return buffer;
    }

    public static ByteBuffer memUTF8(CharSequence text) {
        return memUTF8(text, false);
    }

    /** Encodes {@code value} as null-terminated UTF-16LE into a native block. */
    public static ByteBuffer memUTF16Safe(CharSequence value, boolean nullTerminated) {
        int len = value.length();
        int bytes = memLengthUTF16(value, nullTerminated);
        ByteBuffer buffer = memByteBuffer(nmemAlloc(bytes), bytes);
        long address = memAddress0(buffer);

        for (int i = 0; i < len; i++) {
            putShort(address + ((long) i << 1), (short) value.charAt(i));
        }
        if (nullTerminated) {
            putShort(address + ((long) len << 1), (short) 0);
        }
        return buffer;
    }

    public static ByteBuffer memUTF16(CharSequence value, boolean nullTerminated) {
        return memUTF16Safe(value, nullTerminated);
    }

    public static ByteBuffer memUTF16(CharSequence value) {
        return memUTF16Safe(value, false);
    }

    /** Writes an encoded UTF-16LE string into {@code target}; position is unchanged. Returns the size written in bytes. */
    public static int memUTF16(CharSequence value, boolean nullTerminated, ByteBuffer target) {
        long address = memAddress0(target) + target.position();
        int len = value.length();

        for (int i = 0; i < len; i++) {
            putShort(address + ((long) i << 1), (short) value.charAt(i));
        }
        if (nullTerminated) {
            putShort(address + ((long) len << 1), (short) 0);
        }
        return (int) memLengthUTF16(value, nullTerminated);
    }

    /** Decodes a null-terminated UTF-16LE string at {@code ptr}. Null pointer yields null. */
    public static String memUTF16(long ptr) {
        if (ptr == NULL) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (long p = ptr;; p += 2) {
            int unit = getShort(p) & 0xFFFF;
            if (unit == 0) {
                break;
            }
            sb.append((char) unit);
        }
        return sb.toString();
    }

    /** Decodes exactly {@code len} bytes of UTF-16LE at {@code ptr} (LWJGL 3 semantics: len in BYTES). */
    public static String memUTF16(long ptr, int len) {
        if (ptr == NULL) {
            return null;
        }
        StringBuilder sb = new StringBuilder(len >> 1);
        long end = ptr + len;
        for (long p = ptr; p < end; p += 2) {
            sb.append((char) (getShort(p) & 0xFFFF));
        }
        return sb.toString();
    }

    /** Decodes exactly {@code len} bytes of UTF-16LE starting at the buffer's position. */
    public static String memUTF16(ByteBuffer buffer, int len) {
        return memUTF16(memAddress0(buffer) + buffer.position(), len);
    }

    public static String memUTF16(ByteBuffer buffer) {
        return memUTF16(buffer, buffer.remaining());
    }
}


