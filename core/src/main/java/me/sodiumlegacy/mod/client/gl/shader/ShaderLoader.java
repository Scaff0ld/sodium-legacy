package me.sodiumlegacy.mod.client.gl.shader;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ShaderLoader {
    /**
     * Creates an OpenGL shader from GLSL sources. The GLSL source file should be made available on the classpath at the
     * path of `/assets/{namespace}/shaders/{path}`. User defines can be used to declare variables in the shader source
     * after the version header, allowing for conditional compilation with macro code.
     *
     * @param type The type of shader to create
     * @param name The ShaderSourceId used to locate the shader source file
     * @param constants A list of constants for shader specialization
     * @return An OpenGL shader object compiled with the given user defines
     */
    public static GlShader loadShader(ShaderType type, ShaderSourceId name, ShaderConstants constants) {
        return new GlShader(type, name, ShaderParser.parseShader(getShaderSource(name), constants));
    }

    public static String getShaderSource(ShaderSourceId name) {
        String path = String.format("/assets/%s/shaders/%s", name.getNamespace(), name.getPath());

        try (InputStream in = ShaderLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new RuntimeException("Shader not found: " + path);
            }

            // 1.8.9 port: a UTF-8 BOM at the start of the source is legal text but is NOT
            // accepted by the driver's glShaderSource on the GL 4.3-era desktop drivers used
            // with 1.8.9.
            String src = IOUtils.toString(in, StandardCharsets.UTF_8);

            if (src.startsWith("\uFEFF")) {
                src = src.substring(1);
            }

            return src;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader source for " + path, e);
        }
    }
}
