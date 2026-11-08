package me.sodiumlegacy.mod.client.compatibility.environment.probe;

public final class GraphicsAdapterInfo {
    private final GraphicsAdapterVendor vendor;
    private final String name;
    private final String version;

    public GraphicsAdapterInfo(GraphicsAdapterVendor vendor, String name, String version) {
        this.vendor = vendor;
        this.name = name;
        this.version = version;
    }

    public GraphicsAdapterVendor vendor() {
        return this.vendor;
    }

    public String name() {
        return this.name;
    }

    public String version() {
        return this.version;
    }
}
