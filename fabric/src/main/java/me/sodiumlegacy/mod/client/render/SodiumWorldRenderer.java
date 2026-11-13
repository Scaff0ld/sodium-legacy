package me.sodiumlegacy.mod.client.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.gl.device.CommandList;
import me.sodiumlegacy.mod.client.gl.device.RenderDevice;
import me.sodiumlegacy.mod.client.render.chunk.ChunkRenderMatrices;
import me.sodiumlegacy.mod.client.render.chunk.RenderSectionManager;
import me.sodiumlegacy.mod.client.render.chunk.lists.ChunkRenderList;
import me.sodiumlegacy.mod.client.render.chunk.lists.SortedRenderLists;
import me.sodiumlegacy.mod.client.render.chunk.map.ChunkTracker;
import me.sodiumlegacy.mod.client.render.chunk.map.ChunkTrackerHolder;
import me.sodiumlegacy.mod.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.sodiumlegacy.mod.client.render.viewport.Viewport;
import me.sodiumlegacy.mod.client.render.viewport.frustum.SimpleFrustum;
import org.joml.FrustumIntersection;
import me.sodiumlegacy.mod.client.util.NativeBuffer;
import me.sodiumlegacy.mod.client.util.SectionPos;
import me.sodiumlegacy.mod.client.world.WorldRendererExtended;
import org.joml.Matrix4f;
import me.sodiumlegacy.lwjgl3shim.opengl.GL11C;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.Iterator;

/**
 * Provides an extension to vanilla's {@link WorldRenderer}.
 */
public class SodiumWorldRenderer {
    private final MinecraftClient client;

    private ClientWorld world;
    private int renderDistance;

    private double lastCameraX, lastCameraY, lastCameraZ;
    private float lastCameraPitch, lastCameraYaw;

    private boolean useEntityCulling;

    private RenderSectionManager renderSectionManager;

    // Scratch state for uploading the fixed-function GL matrices to the chunk shaders.
    // Only touched on the render thread (drawChunkLayer / reload).
    private final FloatBuffer matrixScratch = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    private final Matrix4f modelViewMatrixScratch = new Matrix4f();
    private final Matrix4f projectionMatrixScratch = new Matrix4f();

    // Camera-relative frustum built from the GL projection * modelview matrices. Reused every frame
    // (no allocations), replaces the per-section `new Box` + vanilla CameraView test.
    private final Matrix4f viewProjectionScratch = new Matrix4f();
    private final FrustumIntersection frustumIntersection = new FrustumIntersection();
    private final SimpleFrustum frustum = new SimpleFrustum(this.frustumIntersection);

    /**
     * Rebuilds the culling frustum from the matrices currently on the GL stacks and returns it.
     * Must be called on the render thread after the camera transform has been applied (i.e. from
     * {@code setupTerrain}). The modelview holds only the camera rotation, so the resulting planes
     * are in camera-relative space, exactly what {@link Viewport} tests against.
     */
    public SimpleFrustum updateFrustum() {
        Matrix4f projection = this.readMatrix(GL11C.GL_PROJECTION_MATRIX, this.projectionMatrixScratch);
        Matrix4f modelView = this.readMatrix(GL11C.GL_MODELVIEW_MATRIX, this.modelViewMatrixScratch);

        this.frustumIntersection.set(projection.mul(modelView, this.viewProjectionScratch));

        return this.frustum;
    }

    /**
     * Reads a 4x4 column-major matrix from the current GL matrix stack into {@code target}
     * (column-major exactly as OpenGL stores it, so no re-ordering of the buffer is needed).
     */
    private Matrix4f readMatrix(int pname, Matrix4f target) {
        FloatBuffer buffer = this.matrixScratch;
        buffer.clear();
        buffer.position(0);
        buffer.limit(16);
        GL11C.glGetFloatv(pname, buffer);
        return target.set(buffer);
    }

    /**
     * @return The SodiumWorldRenderer based on the current dimension
     */
    public static SodiumWorldRenderer instance() {
        SodiumWorldRenderer instance = instanceNullable();

        if (instance == null) {
            throw new IllegalStateException("No renderer attached to active world");
        }

        return instance;
    }

    /**
     * @return The SodiumWorldRenderer based on the current dimension, or null if none is attached
     */
    public static SodiumWorldRenderer instanceNullable() {
        WorldRenderer worldRenderer = MinecraftClient.getInstance().worldRenderer;

        if (worldRenderer instanceof WorldRendererExtended) {
            return ((WorldRendererExtended) worldRenderer).sodium$getWorldRenderer();
        }

        return null;
    }

