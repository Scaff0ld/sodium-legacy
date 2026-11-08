package me.sodiumlegacy.mod.client.render.vertex;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatDescription;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatRegistry;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.util.Map;
import java.util.concurrent.locks.StampedLock;

public class VertexFormatRegistryImpl implements VertexFormatRegistry {
    private final Map<VertexFormat, VertexFormatDescriptionImpl> descriptions = new Reference2ReferenceOpenHashMap<>();

    private final StampedLock lock = new StampedLock();

    @Override
    public VertexFormatDescription get(VertexFormat format) {
        VertexFormatDescription desc = this.findExisting(format);

        if (desc == null) {
            desc = this.create(format);
        }

        return desc;
    }

    private VertexFormatDescription findExisting(VertexFormat format) {
        long stamp = this.lock.readLock();

        try {
            return this.descriptions.get(format);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    private VertexFormatDescription create(VertexFormat format) {
        long stamp = this.lock.writeLock();

        int id = this.descriptions.size();
        VertexFormatDescriptionImpl desc = new VertexFormatDescriptionImpl(format, id);

        try {
            this.descriptions.put(format, desc);
        } finally {
            this.lock.unlockWrite(stamp);
        }

        return desc;
    }
}

