package me.sodiumlegacy.lwjgl3shim.system;

/**
 * LWJGL 3-style native function-call bridge (compile-only shim).
 *
 * <p>Bodies throw: invoking arbitrary native function pointers requires the
 * libffi glue present in LWJGL 3 and cannot be provided on the MC 1.8.9 /
 * LWJGL 2 runtime. The affected Sodium platform classes (windows/unix) are
 * stubbed separately.</p>
 *
 * <p>Method naming follows LWJGL 3: argument type letters first, then the
 * return type letter ({@code P} = pointer/long, {@code I} = int, {@code V} =
 * return void). All overloads observed in the Sodium sources are provided with
 * the exact argument shapes used at those call sites.</p>
 */
public final class JNI {

    private JNI() {
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Not available on MC 1.8.9 / LWJGL2 (compile-only shim)");
    }

    /** callI(function): no arguments, int return. */
    public static int callI(long functionPointer) {
        throw unsupported();
    }

    /** callP(function): no arguments, pointer return. */
    public static long callP(long functionPointer) {
        throw unsupported();
    }

    /** callPI(pointer, function): one pointer argument, int return. */
    public static int callPI(long pointer, long functionPointer) {
        throw unsupported();
    }

    /** callPPI(pointer, pointer, function): two pointer arguments, int return. */
    public static int callPPI(long pointer1, long pointer2, long functionPointer) {
        throw unsupported();
    }

    /** callPPI(int flags, pointer, pointer, function): int/pointer/pointer arguments, int return. */
    public static int callPPI(int integer, long pointer1, long pointer2, long functionPointer) {
        throw unsupported();
    }

    /** callPPI(pointer, pointer, int, function): pointer/pointer/int arguments, int return. */
    public static int callPPI(long pointer1, long pointer2, int integer, long functionPointer) {
        throw unsupported();
    }

    /** callPPI(pointer, int, int, function): pointer/int/int arguments, int return. */
    public static int callPPI(long pointer1, int integer1, int integer2, long functionPointer) {
        throw unsupported();
    }

    /** callPPI(pointer, int, int, pointer, function): pointer/int/int/pointer arguments, int return. */
    public static int callPPI(long pointer1, int integer1, int integer2, long pointer2, long functionPointer) {
        throw unsupported();
    }

    /** callPPPPI(pointer, pointer, pointer, pointer, function): four pointer arguments, int return. */
    public static int callPPPPI(long pointer1, long pointer2, long pointer3, long pointer4, long functionPointer) {
        throw unsupported();
    }
}


