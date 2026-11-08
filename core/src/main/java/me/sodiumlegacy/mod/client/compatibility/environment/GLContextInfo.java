package me.sodiumlegacy.mod.client.compatibility.environment;

import org.jetbrains.annotations.Nullable;
import me.sodiumlegacy.lwjgl3shim.opengl.GL11C;

public final class GLContextInfo {
    private final String vendor;
    private final String renderer;
    private final String version;

    public GLContextInfo(String vendor, String renderer, String version) {
        this.vendor = vendor;
        this.renderer = renderer;
        this.version = version;
    }

    public String vendor() {
        return this.vendor;
    }

    public String renderer() {
        return this.renderer;
    }

    public String version() {
        return this.version;
    }

    @Nullable
    public static GLContextInfo create() {
        String vendor = GL11C.glGetString(GL11C.GL_VENDOR);
        String renderer = GL11C.glGetString(GL11C.GL_RENDERER);
        String version = GL11C.glGetString(GL11C.GL_VERSION);

        if (vendor == null || renderer == null || version == null) {
            return null;
        }

        return new GLContextInfo(vendor, renderer, version);
    }
}

