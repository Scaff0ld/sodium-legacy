package me.sodiumlegacy.mod.client.platform.windows.api.version;

public final class QueryResult {
    private final long address;
    private final int length;

    public QueryResult(long address, int length) {
        this.address = address;
        this.length = length;
    }

    public long address() {
        return this.address;
    }

    public int length() {
        return this.length;
    }
}
