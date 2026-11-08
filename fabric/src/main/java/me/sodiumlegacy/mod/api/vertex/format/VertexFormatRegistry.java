package me.sodiumlegacy.mod.api.vertex.format;

import me.sodiumlegacy.mod.api.internal.DependencyInjection;
import net.minecraft.client.render.VertexFormat;

public interface VertexFormatRegistry {
    VertexFormatRegistry INSTANCE = DependencyInjection.load(VertexFormatRegistry.class,
            "me.sodiumlegacy.mod.client.render.vertex.VertexFormatRegistryImpl");

    static VertexFormatRegistry instance() {
        return INSTANCE;
    }

    VertexFormatDescription get(VertexFormat format);
}