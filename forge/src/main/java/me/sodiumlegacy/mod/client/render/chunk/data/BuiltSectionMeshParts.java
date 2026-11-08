package me.sodiumlegacy.mod.client.render.chunk.data;

import me.sodiumlegacy.mod.client.gl.util.VertexRange;
import me.sodiumlegacy.mod.client.util.NativeBuffer;

public class BuiltSectionMeshParts {
    private final VertexRange[] ranges;
    private final NativeBuffer buffer;

    public BuiltSectionMeshParts(NativeBuffer buffer, VertexRange[] ranges) {
        this.ranges = ranges;
        this.buffer = buffer;
    }

    public NativeBuffer getVertexData() {
        return this.buffer;
    }

    public VertexRange[] getVertexRanges() {
        return this.ranges;
    }
}
