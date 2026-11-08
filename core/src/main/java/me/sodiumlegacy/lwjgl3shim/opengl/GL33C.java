package me.sodiumlegacy.lwjgl3shim.opengl;

/**
 * LWJGL 3 GL33 -> LWJGL 2 delegation shim. Only referenced members exist.
 * (The javadoc of Sodium's MultiDrawBatch also mentions
 * {@code glMultiDrawElementsBaseVertex}, which is absent in LWJGL 2; see GL32C.)
 */
public final class GL33C {

    public static final int GL_MAP_UNSYNCHRONIZED_BIT = 0x0020; // not exposed by LWJGL 2 (GL33 core value)

    private GL33C() {
    }
}

