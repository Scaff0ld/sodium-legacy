package me.sodiumlegacy.lwjgl3shim.opengl;

import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.ContextCapabilities;

/**
 * LWJGL 3 -> LWJGL 2 compatibility shim for Sodium's capability queries.
 *
 * <p>{@link #getCapabilities()} wraps LWJGL 2's live
 * {@link org.lwjgl.opengl.ContextCapabilities}. Only the boolean fields
 * actually read by Sodium are exposed; extension booleans that have no LWJGL 2
 * counterpart are bounded to {@code false} by what LWJGL 2 can know.</p>
 */
public final class GL {

    private GL() {
    }

    public static GLCapabilities getCapabilities() {
        ContextCapabilities caps = GLContext.getCapabilities();
        return new GLCapabilities(
                caps.OpenGL44,
                false /* GL 4.6 is not representable in LWJGL 2's capabilities */,
                caps.GL_ARB_buffer_storage,
                false /* KHR_no_error does not exist in LWJGL 2's capabilities */
        );
    }
}


