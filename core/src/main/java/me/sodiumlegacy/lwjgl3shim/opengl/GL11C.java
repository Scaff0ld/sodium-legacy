package me.sodiumlegacy.lwjgl3shim.opengl;

import org.lwjgl.opengl.GL11;

/**
 * LWJGL 3 GL11 -> LWJGL 2 GL11 delegation shim. Public surface is limited to
 * what the Sodium sources use.
 */
public final class GL11C {

    public static final int GL_VENDOR = GL11.GL_VENDOR;
    public static final int GL_RENDERER = GL11.GL_RENDERER;
    public static final int GL_VERSION = GL11.GL_VERSION;
    public static final int GL_MODELVIEW_MATRIX = GL11.GL_MODELVIEW_MATRIX;
    public static final int GL_PROJECTION_MATRIX = GL11.GL_PROJECTION_MATRIX;
    public static final int GL_FOG_MODE = GL11.GL_FOG_MODE;
    public static final int GL_FOG_START = GL11.GL_FOG_START;
    public static final int GL_FOG_END = GL11.GL_FOG_END;
    public static final int GL_FOG_COLOR = GL11.GL_FOG_COLOR;
    public static final int GL_LINEAR = GL11.GL_LINEAR;

    private GL11C() {
    }

    public static String glGetString(int name) {
        return GL11.glGetString(name);
    }

    public static void glGetFloatv(int pname, java.nio.FloatBuffer params) {
        GL11.glGetFloat(pname, params);
    }

    public static float glGetFloat(int pname) {
        return GL11.glGetFloat(pname);
    }
}