    public SodiumWorldRenderer(MinecraftClient client) {
        this.client = client;
    }

    public void setWorld(ClientWorld world) {
        // Check that the world is actually changing
        if (this.world == world) {
            return;
        }

        // If we have a world is already loaded, unload the renderer
        if (this.world != null) {
            this.unloadWorld();
        }

        // If we're loading a new world, load the renderer
        if (world != null) {
            this.loadWorld(world);
        }
    }

    private void loadWorld(ClientWorld world) {
        this.world = world;

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    private void unloadWorld() {
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }

        this.world = null;
    }

    /**
     * @return The number of chunk renders which are visible in the current camera's frustum
     */
    public int getVisibleChunkCount() {
        return this.renderSectionManager.getVisibleChunkCount();
    }

    /**
     * Notifies the chunk renderer that the graph scene has changed and should be re-computed.
     */
    public void scheduleTerrainUpdate() {
        // BUG: seems to be called before init
        if (this.renderSectionManager != null) {
            this.renderSectionManager.markGraphDirty();
        }
    }

    /**
     * @return True if no chunks are pending rebuilds
     */
    public boolean isTerrainRenderComplete() {
        return this.renderSectionManager.getBuilder().isBuildQueueEmpty();
    }

    /**
     * Called prior to any chunk rendering in order to update necessary state.
     *
     * <p>On 1.8.9 the vanilla {@code WorldRenderer} does not have a camera object; callers pass the
     * camera's world position, pitch and yaw directly (obtained via {@link Camera#getPosition()} /
     * entity rotation).</p>
     */
    public void setupTerrain(double cameraX,
                             double cameraY,
                             double cameraZ,
                             float pitch,
                             float yaw,
                             Viewport viewport,
                             int frame,
                             boolean spectator,
                             boolean updateChunksImmediately) {
        NativeBuffer.reclaim(false);

        // The vanilla fixed-function fog state is re-captured per frame during the layer draws
        // (see ChunkFogState); mark the previous capture as stale for the uniform cache here.
        me.sodiumlegacy.mod.client.render.chunk.shader.ChunkFogState.beginFrame();

        // The chunk-render matrices are read once per frame during the layer draws (see
        // drawChunkLayer); the matrix stacks are untouched between setupTerrain and the layers.
        this.matricesCached = false;

        this.processChunkEvents();

        me.sodiumlegacy.mod.client.dynamiclights.DynamicLights.update(this.client, this.world, this);

        this.useEntityCulling = SodiumClientMod.options().performance.useEntityCulling;

        if (this.client.options.viewDistance != this.renderDistance) {
            this.reload();
        }

        boolean dirty = cameraX != this.lastCameraX || cameraY != this.lastCameraY || cameraZ != this.lastCameraZ ||
                pitch != this.lastCameraPitch || yaw != this.lastCameraYaw;

        if (dirty) {
            this.renderSectionManager.markGraphDirty();
        }

        this.lastCameraX = cameraX;
        this.lastCameraY = cameraY;
        this.lastCameraZ = cameraZ;
        this.lastCameraPitch = pitch;
        this.lastCameraYaw = yaw;

        this.renderSectionManager.updateChunks(updateChunksImmediately);

        this.renderSectionManager.uploadChunks(updateChunksImmediately);

        if (this.renderSectionManager.needsUpdate()) {
            this.renderSectionManager.update(cameraX, cameraY, cameraZ, viewport, frame, spectator);
        }

        if (updateChunksImmediately) {
            this.renderSectionManager.uploadChunks(true);
        }

        // NOTE(1.8.9): sprite animation tracking (tickVisibleRenders upsteam) no longer exists.

        // TODO(1.8.9): entity render distance scaling (upstream Entity#setRenderDistanceMultiplier)
        // has no analog here.
    }

    private void processChunkEvents() {
        ChunkTracker tracker = ChunkTrackerHolder.get(this.world);
        tracker.forEachEvent(this.renderSectionManager::onChunkAdded, this.renderSectionManager::onChunkRemoved);
    }

