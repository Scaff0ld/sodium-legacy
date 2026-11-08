package me.sodiumlegacy.mod.client.gl.arena.staging;

import me.sodiumlegacy.mod.client.gl.buffer.GlBuffer;
import me.sodiumlegacy.mod.client.gl.device.CommandList;

import java.nio.ByteBuffer;

public interface StagingBuffer {
    void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset);

    void flush(CommandList commandList);

    void delete(CommandList commandList);

    void flip();
}
