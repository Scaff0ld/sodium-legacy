package me.sodiumlegacy.mod.api.vertex.serializer;

import me.sodiumlegacy.mod.api.internal.DependencyInjection;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatDescription;

public interface VertexSerializerRegistry {
    VertexSerializerRegistry INSTANCE = DependencyInjection.load(VertexSerializerRegistry.class,
            "me.sodiumlegacy.mod.client.render.vertex.serializers.VertexSerializerRegistryImpl");

    static VertexSerializerRegistry instance() {
        return INSTANCE;
    }

    VertexSerializer get(VertexFormatDescription srcFormat, VertexFormatDescription dstFormat);
}