    /**
     * Performs a render pass for the given {@link RenderLayer} and draws all visible chunks for it.
     *
     * <p>1.8.9 is a fixed-function GL client: the projection matrix is installed by
     * {@code GameRenderer#setupCamera} (a gluPerspective set right after the sky pass) and
     * the modelview holds only the camera rotations/orientation fudges (the player-position
     * translation is never part of the modelview — vanilla applies it per chunk with
     * {@code translate(chunkPos - viewPos)}, exactly the translation this pipeline encodes
     * in {@code u_RegionOffset}). Both matrices are therefore read verbatim from the GL
     * matrix stacks at this call; transforming region-relative vertices with them matches
     * vanilla's {@code M * T(chunk - camera) * vertex} composite bitwise.</p>
     */
    // Frame-scoped cache for the chunk-render matrices: setupTerrain runs exactly once per frame
    // before any layer is drawn, and vanilla does not touch the matrix stacks between layers, so
    // the first layer that needs the matrices reads them from GL and the rest of the frame reuses
    // them. This removes up to 6 redundant glGetFloatv calls per frame (2 per live layer).
    private boolean matricesCached;

    public void drawChunkLayer(RenderLayer renderLayer, double x, double y, double z) {
        // MC 1.8.9 splits terrain into four RenderLayers; sodium's pipeline has three passes.
        // The single CUTOUT pass already contains BOTH the mipped and the non-mipped cutout
        // materials (the per-material choice is made in the shader through the mip bias, see
        // chunk_material.glsl), so it must be drawn exactly once per frame.
        //
        // It used to be drawn once for CUTOUT_MIPPED and once more for CUTOUT. Vanilla switches
        // the atlas to a non-mipmapped filter right before the CUTOUT layer, so that second draw
        // re-rendered every cutout block (leaves, grass, ...) WITHOUT mipmaps on top of the first
        // one (same depth, GL_LEQUAL) -> distant foliage looked pixelated, and it doubled the cost.
        if (renderLayer == RenderLayer.SOLID) {
            this.renderSectionManager.renderLayer(matrices(), DefaultTerrainRenderPasses.SOLID, x, y, z);
        } else if (renderLayer == RenderLayer.CUTOUT_MIPPED) {
            this.renderSectionManager.renderLayer(matrices(), DefaultTerrainRenderPasses.CUTOUT, x, y, z);
        } else if (renderLayer == RenderLayer.CUTOUT) {
            // intentionally empty: already drawn by the CUTOUT_MIPPED call above (no matrix reads)
        } else if (renderLayer == RenderLayer.TRANSLUCENT) {
            this.renderSectionManager.renderLayer(matrices(), DefaultTerrainRenderPasses.TRANSLUCENT, x, y, z);
        }
    }

    /**
     * Returns the chunk render matrices, reading them from the GL matrix stacks on the first call
     * of the frame and reusing the scratch matrices afterwards.
     */
    private ChunkRenderMatrices matrices() {
        if (!this.matricesCached) {
            readMatrix(GL11C.GL_PROJECTION_MATRIX, this.projectionMatrixScratch);
            readMatrix(GL11C.GL_MODELVIEW_MATRIX, this.modelViewMatrixScratch);
            this.matricesCached = true;
        }

        return new ChunkRenderMatrices(this.projectionMatrixScratch, this.modelViewMatrixScratch);
    }

    public void reload() {
        if (this.world == null) {
            return;
        }

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    private void initRenderer(CommandList commandList) {
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }

        this.renderDistance = this.client.options.viewDistance;

        this.renderSectionManager = new RenderSectionManager(this.world, this.renderDistance, commandList);

        ChunkTracker tracker = ChunkTrackerHolder.get(this.world);
        ChunkTracker.forEachChunk(tracker.getReadyChunks(), this.renderSectionManager::onChunkAdded);
    }

