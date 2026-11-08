package me.sodiumlegacy.mod.mixin.core.client.network;

import me.sodiumlegacy.mod.client.render.chunk.map.ChunkStatus;
import me.sodiumlegacy.mod.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
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
 * load flags must be set when the chunk's {@code ExtendedBlockStorage}s have actually been
 * received. {@link NetHandlerPlayClient#handleChunkData(S21PacketChunkData)} is called by the
 * network handler <em>before</em> the section assignment; flagging there would let the chunk
 * builders see empty sections. The tail of {@code handleChunkData} /
 * {@code handleMapChunkBulk} is the first point where the data is in place.</p>
 */
@Mixin(NetHandlerPlayClient.class)
public class ClientPlayNetworkHandlerMixin {
    @Shadow
    private WorldClient clientWorldController;

    /**
     * Adds (or keeps) full status for the chunk. Packets that only refresh an already loaded
     * chunk are harmless: the tracker's {@code onChunkStatusAdded} is idempotent
     * ({@code prev == cur -> return}). Unload packets ({@code func_149274_i()} == false) return
     * through an early {@code return} between the world update and the method tail, so they
     * never reach this injection.
     */
    @Inject(method = "handleChunkData", at = @At("TAIL"))
    private void sodium$onChunkData(S21PacketChunkData packet, CallbackInfo ci) {
        if (packet.func_149274_i()) {
            ChunkTrackerHolder.get(this.clientWorldController)
                    .onChunkStatusAdded(packet.getChunkX(), packet.getChunkZ(), ChunkStatus.FLAG_ALL);
        }
    }

    @Inject(method = "handleMapChunkBulk", at = @At("TAIL"))
    private void sodium$onChunkMap(S26PacketMapChunkBulk packet, CallbackInfo ci) {
        // Every chunk listed here had its data assigned inside the handler's loop; TAIL is after the loop.
        for (int i = 0; i < packet.getChunkCount(); i++) {
            ChunkTrackerHolder.get(this.clientWorldController)
                    .onChunkStatusAdded(packet.getChunkX(i), packet.getChunkZ(i), ChunkStatus.FLAG_ALL);
        }
    }
}
