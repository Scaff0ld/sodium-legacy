package me.sodiumlegacy.mod.client.gl.arena.staging;

import me.sodiumlegacy.mod.client.gl.buffer.GlBuffer;
import me.sodiumlegacy.mod.client.gl.buffer.GlBufferUsage;
import me.sodiumlegacy.mod.client.gl.buffer.GlMutableBuffer;
import me.sodiumlegacy.mod.client.gl.device.CommandList;

import java.nio.ByteBuffer;

/**
 * The staging buffer used when persistent mapping is not available (no ARB_buffer_storage).
 *
 * The buffer's storage is allocated once and grown only when a larger transfer arrives. Each copy
 * is then a single {@code glBufferSubData} + {@code glCopyBufferSubData} pair, instead of the old
 * path which queued a full {@code glBufferData} re-allocation (an implicit GPU stall on most
 * 2.1-era drivers) for every single upload.
 */
public class FallbackStagingBuffer implements StagingBuffer {
    private static final int INITIAL_CAPACITY = 1024 * 1024 * 16 /* 16 MiB, matches MappedStagingBuffer */;

    private final GlMutableBuffer fallbackBufferObject;
    private long capacity = 0;

    public FallbackStagingBuffer(CommandList commandList) {
        this.fallbackBufferObject = commandList.createMutableBuffer();
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset) {
        if (data.remaining() > this.capacity) {
            this.setCapacity(commandList, ceilToPow2(data.remaining()));
        }

        commandList.uploadSubData(this.fallbackBufferObject, 0, data);
        commandList.copyBufferSubData(this.fallbackBufferObject, dst, 0, writeOffset, data.remaining());
    }

    @Override
    public void flush(CommandList commandList) {
        // Storage is kept around between frames instead of being freed and re-allocated on each upload.
    }

    @Override
    public void delete(CommandList commandList) {
        commandList.deleteBuffer(this.fallbackBufferObject);
    }

    @Override
    public void flip() {

    }

    private void setCapacity(CommandList commandList, long capacity) {
        commandList.allocateStorage(this.fallbackBufferObject, capacity, GlBufferUsage.STREAM_COPY);
        this.capacity = capacity;
    }

    private static long ceilToPow2(int value) {
        long capacity = Math.max(INITIAL_CAPACITY, 1);
        while (capacity < value) {
            capacity <<= 1;
        }
        return capacity;
    }

    @Override
    public String toString() {
        return "Fallback";
    }
}