    /**
     * Renders block entities which are attached to the currently visible chunk sections (their
     * culling volumes intersect a visible section) as well as "global" block entities.
     */
    public void renderBlockEntities(float tickDelta) {
        BlockEntityRenderDispatcher dispatcher = BlockEntityRenderDispatcher.INSTANCE;

        SortedRenderLists renderLists = this.renderSectionManager.getRenderLists();
        Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();

        while (renderListIterator.hasNext()) {
            ChunkRenderList renderList = renderListIterator.next();

            me.sodiumlegacy.mod.client.render.chunk.region.RenderRegion renderRegion = renderList.getRegion();

            me.sodiumlegacy.mod.client.util.iterator.ByteIterator renderSectionIterator = renderList.sectionsWithEntitiesIterator();

            if (renderSectionIterator == null) {
                continue;
            }

            while (renderSectionIterator.hasNext()) {
                int renderSectionId = renderSectionIterator.nextByteAsInt();
                me.sodiumlegacy.mod.client.render.chunk.RenderSection renderSection = renderRegion.getSection(renderSectionId);

                BlockEntity[] blockEntities = renderSection.getCulledBlockEntities();

                if (blockEntities == null) {
                    continue;
                }

                for (BlockEntity blockEntity : blockEntities) {
                    dispatcher.renderEntity(blockEntity, tickDelta, -1);
                }
            }
        }

        for (me.sodiumlegacy.mod.client.render.chunk.RenderSection renderSection : this.renderSectionManager.getSectionsWithGlobalEntities()) {
            BlockEntity[] blockEntities = renderSection.getGlobalBlockEntities();

            if (blockEntities == null) {
                continue;
            }

            for (BlockEntity blockEntity : blockEntities) {
                dispatcher.renderEntity(blockEntity, tickDelta, -1);
            }
        }
    }

    // the volume of a section multiplied by the number of sections to be checked at most
    private static final double MAX_ENTITY_CHECK_VOLUME = 16 * 16 * 16 * 15;

    /**
     * Returns whether or not the entity intersects with any visible chunks in the graph.
     * @return True if the entity is visible, otherwise false
     */
    public boolean isEntityVisible(Entity entity) {
        if (!this.useEntityCulling) {
            return true;
        }

        // Ensure entities with outlines or nametags are always visible
        // TODO(1.8.9): vanilla has an outline shader; hook `[worldRenderer].isEntityGlowing` once the
        // mixin wiring exists.
        if (entity.shouldRenderName()) {
            return true;
        }

        // NOTE(1.8.9): Entity#getBox is not the mapped bounding-box getter (it returns null in
        // this mapping set); the real one is Entity#getBoundingBox. Guard against null either
        // way, since freshly-spawned entities may not have a box yet.
        Box box = entity.getBoundingBox();

        if (box == null) {
            return true;
        }

        // bail on very large entities to avoid checking many sections
        double entityVolume = (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
        if (entityVolume > MAX_ENTITY_CHECK_VOLUME) {
            // TODO: do a frustum check instead, even large entities aren't visible if they're outside the frustum
            return true;
        }

        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        // Boxes outside the valid world height (0..255 on 1.8.9) will never map to a rendered chunk
        // Always render these boxes or they'll be culled incorrectly!
        if (y2 < 0.5D || y1 > 255.5D) {
            return true;
        }

        int minX = SectionPos.getSectionCoord((int) Math.floor(x1 - 0.5D));
        int minY = SectionPos.getSectionCoord((int) Math.floor(y1 - 0.5D));
        int minZ = SectionPos.getSectionCoord((int) Math.floor(z1 - 0.5D));

        int maxX = SectionPos.getSectionCoord((int) Math.floor(x2 + 0.5D));
        int maxY = SectionPos.getSectionCoord((int) Math.floor(y2 + 0.5D));
        int maxZ = SectionPos.getSectionCoord((int) Math.floor(z2 + 0.5D));

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (this.renderSectionManager.isSectionVisible(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public String getChunksDebugString() {
        // C: visible/total D: distance
        // TODO: add dirty and queued counts
        return String.format("C: %d/%d D: %d", this.renderSectionManager.getVisibleChunkCount(), this.renderSectionManager.getTotalSections(), this.renderDistance);
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified block region.
     */
    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        this.scheduleRebuildForChunks(minX >> 4, minY >> 4, minZ >> 4, maxX >> 4, maxY >> 4, maxZ >> 4, important);
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified chunk region.
     */
    public void scheduleRebuildForChunks(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkY = minY; chunkY <= maxY; chunkY++) {
                for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                    this.scheduleRebuildForChunk(chunkX, chunkY, chunkZ, important);
                }
            }
        }
    }

    /**
     * Schedules a chunk rebuild for the render belonging to the given chunk section position.
     */
    public void scheduleRebuildForChunk(int x, int y, int z, boolean important) {
        this.renderSectionManager.scheduleRebuild(x, y, z, important);
    }

    public Collection<String> getDebugStrings() {
        return this.renderSectionManager.getDebugStrings();
    }

    public boolean isSectionReady(int x, int y, int z) {
        return this.renderSectionManager.isSectionBuilt(x, y, z);
    }
}

