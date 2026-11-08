package me.sodiumlegacy.mod.client.gl.util;

public final class VertexRange {
    private final int vertexStart;
    private final int vertexCount;

    public VertexRange(int vertexStart, int vertexCount) {
        this.vertexStart = vertexStart;
        this.vertexCount = vertexCount;
    }

    public int vertexStart() {
        return this.vertexStart;
    }

    public int vertexCount() {
        return this.vertexCount;
    }
}
