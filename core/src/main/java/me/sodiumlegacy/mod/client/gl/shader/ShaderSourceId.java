package me.sodiumlegacy.mod.client.gl.shader;

/**
 * A loader-agnostic identifier for a shader resource, replacing the Minecraft
 * {@code Identifier} so the shader pipeline stays Minecraft-free. Mirrors
 * {@code namespace:path} semantics and formatting.
 */
public final class ShaderSourceId {
    private final String namespace;
    private final String path;

    public ShaderSourceId(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public String getPath() {
        return this.path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ShaderSourceId)) {
            return false;
        }

        ShaderSourceId that = (ShaderSourceId) o;
        return this.namespace.equals(that.namespace) && this.path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return 31 * this.namespace.hashCode() + this.path.hashCode();
    }

    @Override
    public String toString() {
        return this.namespace + ":" + this.path;
    }
}
