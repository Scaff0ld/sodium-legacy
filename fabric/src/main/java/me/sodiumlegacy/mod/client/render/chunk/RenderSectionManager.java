package me.sodiumlegacy.mod.client.render.chunk;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.gl.arena.GlBufferArena;
import me.sodiumlegacy.mod.client.gl.device.CommandList;
import me.sodiumlegacy.mod.client.gl.device.RenderDevice;
import me.sodiumlegacy.mod.client.render.chunk.compile.ChunkBuildOutput;
import me.sodiumlegacy.mod.client.render.chunk.compile.executor.ChunkBuilder;
import me.sodiumlegacy.mod.client.render.chunk.compile.executor.ChunkJobResult;
import me.sodiumlegacy.mod.client.render.chunk.compile.executor.ChunkJobCollector;

import java.util.function.Consumer;
import me.sodiumlegacy.mod.client.render.chunk.compile.executor.ChunkJobResult;
import me.sodiumlegacy.mod.client.render.chunk.compile.executor.ChunkJobTyped;
import me.sodiumlegacy.mod.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import me.sodiumlegacy.mod.client.render.chunk.data.BuiltSectionInfo;
import me.sodiumlegacy.mod.client.render.chunk.lists.ChunkRenderList;
import me.sodiumlegacy.mod.client.render.chunk.lists.SortedRenderLists;
import me.sodiumlegacy.mod.client.render.chunk.lists.VisibleChunkCollector;
import me.sodiumlegacy.mod.client.render.chunk.occlusion.GraphDirection;
import me.sodiumlegacy.mod.client.render.chunk.occlusion.OcclusionCuller;
import me.sodiumlegacy.mod.client.render.chunk.shader.ChunkFogState;
import me.sodiumlegacy.mod.client.render.chunk.region.RenderRegion;
import me.sodiumlegacy.mod.client.render.chunk.region.RenderRegionManager;
import me.sodiumlegacy.mod.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.sodiumlegacy.mod.client.render.chunk.terrain.TerrainRenderPass;
import me.sodiumlegacy.mod.client.render.chunk.vertex.format.ChunkMeshFormats;
import me.sodiumlegacy.mod.client.render.texture.SpriteUtil;
import me.sodiumlegacy.mod.client.render.util.RenderAsserts;
import me.sodiumlegacy.mod.client.render.viewport.CameraTransform;
import me.sodiumlegacy.mod.client.render.viewport.Viewport;
import me.sodiumlegacy.mod.client.util.MathUtil;
import me.sodiumlegacy.mod.client.world.WorldSlice;
import me.sodiumlegacy.mod.client.world.cloned.ChunkRenderContext;
import me.sodiumlegacy.mod.client.world.cloned.ClonedChunkSectionCache;
import me.sodiumlegacy.mod.client.util.SectionPos;
import me.sodiumlegacy.mod.client.util.iterator.ByteIterator;
import me.sodiumlegacy.mod.client.util.task.CancellationToken;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import me.sodiumlegacy.mod.client.util.SectionPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class RenderSectionManager {
    private final ChunkBuilder builder;

    private final RenderRegionManager regions;
    private final ClonedChunkSectionCache sectionCache;

    private final Long2ReferenceMap<RenderSection> sectionByPosition = new Long2ReferenceOpenHashMap<>();

    private final ConcurrentLinkedDeque<ChunkJobResult<ChunkBuildOutput>> buildResults = new ConcurrentLinkedDeque<>();

    // Cached method reference: `this.buildResults::add` used by the per-frame ChunkJobCollectors
    // would otherwise allocate a fresh lambda instance on every call to updateChunks().
    private final Consumer<ChunkJobResult<ChunkBuildOutput>> buildResultsConsumer = this.buildResults::add;

    private final ChunkRenderer chunkRenderer;

    private final ClientWorld world;

    private final ReferenceSet<RenderSection> sectionsWithGlobalEntities = new ReferenceOpenHashSet<>();

    private final OcclusionCuller occlusionCuller;

    private final int renderDistance;

    @NotNull
    private SortedRenderLists renderLists;

    @NotNull
    private Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildLists;

    private int lastUpdatedFrame;

    private boolean needsUpdate;

    // Re-used camera position: a fresh BlockPos per graph update was pure GC churn on the
    // hot path (the graph updates on every frame the camera moves).
    private final BlockPos.Mutable lastCameraPosition = new BlockPos.Mutable();

    // Re-used visitor: allocating one per graph update churned the GC while the camera moves.
    private final VisibleChunkCollector visitor = new VisibleChunkCollector();

    public RenderSectionManager(ClientWorld world, int renderDistance, CommandList commandList) {
        this.chunkRenderer = new DefaultChunkRenderer(RenderDevice.INSTANCE, ChunkMeshFormats.COMPACT);

        this.world = world;
        this.builder = new ChunkBuilder(world, ChunkMeshFormats.COMPACT);

        this.needsUpdate = true;
        this.renderDistance = renderDistance;

        this.regions = new RenderRegionManager(commandList);
        this.sectionCache = new ClonedChunkSectionCache(this.world);

        this.renderLists = SortedRenderLists.empty();
        this.occlusionCuller = new OcclusionCuller(Long2ReferenceMaps.unmodifiable(this.sectionByPosition), this.world);

        this.rebuildLists = new EnumMap<>(ChunkUpdateType.class);

        for (ChunkUpdateType type : ChunkUpdateType.values()) {
            this.rebuildLists.put(type, new ArrayDeque<>());
        }
    }

    public void update(double cameraX, double cameraY, double cameraZ, Viewport viewport, int frame, boolean spectator) {
        this.lastCameraPosition.setPosition(
                (int) Math.floor(cameraX),
                (int) Math.floor(cameraY),
                (int) Math.floor(cameraZ));

        this.createTerrainRenderList(viewport, frame, spectator);

        this.needsUpdate = false;
        this.lastUpdatedFrame = frame;
    }

    private void createTerrainRenderList(Viewport viewport, int frame, boolean spectator) {
        this.resetRenderLists();

        final float searchDistance = this.getSearchDistance();
        final boolean useOcclusionCulling = this.shouldUseOcclusionCulling(spectator);

        this.visitor.begin(frame);

        this.occlusionCuller.findVisible(this.visitor, viewport, searchDistance, useOcclusionCulling, frame);

        this.renderLists = this.visitor.createRenderLists();
        this.rebuildLists = this.visitor.getRebuildLists();
    }

    private float getSearchDistance() {
        float distance;

        if (SodiumClientMod.options().performance.useFogOcclusion) {
            distance = this.getEffectiveRenderDistance();
        } else {
            distance = this.getRenderDistance();
        }

        return distance;
    }

    private boolean shouldUseOcclusionCulling(boolean spectator) {
        boolean useOcclusionCulling;

        if (spectator && this.isOpaqueFullCube(this.lastCameraPosition)) {
            useOcclusionCulling = false;
        } else {
            useOcclusionCulling = MinecraftClient.getInstance().chunkCullingEnabled;
        }
        return useOcclusionCulling;
    }

    private boolean isOpaqueFullCube(BlockPos origin) {
        Block block = this.world.getBlockState(origin).getBlock();
        return block.getOpacity() >= 255 && block.isFullCube();
    }

    private void resetRenderLists() {
        this.renderLists = SortedRenderLists.empty();

        for (ArrayDeque<RenderSection> list : this.rebuildLists.values()) {
            list.clear();
        }
    }

    public void onSectionAdded(int x, int y, int z) {
        long key = SectionPos.asLong(x, y, z);

        if (this.sectionByPosition.containsKey(key)) {
            return;
        }

        RenderRegion region = this.regions.createForChunk(x, y, z);

        RenderSection renderSection = new RenderSection(region, x, y, z);
        region.addSection(renderSection);

        this.sectionByPosition.put(key, renderSection);

        Chunk chunk = this.world.getChunk(x, z);
        ChunkSection section = null;
        if (y >= 0 && y <= 15) {
            section = chunk.getBlockStorage()[y];
        }

        if (section == null || section.isEmpty()) {
            this.updateSectionInfo(renderSection, BuiltSectionInfo.EMPTY);
        } else {
            renderSection.setPendingUpdate(ChunkUpdateType.INITIAL_BUILD);
        }

        this.connectNeighborNodes(renderSection);

        this.needsUpdate = true;
    }

    public void onSectionRemoved(int x, int y, int z) {
        RenderSection section = this.sectionByPosition.remove(SectionPos.asLong(x, y, z));

        if (section == null) {
            return;
        }

        RenderRegion region = section.getRegion();

        if (region != null) {
            region.removeSection(section);
        }

        this.disconnectNeighborNodes(section);
        this.updateSectionInfo(section, null);

        section.delete();

        this.needsUpdate = true;
    }

    // Re-used across per-layer render calls instead of allocating a fresh transform for each layer.
    private final CameraTransform cameraTransform = new CameraTransform(0.0, 0.0, 0.0);

    public void renderLayer(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z) {
        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        this.chunkRenderer.render(matrices, commandList, this.renderLists, pass, this.cameraTransform.set(x, y, z));

        commandList.flush();
    }

    // NOTE(1.8.9): vanilla Sodium's tickVisibleRenders (sprite animation tracking) was removed in
    // the port — see SpriteUtil — so there is nothing to do per frame anymore.

    public boolean isSectionVisible(int x, int y, int z) {
        RenderSection render = this.getRenderSection(x, y, z);

        if (render == null) {
            return false;
        }

        return render.getLastVisibleFrame() == this.lastUpdatedFrame;
    }

    public void updateChunks(boolean updateImmediately) {
        this.sectionCache.cleanup();
        this.regions.update();

        ChunkJobCollector blockingRebuilds = new ChunkJobCollector(Integer.MAX_VALUE, this.buildResultsConsumer);
        ChunkJobCollector deferredRebuilds = new ChunkJobCollector(this.builder.getSchedulingBudget(), this.buildResultsConsumer);

        this.submitRebuildTasks(blockingRebuilds, ChunkUpdateType.IMPORTANT_REBUILD);
        this.submitRebuildTasks(updateImmediately ? blockingRebuilds : deferredRebuilds, ChunkUpdateType.REBUILD);
        this.submitRebuildTasks(updateImmediately ? blockingRebuilds : deferredRebuilds, ChunkUpdateType.INITIAL_BUILD);

        blockingRebuilds.awaitCompletion(this.builder);
    }

    // Upper bound of finished sections uploaded to the GPU in one frame (deferred mode). A burst of
    // finished builds (joining, teleporting, fast flying) is spread over several frames instead of
    // one long frame; leftovers stay queued. Tune to taste.
    private static final int MAX_UPLOADS_PER_FRAME = 8;

    public void uploadChunks() {
        this.uploadChunks(false);
    }

    public void uploadChunks(boolean all) {
        ArrayList<ChunkBuildOutput> results = this.collectChunkBuildResults(all ? Integer.MAX_VALUE : MAX_UPLOADS_PER_FRAME);

        if (results.isEmpty()) {
            return;
        }

        this.processChunkBuildResults(results);

        for (ChunkBuildOutput result : results) {
            result.delete();
        }

        this.needsUpdate = true;
    }

    private void processChunkBuildResults(ArrayList<ChunkBuildOutput> results) {
        List<ChunkBuildOutput> filtered = filterChunkBuildResults(results);

        this.regions.uploadMeshes(RenderDevice.INSTANCE.createCommandList(), filtered);

        for (ChunkBuildOutput result : filtered) {
            this.updateSectionInfo(result.render, result.info);

            CancellationToken job = result.render.getBuildCancellationToken();

            if (job != null && result.buildTime >= result.render.getLastSubmittedFrame()) {
                result.render.setBuildCancellationToken(null);
            }

            result.render.setLastBuiltFrame(result.buildTime);
        }
    }

    private void updateSectionInfo(RenderSection render, BuiltSectionInfo info) {
        render.setInfo(info);

        if (info == null || ArrayUtils.isEmpty(info.globalBlockEntities)) {
            this.sectionsWithGlobalEntities.remove(render);
        } else {
            this.sectionsWithGlobalEntities.add(render);
        }
    }

    private static List<ChunkBuildOutput> filterChunkBuildResults(ArrayList<ChunkBuildOutput> outputs) {
        Reference2ReferenceLinkedOpenHashMap<RenderSection, ChunkBuildOutput> map = new Reference2ReferenceLinkedOpenHashMap<>();

        for (ChunkBuildOutput output : outputs) {
            if (output.render.isDisposed() || output.render.getLastBuiltFrame() > output.buildTime) {
                continue;
            }

            RenderSection render = output.render;
            ChunkBuildOutput previous = map.get(render);

            if (previous == null || previous.buildTime < output.buildTime) {
                map.put(render, output);
            }
        }

        return new ArrayList<>(map.values());
    }

    private ArrayList<ChunkBuildOutput> collectChunkBuildResults() {
        return this.collectChunkBuildResults(Integer.MAX_VALUE);
    }

    private ArrayList<ChunkBuildOutput> collectChunkBuildResults(int max) {
        ArrayList<ChunkBuildOutput> results = new ArrayList<>();
        ChunkJobResult<ChunkBuildOutput> result;

        while (results.size() < max && (result = this.buildResults.poll()) != null) {
            results.add(result.unwrap());
        }

        return results;
    }

    private int submitRebuildTasks(ChunkJobCollector collector, ChunkUpdateType type) {
        ArrayDeque<RenderSection> queue = this.rebuildLists.get(type);
        int submitted = 0;

        while (!queue.isEmpty() && collector.canOffer()) {
            RenderSection section = queue.remove();

            if (section.isDisposed()) {
                continue;
            }

            int frame = this.lastUpdatedFrame;
            ChunkBuilderMeshingTask task = this.createRebuildTask(section, frame);

            if (task != null) {
                submitted++;
                ChunkJobTyped<ChunkBuilderMeshingTask, ChunkBuildOutput> job =
                        this.builder.scheduleTask(task, type.isImportant(), collector::onJobFinished);
                collector.addSubmittedJob(job);
                section.setBuildCancellationToken(job);
            } else {
                ChunkJobResult<ChunkBuildOutput> result = ChunkJobResult.successfully(new ChunkBuildOutput(section, BuiltSectionInfo.EMPTY, Collections.emptyMap(), frame));
                this.buildResults.add(result);

                section.setBuildCancellationToken(null);
            }

            section.setLastSubmittedFrame(frame);
            section.setPendingUpdate(null);
        }

        return submitted;
    }

    public @Nullable ChunkBuilderMeshingTask createRebuildTask(RenderSection render, int frame) {
        ChunkRenderContext context = WorldSlice.prepare(this.world, render.getPosition(), this.sectionCache);

        if (context == null) {
            return null;
        }

        return new ChunkBuilderMeshingTask(render, context, frame);
    }

    public void markGraphDirty() {
        this.needsUpdate = true;
    }

    public boolean needsUpdate() {
        return this.needsUpdate;
    }

    public ChunkBuilder getBuilder() {
        return this.builder;
    }

    public void destroy() {
        this.builder.shutdown(); // stop all the workers, and cancel any tasks

        for (ChunkBuildOutput result : this.collectChunkBuildResults()) {
            result.delete(); // delete resources for any pending tasks (including those that were cancelled)
        }

        this.sectionsWithGlobalEntities.clear();
        this.resetRenderLists();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.regions.delete(commandList);
            this.chunkRenderer.delete(commandList);
        }
    }

    public int getTotalSections() {
        return this.sectionByPosition.size();
    }

    public int getVisibleChunkCount() {
        int sections = 0;
        Iterator<ChunkRenderList> iterator = this.renderLists.iterator();

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();
            sections += renderList.getSectionsWithGeometryCount();
        }

        return sections;
    }

    public void scheduleRebuild(int x, int y, int z, boolean important) {
        RenderAsserts.validateCurrentThread();

        this.sectionCache.invalidate(x, y, z);

        RenderSection section = this.sectionByPosition.get(SectionPos.asLong(x, y, z));

        if (section != null && section.isBuilt()) {
            ChunkUpdateType pendingUpdate;

            if (allowImportantRebuilds() && (important || this.shouldPrioritizeRebuild(section))) {
                pendingUpdate = ChunkUpdateType.IMPORTANT_REBUILD;
            } else {
                pendingUpdate = ChunkUpdateType.REBUILD;
            }

            if (ChunkUpdateType.canPromote(section.getPendingUpdate(), pendingUpdate)) {
                section.setPendingUpdate(pendingUpdate);

                this.needsUpdate = true;
            }
        }
    }

    private static final float NEARBY_REBUILD_DISTANCE = 256.0f; // (16.0f)^2, squared block distance

    private boolean shouldPrioritizeRebuild(RenderSection section) {
        return this.lastCameraPosition != null && section.getSquaredDistance(this.lastCameraPosition) < NEARBY_REBUILD_DISTANCE;
    }

    private static boolean allowImportantRebuilds() {
        return !SodiumClientMod.options().performance.alwaysDeferChunkUpdates;
    }

    private float getEffectiveRenderDistance() {
        // Upstream trims the graph search against the fog end value. 1.8.9's fixed-function fog
        // state is only guaranteed to hold the terrain fog while the layers render, so it is
        // captured by the chunk fog shader component (ChunkShaderFogState) and consumed here
        // one frame later. Sections whose entire bounding box lies beyond the fog end are
        // fully fog-colored and therefore never contribute visible geometry.
        if (ChunkFogState.isLinearFog()) {
            return Math.min(this.getRenderDistance(), ChunkFogState.getFogEnd());
        }

        // EXP fog (water/lava/underground/blindness) is now reproduced by the chunk shader, but
        // it is dense: keep the full search distance to stay conservative.
        return this.getRenderDistance();
    }

    private float getRenderDistance() {
        return this.renderDistance * 16.0f;
    }

    private void connectNeighborNodes(RenderSection render) {
        for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
            RenderSection adj = this.getRenderSection(render.getChunkX() + GraphDirection.x(direction),
                    render.getChunkY() + GraphDirection.y(direction),
                    render.getChunkZ() + GraphDirection.z(direction));

            if (adj != null) {
                adj.setAdjacentNode(GraphDirection.opposite(direction), render);
                render.setAdjacentNode(direction, adj);
            }
        }
    }

    private void disconnectNeighborNodes(RenderSection render) {
        for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
            RenderSection adj = render.getAdjacent(direction);

            if (adj != null) {
                adj.setAdjacentNode(GraphDirection.opposite(direction), null);
                render.setAdjacentNode(direction, null);
            }
        }
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.sectionByPosition.get(SectionPos.asLong(x, y, z));
    }

    public Collection<String> getDebugStrings() {
        List<String> list = new ArrayList<>();

        int count = 0;

        long deviceUsed = 0;
        long deviceAllocated = 0;

        for (RenderRegion region : this.regions.getLoadedRegions()) {
            RenderRegion.DeviceResources resources = region.getResources();

            if (resources == null) {
                continue;
            }

            GlBufferArena buffer = resources.getGeometryArena();

            deviceUsed += buffer.getDeviceUsedMemory();
            deviceAllocated += buffer.getDeviceAllocatedMemory();

            count++;
        }

        list.add(String.format("Geometry Pool: %d/%d MiB (%d buffers)", MathUtil.toMib(deviceUsed), MathUtil.toMib(deviceAllocated), count));
        list.add(String.format("Transfer Queue: %s", this.regions.getStagingBuffer().toString()));

        list.add(String.format("Chunk Builder: Permits=%02d | Busy=%02d | Total=%02d",
                this.builder.getScheduledJobCount(), this.builder.getBusyThreadCount(), this.builder.getTotalThreadCount())
        );

        list.add(String.format("Chunk Queues: U=%02d (P0=%03d | P1=%03d | P2=%03d)",
                this.buildResults.size(),
                this.rebuildLists.get(ChunkUpdateType.IMPORTANT_REBUILD).size(),
                this.rebuildLists.get(ChunkUpdateType.REBUILD).size(),
                this.rebuildLists.get(ChunkUpdateType.INITIAL_BUILD).size())
        );

        return list;
    }

    public @NotNull SortedRenderLists getRenderLists() {
        return this.renderLists;
    }

    public boolean isSectionBuilt(int x, int y, int z) {
        RenderSection section = this.getRenderSection(x, y, z);
        return section != null && section.isBuilt();
    }

    public void onChunkAdded(int x, int z) {
        // 1.8.9 port: a chunk can be dropped and re-sent by the server (view distance toggles,
        // respawn, chunk re-delivery). Evict any cached clones of this chunk — they may have been
        // taken while the chunk was (re)instantiated without block data. Without this, builds
        // would mesh AIR for up to MAX_CACHE_DURATION on freshly re-delivered chunks.
        for (int y = 0; y < 16; y++) {
            this.sectionCache.invalidate(x, y, z);
        }

        for (int y = 0; y < 16; y++) {
            this.onSectionAdded(x, y, z);
        }
    }

    public void onChunkRemoved(int x, int z) {
        for (int y = 0; y < 16; y++) {
            this.onSectionRemoved(x, y, z);
        }
    }

    public Collection<RenderSection> getSectionsWithGlobalEntities() {
        return ReferenceSets.unmodifiable(this.sectionsWithGlobalEntities);
    }
}

