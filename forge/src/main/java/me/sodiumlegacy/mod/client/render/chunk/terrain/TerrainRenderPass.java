package me.sodiumlegacy.mod.client.render.chunk.terrain;

import net.minecraft.util.EnumWorldBlockLayer;

public class TerrainRenderPass {
    @Deprecated
    private final EnumWorldBlockLayer layer;

    private final boolean useReverseOrder;
    private final boolean fragmentDiscard;

    public TerrainRenderPass(EnumWorldBlockLayer layer, boolean useReverseOrder, boolean allowFragmentDiscard) {
        this.layer = layer;

        this.useReverseOrder = useReverseOrder;
        this.fragmentDiscard = allowFragmentDiscard;
    }

    public boolean isReverseOrder() {
        return this.useReverseOrder;
    }

    @Deprecated
    public void startDrawing() {
        // no-op: 1.8.9's RenderLayer enum carries no GL state; layer state is applied by the
        // chunk shader binding path in this port.
    }

    @Deprecated
    public void endDrawing() {
        // no-op (see startDrawing)
    }

    public boolean supportsFragmentDiscard() {
        return this.fragmentDiscard;
    }
}
