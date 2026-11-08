package me.sodiumlegacy.mod.mixin.core.client.network;

import me.sodiumlegacy.mod.client.render.chunk.map.ChunkStatus;
import me.sodiumlegacy.mod.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkMapS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.8.9 port: replaces the fabric-api client chunk events which upstream 0.5.x used to drive
 * the {@code ChunkTracker} (those events do not exist on 1.8.9).
 *
 * <p>The tracker considers a chunk "ready" only once it has both block and light data, so the
 * load flags must be set when the chunk's {@code WorldChunk} has actually received its section
 * data. {@link ClientWorld#handleChunk(int, int, boolean)} is called by the network handler
 * <em>before</em> the section assignment ({@code WorldChunk#method_3895}); flagging there would
 * let the chunk builders see empty sections, similar to how upstream 1.16+ flags arrive on the
 * chunk-full transition, not on the proto-chunk one. The tail of {@code onChunkData} /
 * {@code onChunkMap} is the first point where the data is in place.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Shadow
    private ClientWorld world;

    /**
     * Adds (or keeps) full status for the chunk. Packets that only refresh an already loaded
     * chunk are harmless: the tracker's {@code onChunkStatusAdded} is idempotent
     * ({@code prev == cur -> return}). Unload packets ({@code method_7760() == 0}) return
     * through an early {@code return} between the {@code handleChunk(..., false)} call and
     * the method tail, so they never reach this injection.
     */
    @Inject(method = "onChunkData", at = @At("TAIL"))
    private void sodium$onChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        if (packet.shouldLoad()) {
            ChunkTrackerHolder.get(this.world)
                    .onChunkStatusAdded(packet.getChunkX(), packet.getChunkZ(), ChunkStatus.FLAG_ALL);
        }
    }

    @Inject(method = "onChunkMap", at = @At("TAIL"))
    private void sodium$onChunkMap(ChunkMapS2CPacket packet, CallbackInfo ci) {
        // Every chunk listed here had its data assigned inside the handler's loop
        // (this.world.handleChunk(...) + chunk.method_3895(...)); TAIL is after the loop.
        for (int i = 0; i < packet.getXPosLength(); i++) {
            ChunkTrackerHolder.get(this.world)
                    .onChunkStatusAdded(packet.getXPos(i), packet.getZPos(i), ChunkStatus.FLAG_ALL);
        }
    }
}
