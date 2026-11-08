package me.sodiumlegacy.lwjgl3shim.opengl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/**
 * LWJGL 3 GL30 -> LWJGL 2 delegation shim (monolithic LWJGL 2 layout: uniforms
 * live on GL20, etc.). Public surface limited to Sodium's call sites.
 */
public final class GL30C {

    // constants

    public static final int GL_MAP_READ_BIT = GL30.GL_MAP_READ_BIT;
    public static final int GL_MAP_WRITE_BIT = GL30.GL_MAP_WRITE_BIT;
    public static final int GL_MAP_INVALIDATE_RANGE_BIT = GL30.GL_MAP_INVALIDATE_RANGE_BIT;
    public static final int GL_MAP_INVALIDATE_BUFFER_BIT = GL30.GL_MAP_INVALIDATE_BUFFER_BIT;
    public static final int GL_MAP_FLUSH_EXPLICIT_BIT = GL30.GL_MAP_FLUSH_EXPLICIT_BIT;

    public static final int GL_EQUAL = GL11.GL_EQUAL;
    public static final int GL_LEQUAL = GL11.GL_LEQUAL;

    private GL30C() {
    }

    // vertex arrays

    public static void glBindVertexArray(int vertexArray) {
        GL30.glBindVertexArray(vertexArray);
    }

    public static void glDeleteVertexArrays(int vertexArray) {
        GL30.glDeleteVertexArrays(vertexArray);
    }

    public static int glGenVertexArrays() {
        return GL30.glGenVertexArrays();
    }

    public static void glVertexAttribIPointer(int index, int size, int type, int stride, long pointerOffset) {
        GL30.glVertexAttribIPointer(index, size, type, stride, pointerOffset);
    }

    // uniforms

    public static void glUniform1f(int location, float value) {
        GL20.glUniform1f(location, value);
    }

    public static void glUniform1i(int location, int value) {
        GL20.glUniform1i(location, value);
    }

    public static void glUniform3f(int location, float x, float y, float z) {
        GL20.glUniform3f(location, x, y, z);
    }

    /** LWJGL 3's float[] overload; LWJGL 2 only exposes FloatBuffer uniforms. */
    public static void glUniform3fv(int location, float[] value) {
        FloatBuffer buffer = org.lwjgl.BufferUtils.createFloatBuffer(value.length);
        buffer.put(value);
        buffer.flip();
        GL20.glUniform3(location, buffer);
    }

    /** LWJGL 3's float[] overload; LWJGL 2 only exposes FloatBuffer uniforms. */
    public static void glUniform4fv(int location, float[] value) {
        FloatBuffer buffer = org.lwjgl.BufferUtils.createFloatBuffer(value.length);
        buffer.put(value);
        buffer.flip();
        GL20.glUniform4(location, buffer);
    }

    public static void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        GL20.glUniformMatrix4(location, transpose, value);
    }

    // program binding

    public static void glBindFragDataLocation(int program, int colorNumber, CharSequence name) {
        GL30.glBindFragDataLocation(program, colorNumber, name);
    }
}


