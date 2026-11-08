package me.sodiumlegacy.mod.client.gl.shader;

import me.sodiumlegacy.mod.client.gl.GlObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import me.sodiumlegacy.lwjgl3shim.opengl.GL20C;

/**
 * A compiled OpenGL shader object.
 */
public class GlShader extends GlObject {
    private static final Logger LOGGER = LogManager.getLogger(GlShader.class);

    private final ShaderSourceId name;

    public GlShader(ShaderType type, ShaderSourceId name, String src) {
        this.name = name;

        int handle = GL20C.glCreateShader(type.id);
        ShaderWorkarounds.safeShaderSource(handle, src);
        GL20C.glCompileShader(handle);

        String log = GL20C.glGetShaderInfoLog(handle);

        if (!log.isEmpty()) {
            LOGGER.warn("Shader compilation log for " + this.name + ": " + log);
        }

        int result = GL20C.glGetShaderi(handle, GL20C.GL_COMPILE_STATUS);

        if (result != GL20C.GL_TRUE) {
            throw new RuntimeException("Shader compilation failed for " + this.name
                    + " (report the GL InfoLog below):\n" + log);
        }

        this.setHandle(handle);
    }

    public ShaderSourceId getName() {
        return this.name;
    }

    public void delete() {
        GL20C.glDeleteShader(this.handle());

        this.invalidateHandle();
    }
}

