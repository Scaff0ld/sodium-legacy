package me.sodiumlegacy.lwjgl3shim.opengl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;

/**
 * LWJGL 3 GL20 -> LWJGL 2 delegation shim (public surface limited to Sodium's
 * call sites). Uniform upload functions delegate to LWJGL 2's GL20 (monolithic
 * layout: uniforms live on GL20, buffer functions on GL15).
 */
public final class GL20C {

    // constants

    public static final int GL_ARRAY_BUFFER = GL15.GL_ARRAY_BUFFER;
    public static final int GL_ARRAY_BUFFER_BINDING = GL15.GL_ARRAY_BUFFER_BINDING;
    public static final int GL_ELEMENT_ARRAY_BUFFER = GL15.GL_ELEMENT_ARRAY_BUFFER;
    public static final int GL_ELEMENT_ARRAY_BUFFER_BINDING = GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING;

    public static final int GL_STREAM_DRAW = GL15.GL_STREAM_DRAW;
    public static final int GL_STREAM_READ = GL15.GL_STREAM_READ;
    public static final int GL_STREAM_COPY = GL15.GL_STREAM_COPY;
    public static final int GL_STATIC_DRAW = GL15.GL_STATIC_DRAW;
    public static final int GL_STATIC_READ = GL15.GL_STATIC_READ;
    public static final int GL_STATIC_COPY = GL15.GL_STATIC_COPY;
    public static final int GL_DYNAMIC_DRAW = GL15.GL_DYNAMIC_DRAW;
    public static final int GL_DYNAMIC_READ = GL15.GL_DYNAMIC_READ;
    public static final int GL_DYNAMIC_COPY = GL15.GL_DYNAMIC_COPY;

    public static final int GL_TRIANGLES = GL11.GL_TRIANGLES;

    public static final int GL_COMPILE_STATUS = GL20.GL_COMPILE_STATUS;
    public static final int GL_LINK_STATUS = GL20.GL_LINK_STATUS;
    public static final int GL_TRUE = GL11.GL_TRUE;

    public static final int GL_FLOAT = GL11.GL_FLOAT;
    public static final int GL_UNSIGNED_BYTE = GL11.GL_UNSIGNED_BYTE;
    public static final int GL_UNSIGNED_SHORT = GL11.GL_UNSIGNED_SHORT;
    public static final int GL_UNSIGNED_INT = GL11.GL_UNSIGNED_INT;

    public static final int GL_VERTEX_SHADER = GL20.GL_VERTEX_SHADER;
    public static final int GL_FRAGMENT_SHADER = GL20.GL_FRAGMENT_SHADER;

    private GL20C() {
    }

    // programs / shaders

    public static int glCreateShader(int type) {
        return GL20.glCreateShader(type);
    }

    // shaders

    /**
     * Standard single-string shader-source upload (equivalent to vanilla 1.8.9's
     * {@code GLX.gl20ShaderSource(shader, ByteBuffer)}). The length forwarded to the
     * native glue is the buffer's remaining() byte count, exactly as vanilla does.
     */
    public static void glShaderSource(int shader, ByteBuffer source) {
        GL20.glShaderSource(shader, source);
    }

    public static void glCompileShader(int shader) {
        GL20.glCompileShader(shader);
    }

    public static void glDeleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }

    public static int glCreateProgram() {
        return GL20.glCreateProgram();
    }

    public static void glAttachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }

    public static void glLinkProgram(int program) {
        GL20.glLinkProgram(program);
    }

    public static void glUseProgram(int program) {
        GL20.glUseProgram(program);
    }

    public static void glDeleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }

    public static String glGetShaderInfoLog(int shader) {
        return GL20.glGetShaderInfoLog(shader, GL20.glGetShader(shader, GL20.GL_INFO_LOG_LENGTH));
    }

    public static String glGetProgramInfoLog(int program) {
        return GL20.glGetProgramInfoLog(program, GL20.glGetProgram(program, GL20.GL_INFO_LOG_LENGTH));
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        return GL20.glGetUniformLocation(program, name);
    }

    public static void glBindAttribLocation(int program, int index, CharSequence name) {
        GL20.glBindAttribLocation(program, index, name);
    }

    // buffers

    public static void glBindBuffer(int target, int buffer) {
        GL15.glBindBuffer(target, buffer);
    }

    public static void glBufferData(int target, ByteBuffer data, int usage) {
        GL15.glBufferData(target, data, usage);
    }

    public static void glBufferData(int target, long size, int usage) {
        GL15.glBufferData(target, size, usage);
    }

    public static void glBufferSubData(int target, long offset, ByteBuffer data) {
        GL15.glBufferSubData(target, offset, data);
    }

    public static void glDeleteBuffers(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }

    public static int glGenBuffers() {
        return GL15.glGenBuffers();
    }

    // vertex attributes

    public static void glVertexAttribPointer(int index, int size, int type,
                                             boolean normalized, int stride, long pointerOffset) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointerOffset);
    }

    public static void glEnableVertexAttribArray(int index) {
        GL20.glEnableVertexAttribArray(index);
    }

    // shader / program queries

    /**
     * Single-value {@code glGetShaderiv} (LWJGL 3 shape). MC 1.8.9's
     * {@code GlStateManager} has no GLSL shader query helpers, so Sodium's
     * shader classes route here instead.
     */
    public static int glGetShaderi(int shader, int pname) {
        return GL20.glGetShader(shader, pname);
    }

    /**
     * Single-value {@code glGetProgramiv} (LWJGL 3 shape); see
     * {@link #glGetShaderi(int, int)} for why this exists on the port.
     */
    public static int glGetProgrami(int program, int pname) {
        return GL20.glGetProgram(program, pname);
    }
}


