package me.sodiumlegacy.mod.client.render.vertex;

import me.sodiumlegacy.mod.api.vertex.buffer.VertexBufferWriter;
import net.minecraft.client.renderer.WorldRenderer;

public class VertexConsumerUtils {
    /**
     * Attempt to convert a {@link WorldRenderer} into a {@link VertexBufferWriter}. If this fails, return null
     * and log a message.
     * @param consumer the consumer to convert
     * @return a {@link VertexBufferWriter}, or null if the consumer does not support this
     */
    public static VertexBufferWriter convertOrLog(WorldRenderer consumer) {
        VertexBufferWriter writer = VertexBufferWriter.tryOf(consumer);

        if (writer == null) {
            VertexConsumerTracker.logBadConsumer(consumer);
        }

        return writer;
    }
}

