package me.sodiumlegacy.mod.client.render.chunk.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.gl.arena.GlBufferArena;
import me.sodiumlegacy.mod.client.gl.arena.PendingUpload;
import me.sodiumlegacy.mod.client.gl.arena.staging.FallbackStagingBuffer;
import me.sodiumlegacy.mod.client.gl.arena.staging.MappedStagingBuffer;
import me.sodiumlegacy.mod.client.gl.arena.staging.StagingBuffer;
import me.sodiumlegacy.mod.client.gl.device.CommandList;
import me.sodiumlegacy.mod.client.gl.device.RenderDevice;
import me.sodiumlegacy.mod.client.render.chunk.RenderSection;
import me.sodiumlegacy.mod.client.render.chunk.compile.ChunkBuildOutput;
import me.sodiumlegacy.mod.client.render.chunk.data.BuiltSectionMeshParts;
import me.sodiumlegacy.mod.client.render.chunk.data.SectionRenderDataStorage;
import me.sodiumlegacy.mod.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.sodiumlegacy.mod.client.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RenderRegionManager {
    private final Long2ReferenceOpenHashMap<RenderRegion> regions = new Long2ReferenceOpenHashMap<>();

    private final StagingBuffer stagingBuffer;

    public RenderRegionManager(CommandList commandList) {
        this.stagingBuffer = createStagingBuffer(commandList);
    }

    public void update() {
        this.stagingBuffer.flip();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            Iterator<RenderRegion> it = this.regions.values()
                    .iterator();

            while (it.hasNext()) {
                RenderRegion region = it.next();
                region.update(commandList);

                if (region.isEmpty()) {
                    region.delete(commandList);

                    it.remove();
                }
            }
        }
    }

    public void uploadMeshes(CommandList commandList, Collection<ChunkBuildOutput> results) {
        for (Reference2ReferenceMap.Entry<RenderRegion, List<ChunkBuildOutput>> entry : this.createMeshUploadQueues(results)) {
            this.uploadMeshes(commandList, entry.getKey(), entry.getValue());
        }
    }

    private void uploadMeshes(CommandList commandList, RenderRegion region, Collection<ChunkBuildOutput> results) {
        ArrayList<PendingSectionUpload> uploads = new ArrayList<>();

        for (ChunkBuildOutput result : results) {
            for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
                SectionRenderDataStorage storage = region.getStorage(pass);

                if (storage != null) {
                    storage.removeMeshes(result.render.getSectionIndex());
                }

                BuiltSectionMeshParts mesh = result.getMesh(pass);

                if (mesh != null) {
                    uploads.add(new PendingSectionUpload(result.render, mesh, pass,
                            new PendingUpload(mesh.getVertexData())));
                }
            }
        }

        // If we have nothing to upload, abort!
        if (uploads.isEmpty()) {
            return;
        }

        RenderRegion.DeviceResources resources = region.createResources(commandList);
        GlBufferArena arena = resources.getGeometryArena();

        boolean bufferChanged = arena.upload(commandList, uploads.stream()
                .map(upload -> upload.vertexUpload));

        // If any of the buffers changed, the tessellation will need to be updated
        // Once invalidated the tessellation will be re-created on the next attempted use
        if (bufferChanged) {
            region.refresh(commandList);
        }

        // Collect the upload results
        for (PendingSectionUpload upload : uploads) {
            SectionRenderDataStorage storage = region.createStorage(upload.pass);

            storage.setMeshes(upload.section.getSectionIndex(),
                    upload.vertexUpload.getResult(), upload.meshData.getVertexRanges());
        }
    }

    private Reference2ReferenceMap.FastEntrySet<RenderRegion, List<ChunkBuildOutput>> createMeshUploadQueues(Collection<ChunkBuildOutput> results) {
        Reference2ReferenceOpenHashMap<RenderRegion, List<ChunkBuildOutput>> map = new Reference2ReferenceOpenHashMap<>();

        for (ChunkBuildOutput result : results) {
            List<ChunkBuildOutput> queue = map.computeIfAbsent(result.render.getRegion(), k -> new ArrayList<>());
            queue.add(result);
        }

        return map.reference2ReferenceEntrySet();
    }

    public void delete(CommandList commandList) {
        for (RenderRegion region : this.regions.values()) {
            region.delete(commandList);
        }

        this.regions.clear();
        this.stagingBuffer.delete(commandList);
    }

    public Collection<RenderRegion> getLoadedRegions() {
        return this.regions.values();
    }

    public StagingBuffer getStagingBuffer() {
        return this.stagingBuffer;
    }

    public RenderRegion createForChunk(int chunkX, int chunkY, int chunkZ) {
        return this.create(chunkX >> RenderRegion.REGION_WIDTH_SH,
                chunkY >> RenderRegion.REGION_HEIGHT_SH,
                chunkZ >> RenderRegion.REGION_LENGTH_SH);
    }

    @NotNull
    private RenderRegion create(int x, int y, int z) {
        long key = RenderRegion.key(x, y, z);
        RenderRegion instance = this.regions.get(key);

        if (instance == null) {
            this.regions.put(key, instance = new RenderRegion(x, y, z, this.stagingBuffer));
        }

        return instance;
    }

    private static final class PendingSectionUpload {
        private final RenderSection section;
        private final BuiltSectionMeshParts meshData;
        private final TerrainRenderPass pass;
        private final PendingUpload vertexUpload;

        private PendingSectionUpload(RenderSection section, BuiltSectionMeshParts meshData, TerrainRenderPass pass, PendingUpload vertexUpload) {
            this.section = section;
            this.meshData = meshData;
            this.pass = pass;
            this.vertexUpload = vertexUpload;
        }
    }


    private static StagingBuffer createStagingBuffer(CommandList commandList) {
        if (SodiumClientMod.options().advanced.useAdvancedStagingBuffers && MappedStagingBuffer.isSupported(RenderDevice.INSTANCE)) {
            return new MappedStagingBuffer(commandList);
        }

        return new FallbackStagingBuffer(commandList);
    }
}
