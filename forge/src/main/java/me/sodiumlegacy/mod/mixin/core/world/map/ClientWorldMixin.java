package me.sodiumlegacy.mod.mixin.core.world.map;

import me.sodiumlegacy.mod.client.render.chunk.map.ChunkStatus;
import me.sodiumlegacy.mod.client.render.chunk.map.ChunkTracker;
import me.sodiumlegacy.mod.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.multiplayer.WorldClient;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(WorldClient.class)
public class ClientWorldMixin implements ChunkTrackerHolder {
    @Unique
    private final ChunkTracker chunkTracker = new ChunkTracker();

    @Override
    public ChunkTracker sodium$getTracker() {
        return Validate.notNull(this.chunkTracker);
    }

    /**
     * 1.8.9 port: {@code handleChunk(x, z, false)} is the client's only chunk unload path
     * (invoked by ClientPlayNetworkHandler for unload packets). Removing the status flags here
     * makes the tracker drop the neighbors from their "ready" state, where the running
     * {@code SodiumWorldRenderer.setupTerrain} drains the unload queue into
     * {@code RenderSectionManager#onChunkRemoved}. Load events are tracked from
     * {@code ClientPlayNetworkHandlerMixin} instead, at the point where the chunk's section
     * data has actually landed.
     */
    @Inject(method = "doPreChunk", at = @At("HEAD"))
    private void sodium$onHandleChunk(int x, int z, boolean load, CallbackInfo ci) {
        if (!load) {
            ChunkTrackerHolder.get((WorldClient) (Object) this)
                    .onChunkStatusRemoved(x, z, ChunkStatus.FLAG_ALL);
        }
    }
}
