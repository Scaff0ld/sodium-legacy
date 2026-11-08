package me.sodiumlegacy.mod.client.render.vertex;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import me.sodiumlegacy.mod.client.SodiumClientMod;
// 1.8.9 port: no generic VertexConsumer interface exists; BufferBuilder is the vanilla consumer
import net.minecraft.client.render.BufferBuilder;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class VertexConsumerTracker {
    private static final Logger LOGGER = LogManager.getLogger("SodiumLegacy-VertexConsumerTracker");
    private static final ReferenceSet<Class<? extends BufferBuilder>> BAD_CONSUMERS = ReferenceSets.synchronize(new ReferenceOpenHashSet<>());

    public static void logBadConsumer(BufferBuilder consumer) {
        if (BAD_CONSUMERS.add(consumer.getClass())) {
            LOGGER.warn("Class {} does not support optimized vertex writing code paths, which may cause reduced rendering performance",
                    consumer.getClass().getName());
        }
    }
}
