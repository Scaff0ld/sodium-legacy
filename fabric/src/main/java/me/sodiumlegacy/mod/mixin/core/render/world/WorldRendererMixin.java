package me.sodiumlegacy.mod.mixin.core.render.world;

import com.mojang.blaze3d.platform.GlStateManager;
import me.sodiumlegacy.mod.client.gl.device.RenderDevice;
import me.sodiumlegacy.mod.client.render.SodiumWorldRenderer;
import me.sodiumlegacy.mod.client.render.viewport.Viewport;
import me.sodiumlegacy.mod.client.util.FlawlessFrames;
import me.sodiumlegacy.mod.client.world.WorldRendererExtended;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderEffect;
import net.minecraft.client.render.BlockBreakingInfo;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replaces vanilla's 1.8.9 chunk render pipeline with sodium's.
 *
 * <p>1.8.9 has a very different {@code WorldRenderer}: there is no per-frame camera
 * object, no BlockRenderLayer split of terrain, and no shader-driven vertex sort. The
 * seams used here are the actual 1.8.9 entry points, which were verified against the
 * decompiled sources:</p>
 *
 * <ul>
 *   <li>{@code setupTerrain(Entity, double, CameraView, int, boolean)} — replaced with sodium's
 *       graph traversal (vanilla's built-in chunk queues and lists are left blank since our
 *       overrides of the render entry points no longer consume them).</li>
 *   <li>{@code renderLayer(RenderLayer, double, int, Entity)} (the public overload, invoked once
 *       per terrain layer from {@code GameRenderer#renderWorld}) — replaced with sodium's
 *       {@link SodiumWorldRenderer#drawChunkLayer(RenderLayer, double, double, double)}.</li>
 *   <li>{@code setWorld(ClientWorld)} / {@code reload()} — sodium state is kept in sync.</li>
 *   <li>{@code onBlockUpdate(BlockPos)} / {@code onLightUpdate(BlockPos)} /
 *       {@code onRenderRegionUpdate(int, int, int, int, int, int)} / {@link BlockPos} vs
 *       {@code scheduleTerrainUpdate()} — forwarded to sodium's rebuild scheduler.</li>
 * </ul>
 *
 * <p>Block entities and entity rendering remain vanilla's; the block-entity fast path in
 * {@link SodiumWorldRenderer#renderBlockEntities(float)} is deliberately not hooked up here yet,
 * since vanilla 1.8.9 renders every section's block entities itself.</p>
 *
 * @author jellysquid
 * @reason We want to replace the 1.8.9 pipeline with sodium's modern built pipeline.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin implements WorldRendererExtended {
    @Unique
    private SodiumWorldRenderer renderer;

    @Unique
    private int frame;

    // Per-frame scratch objects re-used on the setupTerrain hot path (avoids fresh allocations
    // for the Viewport + camera Vector3d every single frame).
    @Unique
    private final Viewport viewport =
            new Viewport(null, new Vector3d());

    @Unique
    private final Vector3d viewportPosition = new Vector3d();

    // vanilla state consumed by our renderEntities overwrite
    @Shadow
    private MinecraftClient client;

    @Shadow
    private ClientWorld world;

    @Shadow
    private EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    private int totalEntityCount;

    @Shadow
    private int renderedEntityCount;

    @Shadow
    private int hiddenEntityCount;

    @Shadow
    private int blockEntityCount;

    @Shadow
    private Framebuffer entityOutlineFramebuffer;

    @Shadow
    private ShaderEffect entityOutlineShader;

    @Shadow
    private Set<BlockEntity> noCullingBlockEntities;

    @Shadow
    private Map<Integer, BlockBreakingInfo> blockBreakingInfos;

    @Shadow
    protected abstract boolean isEntityOutline();

    @Shadow
    protected abstract void preDrawBlockDamage();

    @Shadow
    protected abstract void postDrawBlockDamage();

    @Override
    public SodiumWorldRenderer sodium$getWorldRenderer() {
        return this.renderer;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(MinecraftClient client, CallbackInfo ci) {
        this.renderer = new SodiumWorldRenderer(client);
    }

    @Inject(method = "setWorld", at = @At("RETURN"))
    private void onWorldChanged(@Nullable ClientWorld world, CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.setWorld(world);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Inject(method = "reload()V", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.reload();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Inject(method = "scheduleTerrainUpdate", at = @At("RETURN"))
    private void onTerrainUpdateScheduled(CallbackInfo ci) {
        this.renderer.scheduleTerrainUpdate();
    }

    /**
     * @author JellySquid
     * @reason Vanilla's draw path for chunk layers is replaced wholesale; interpolated camera
     * position is computed the same way vanilla does (from the entity's inter-block pos).
     */
    @Overwrite
    public int renderLayer(RenderLayer renderLayer, double tickDelta, int anaglyphFilter, Entity entity) {
        double x = entity.prevTickX + (entity.x - entity.prevTickX) * tickDelta;
        double y = entity.prevTickY + (entity.y - entity.prevTickY) * tickDelta;
        double z = entity.prevTickZ + (entity.z - entity.prevTickZ) * tickDelta;

        RenderDevice.enterManagedCode();

        try {
            this.renderer.drawChunkLayer(renderLayer, x, y, z);
        } finally {
            RenderDevice.exitManagedCode();
        }

        // Vanilla's renderLayer wraps the chunk drawing in enableLightmap()/disableLightmap().
        // Drawing the chunks here enables the lightmap (TextureUtil needs it for the sampler) but
        // nothing turned it off again, so it stayed enabled on texture unit 1 after the last layer
        // until the first-person hand's own disableLightmap(). Without the hand (F1 / F5) it leaked
        // into the following frame's fixed-function rendering, modulated by whatever light
        // coordinate was left over (the player's own, i.e. dark at night): the "black veil".
        this.client.gameRenderer.disableLightmap();

        return 0;
    }

    /**
     * @author JellySquid
	 * @modified Scaffold
     * @reason Vanilla 1.8.9 renders regular world entities and block entities by iterating
     * {@code visibleChunks}, a list which is only ever populated by vanilla's own
     * {@code setupTerrain} graph traversal. Our overwrite of {@code setupTerrain} no longer fills
     * it (sodium maintains its own visibility graph), so those passes would render nothing. This
     * overwrite replicates vanilla's method but replaces the two {@code visibleChunks} passes:
     * <ul>
     *   <li>the entity pass iterates {@link ClientWorld#getLoadedEntities()} instead of the
     *       per-chunk entity lists of the (never-updated) vanilla chunk graph, keeping vanilla's
     *       {@code shouldRender} checks, the first-person camera skip and the unloaded-chunk skip.</li>
     *   <li>the block-entity pass renders the block entities tracked by sodium's visible
     *       sections via {@link SodiumWorldRenderer#renderBlockEntities(float)} instead of
     *       vanilla's (never-built) BuiltChunk caches.</li>
     * </ul>
     * Everything else (the "global" entity pass, the spectator-player outline pass, the
     * non-culled block entities and the block-breaking overlay) is byte-for-byte vanilla logic.
     */
    @Overwrite
    public void renderEntities(Entity entity, CameraView cameraView, float tickDelta) {
        if (this.totalEntityCount > 0) {
            this.totalEntityCount--;
            return;
        }

        double d = entity.prevX + (entity.x - entity.prevX) * tickDelta;
        double e = entity.prevY + (entity.y - entity.prevY) * tickDelta;
        double f = entity.prevZ + (entity.z - entity.prevZ) * tickDelta;

        this.world.profiler.push("prepare");
        BlockEntityRenderDispatcher.INSTANCE
            .updateCamera(this.world, this.client.getTextureManager(), this.client.textRenderer, this.client.getCameraEntity(), tickDelta);
        this.entityRenderDispatcher
            .updateCamera(this.world, this.client.textRenderer, this.client.getCameraEntity(), this.client.targetedEntity, this.client.options, tickDelta);
        this.renderedEntityCount = 0;
        this.hiddenEntityCount = 0;
        this.blockEntityCount = 0;

        Entity entity2 = this.client.getCameraEntity();
        double g = entity2.prevTickX + (entity2.x - entity2.prevTickX) * tickDelta;
        double h = entity2.prevTickY + (entity2.y - entity2.prevTickY) * tickDelta;
        double i = entity2.prevTickZ + (entity2.z - entity2.prevTickZ) * tickDelta;
        BlockEntityRenderDispatcher.CAMERA_X = g;
        BlockEntityRenderDispatcher.CAMERA_Y = h;
        BlockEntityRenderDispatcher.CAMERA_Z = i;
        this.entityRenderDispatcher.updateCamera(g, h, i);
        this.client.gameRenderer.enableLightmap();

        // "global" pass — kept vanilla: iterates World#entities, no frustum/culling checks
        this.world.profiler.swap("global");
        List<Entity> list = this.world.getLoadedEntities();
        this.renderedEntityCount = list.size();

        for (int j = 0; j < this.world.entities.size(); j++) {
            Entity entity3 = this.world.entities.get(j);
            this.hiddenEntityCount++;
            if (entity3.shouldRender(d, e, f)) {
                this.entityRenderDispatcher.renderEntity(entity3, tickDelta);
            }
        }

        // spectator player outlines — kept vanilla
        if (this.isEntityOutline()) {
            GlStateManager.depthFunc(519);
            GlStateManager.disableFog();
            this.entityOutlineFramebuffer.clear();
            this.entityOutlineFramebuffer.bind(false);
            this.world.profiler.swap("entityOutlines");
            DiffuseLighting.disable();
            this.entityRenderDispatcher.method_10206(true);

            for (int jx = 0; jx < list.size(); jx++) {
                Entity entity3 = list.get(jx);
                boolean bl = this.client.getCameraEntity() instanceof LivingEntity && ((LivingEntity)this.client.getCameraEntity()).isSleeping();
                boolean bl2 = entity3.shouldRender(d, e, f)
                    && (entity3.ignoreCameraFrustum || cameraView.isBoxInFrustum(entity3.getBoundingBox()) || entity3.rider == this.client.player)
                    && entity3 instanceof PlayerEntity;
                if ((entity3 != this.client.getCameraEntity() || this.client.options.perspective != 0 || bl) && bl2) {
                    this.entityRenderDispatcher.renderEntity(entity3, tickDelta);
                }
            }

            this.entityRenderDispatcher.method_10206(false);
            DiffuseLighting.enableNormally();
            GlStateManager.depthMask(false);
            this.entityOutlineShader.render(tickDelta);
            GlStateManager.enableLighting();
            GlStateManager.depthMask(true);
            this.client.getFramebuffer().bind(false);
            GlStateManager.enableFog();
            GlStateManager.enableBlend();
            GlStateManager.enableColorMaterial();
            GlStateManager.depthFunc(515);
            GlStateManager.enableDepthTest();
            GlStateManager.enableAlphaTest();
        }

        // Main entity pass — replaces vanilla's visibleChunks iteration with sodium's visibility
        // graph: dispatcher.shouldRender runs the vanilla distance/frustum/dimension checks plus
        // sodium's entity culling (injected in EntityRendererMixin).
        this.world.profiler.swap("entities");

        for (int jx = 0; jx < list.size(); jx++) {
            Entity entity4 = list.get(jx);
            boolean bl3 = this.entityRenderDispatcher.shouldRender(entity4, cameraView, d, e, f) || entity4.rider == this.client.player;
            if (bl3) {
                boolean bl4 = this.client.getCameraEntity() instanceof LivingEntity ? ((LivingEntity)this.client.getCameraEntity()).isSleeping() : false;
                if (entity4 == this.client.getCameraEntity() && this.client.options.perspective == 0 && !bl4
                    || entity4.y >= 0.0 && entity4.y < 256.0 && !this.world.blockExists(new BlockPos(entity4))) {
                    continue;
                }

                this.hiddenEntityCount++;
                this.entityRenderDispatcher.renderEntity(entity4, tickDelta);
            } else if (entity4 instanceof WitherSkullEntity) {
                this.client.getEntityRenderManager().method_10204(entity4, tickDelta);
            }
        }

        // Block entities — sources sodium's per-section caches instead of vanilla's (never-built)
        // BuiltChunkStorage chunk data, which would otherwise always be empty.
        this.world.profiler.swap("blockentities");
        DiffuseLighting.enableNormally();

        this.renderer.renderBlockEntities(tickDelta);

        synchronized (this.noCullingBlockEntities) {
            for (BlockEntity blockEntity2 : this.noCullingBlockEntities) {
                BlockEntityRenderDispatcher.INSTANCE.renderEntity(blockEntity2, tickDelta, -1);
            }
        }

        this.preDrawBlockDamage();

        for (BlockBreakingInfo blockBreakingInfo : this.blockBreakingInfos.values()) {
            BlockPos blockPos = blockBreakingInfo.getPos();
            BlockEntity blockEntity3 = this.world.getBlockEntity(blockPos);
            if (blockEntity3 instanceof ChestBlockEntity) {
                ChestBlockEntity chestBlockEntity = (ChestBlockEntity)blockEntity3;
                if (chestBlockEntity.neighborChestWest != null) {
                    blockPos = blockPos.offset(Direction.WEST);
                    blockEntity3 = this.world.getBlockEntity(blockPos);
                } else if (chestBlockEntity.neighborChestNorth != null) {
                    blockPos = blockPos.offset(Direction.NORTH);
                    blockEntity3 = this.world.getBlockEntity(blockPos);
                }
            }

            Block block = this.world.getBlockState(blockPos).getBlock();
            if (blockEntity3 != null
                && (block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof AbstractSignBlock || block instanceof SkullBlock)) {
                BlockEntityRenderDispatcher.INSTANCE.renderEntity(blockEntity3, tickDelta, blockBreakingInfo.getStage());
            }
        }

        this.postDrawBlockDamage();
        this.client.gameRenderer.disableLightmap();
        this.client.profiler.pop();
    }

    /**
     * @author JellySquid
	 * @modified Scaffold
     * @reason Vanilla's builtin chunk graph (visibleChunks + BuiltChunkStorage) is replaced by
     * sodium's section graph; the world-space {@link CameraView} of 1.8.9 is wrapped into a
     * sodium viewport (see {@link SodiumWorldRenderer#updateFrustum()}) so the visibility graph still frustum-culls.
     */
    @Overwrite
    public void setupTerrain(Entity entity, double tickDelta, CameraView cameraView, int frame, boolean spectator) {
        double x = entity.prevTickX + (entity.x - entity.prevTickX) * tickDelta;
        double y = entity.prevTickY + (entity.y - entity.prevTickY) * tickDelta;
        double z = entity.prevTickZ + (entity.z - entity.prevTickZ) * tickDelta;

        // The vanilla camera position tracks the smooth (interpolated) entity position; the pitch
        // and yaw fields hold the current fixed values for this frame (exactly like vanilla's
        // frustum rebuild entry points use them).
        // The viewport and its position vector are re-used every frame instead of being re-allocated
        // (this runs once per frame on the hot setup path).
        Viewport viewport = this.viewport.set(this.renderer.updateFrustum(),
                this.viewportPosition.set(x, y, z));

        boolean updateChunksImmediately = FlawlessFrames.isActive();

        RenderDevice.enterManagedCode();

        try {
            this.renderer.setupTerrain(
                    x,
                    y,
                    z,
                    entity.pitch,
                    entity.yaw,
                    viewport,
                    this.frame++,
                    spectator,
                    updateChunksImmediately);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Overwrite
    public void onBlockUpdate(BlockPos pos) {
        this.renderer.scheduleRebuildForBlockArea(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, false);
    }

    @Overwrite
    public void onLightUpdate(BlockPos pos) {
        this.renderer.scheduleRebuildForBlockArea(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, false);
    }

    @Overwrite
    public void onRenderRegionUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.renderer.scheduleRebuildForBlockArea(x1 - 1, y1 - 1, z1 - 1, x2 + 1, y2 + 1, z2 + 1, false);
    }

    /**
     * @author JellySquid
     * @reason Replace the debug string with our own renderer's counters
     */
    @Overwrite
    public String getChunksDebugString() {
        return this.renderer.getChunksDebugString();
    }
}
