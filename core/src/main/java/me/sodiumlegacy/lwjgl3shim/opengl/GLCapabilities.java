package me.sodiumlegacy.lwjgl3shim.opengl;

import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.ContextCapabilities;

/**
 * LWJGL 3-shaped capabilities wrapper (class, not enum) exposing exactly the
 * fields Sodium reads. Values derive from LWJGL 2's ContextCapabilities.
 */
public final class GLCapabilities {

    /** OpenGL 4.4 core support; sourced from LWJGL 2's ContextCapabilities.OpenGL44. */
    public final boolean OpenGL44;

    /** OpenGL 4.6 core support (LWJGL 2 cannot report a GL newer than 4.5). */
    public final boolean OpenGL46;

    /** ARB_buffer_storage support; sourced from LWJGL 2's ContextCapabilities. */
    public final boolean GL_ARB_buffer_storage;

    /** KHR_no_error support (no LWJGL 2 counterpart). */
    public final boolean GL_KHR_no_error;

    GLCapabilities(boolean OpenGL44, boolean OpenGL46, boolean ARB_buffer_storage, boolean KHR_no_error) {
        this.OpenGL44 = OpenGL44;
        this.OpenGL46 = OpenGL46;
        this.GL_ARB_buffer_storage = ARB_buffer_storage;
        this.GL_KHR_no_error = KHR_no_error;
    }
}


