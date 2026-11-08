package me.sodiumlegacy.mod.mixin.core.render.world;

import net.minecraft.client.renderer.GlStateManager;
import me.sodiumlegacy.mod.client.gl.device.RenderDevice;
import me.sodiumlegacy.mod.client.render.SodiumWorldRenderer;
import me.sodiumlegacy.mod.client.render.viewport.Viewport;
import me.sodiumlegacy.mod.client.util.FlawlessFrames;
import me.sodiumlegacy.mod.client.world.WorldRendererExtended;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockSkull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
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
 * <p>The vanilla target class is {@code RenderGlobal}. The seams used here are the actual
 * 1.8.9 entry points: {@code setupTerrain(Entity, double, ICamera, int, boolean)},
 * {@code renderBlockLayer(EnumWorldBlockLayer, double, int, Entity)},
 * {@code renderEntities(Entity, ICamera, float)}, {@code setWorldAndLoadRenderers(WorldClient)},
 * {@code loadRenderers()}, {@code markBlockForUpdate(BlockPos)},
 * {@code notifyLightSet(BlockPos)}, {@code markBlockRangeForRenderUpdate(...)} and
 * {@code getDebugInfoRenders()}.</p>
 *
 * <p>Block entities and entity rendering remain vanilla's; the block-entity fast path in
 * {@link SodiumWorldRenderer#renderBlockEntities(float)} is deliberately not hooked up here yet,
 * since vanilla 1.8.9 renders every section's block entities itself.</p>
 *
 * @author jellysquid
 * @reason We want to replace the 1.8.9 pipeline with sodium's modern built pipeline.
 */
@Mixin(RenderGlobal.class)
public abstract class WorldRendererMixin implements WorldRendererExtended {
    @Unique
    private SodiumWorldRenderer renderer;

    @Unique
    private int frame;

    // vanilla state consumed by our renderEntities overwrite
    @Shadow
    private Minecraft mc;

    @Shadow
    private WorldClient theWorld;

    @Shadow
    private RenderManager renderManager;

    @Shadow
    private int countEntitiesTotal;

    @Shadow
    private int countEntitiesRendered;

    @Shadow
    private int countEntitiesHidden;

    @Shadow
    private Framebuffer entityOutlineFramebuffer;

    @Shadow
    private ShaderGroup entityOutlineShader;

    @Shadow
    private Set<TileEntity> setTileEntities;

    @Shadow
    private Map<Integer, DestroyBlockProgress> damagedBlocks;

    @Shadow
    protected abstract boolean isRenderEntityOutlines();

    @Shadow
    protected abstract void preRenderDamagedBlocks();

    @Shadow
    protected abstract void postRenderDamagedBlocks();

    @Override
    public SodiumWorldRenderer sodium$getWorldRenderer() {
        return this.renderer;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(Minecraft mc, CallbackInfo ci) {
        this.renderer = new SodiumWorldRenderer(mc);
    }

    @Inject(method = "setWorldAndLoadRenderers", at = @At("RETURN"))
    private void onWorldChanged(@Nullable WorldClient world, CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.setWorld(world);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Inject(method = "loadRenderers", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.reload();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Inject(method = "setDisplayListEntitiesDirty", at = @At("RETURN"))
    private void onTerrainUpdateScheduled(CallbackInfo ci) {
        this.renderer.scheduleTerrainUpdate();
    }

    /**
     * @author JellySquid
     * @reason Vanilla's draw path for chunk layers is replaced wholesale; interpolated camera
     * position is computed the same way vanilla does (from the entity's inter-block pos).
     */
    @Overwrite
    public int renderBlockLayer(EnumWorldBlockLayer renderLayer, double tickDelta, int anaglyphFilter, Entity entity) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * tickDelta;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * tickDelta;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * tickDelta;

        RenderDevice.enterManagedCode();

        try {
            this.renderer.drawChunkLayer(renderLayer, x, y, z);
        } finally {
            RenderDevice.exitManagedCode();
        }

        // Vanilla's renderBlockLayer wraps the chunk drawing in enableLightmap()/disableLightmap().
        // Drawing the chunks here enables the lightmap (TextureUtil needs it for the sampler) but
        // nothing turned it off again, so it stayed enabled on texture unit 1 after the last layer
        // until the first-person hand's own disableLightmap(). Without the hand (F1 / F5) it leaked
        // into the following frame's fixed-function rendering, modulated by whatever light
        // coordinate was left over (the player's own, i.e. dark at night): the "black veil".
        this.mc.entityRenderer.disableLightmap();

        return 0;
    }

    /**
     * @author JellySquid
	 * @modified Scaffold
     */
    @Overwrite
    public void renderEntities(Entity entity, ICamera cameraView, float tickDelta) {
        if (this.countEntitiesTotal > 0) {
            this.countEntitiesTotal--;
            return;
        }

        double d = entity.prevPosX + (entity.posX - entity.prevPosX) * tickDelta;
        double e = entity.prevPosY + (entity.posY - entity.prevPosY) * tickDelta;
        double f = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * tickDelta;

        this.theWorld.theProfiler.startSection("prepare");
        TileEntityRendererDispatcher.instance.cacheActiveRenderInfo(
            this.theWorld, this.mc.getTextureManager(), this.mc.fontRendererObj, this.mc.getRenderViewEntity(), tickDelta);
        this.renderManager
            .cacheActiveRenderInfo(this.theWorld, this.mc.fontRendererObj, this.mc.getRenderViewEntity(), this.mc.pointedEntity, this.mc.gameSettings, tickDelta);
        this.countEntitiesRendered = 0;
        this.countEntitiesHidden = 0;

        Entity entity2 = this.mc.getRenderViewEntity();
        double g = entity2.lastTickPosX + (entity2.posX - entity2.lastTickPosX) * tickDelta;
        double h = entity2.lastTickPosY + (entity2.posY - entity2.lastTickPosY) * tickDelta;
        double i = entity2.lastTickPosZ + (entity2.posZ - entity2.lastTickPosZ) * tickDelta;
        TileEntityRendererDispatcher.staticPlayerX = g;
        TileEntityRendererDispatcher.staticPlayerY = h;
        TileEntityRendererDispatcher.staticPlayerZ = i;
        this.renderManager.setRenderPosition(g, h, i);
        this.mc.entityRenderer.enableLightmap();

        // "global" pass — kept vanilla: iterates World#weatherEffects (lightning bolts), no
        // frustum/culling checks. This must NOT iterate loadedEntityList: that list is drawn by the
        // main pass below, so doing it here too rendered every entity (player included) twice.
        this.theWorld.theProfiler.startSection("global");
        List<Entity> list = this.theWorld.getLoadedEntityList();
        this.countEntitiesRendered = list.size();

        for (int j = 0; j < this.theWorld.weatherEffects.size(); j++) {
            Entity entity3 = this.theWorld.weatherEffects.get(j);
            this.countEntitiesHidden++;
            if (entity3.isInRangeToRender3d(d, e, f)) {
                this.renderManager.renderEntitySimple(entity3, tickDelta);
            }
        }

        // spectator player outlines — kept vanilla
        if (this.isRenderEntityOutlines()) {
            GlStateManager.depthFunc(519);
            GlStateManager.disableFog();
            this.entityOutlineFramebuffer.framebufferClear();
            this.entityOutlineFramebuffer.bindFramebuffer(true);
            this.theWorld.theProfiler.startSection("entityOutlines");
            RenderHelper.disableStandardItemLighting();
            this.renderManager.setRenderOutlines(true);

            for (int jx = 0; jx < list.size(); jx++) {
                Entity entity3 = list.get(jx);
                boolean bl = this.mc.getRenderViewEntity() instanceof EntityPlayer && ((EntityPlayer)this.mc.getRenderViewEntity()).isPlayerSleeping();
                boolean bl2 = entity3.isInRangeToRender3d(d, e, f)
                    && (entity3.ignoreFrustumCheck || cameraView.isBoundingBoxInFrustum(entity3.getEntityBoundingBox()) || entity3.riddenByEntity == this.mc.thePlayer)
                    && entity3 instanceof EntityPlayer;
                if ((entity3 != this.mc.getRenderViewEntity() || this.mc.gameSettings.thirdPersonView != 0 || bl) && bl2) {
                    this.renderManager.renderEntitySimple(entity3, tickDelta);
                }
            }

            this.renderManager.setRenderOutlines(false);
            RenderHelper.enableStandardItemLighting();
            GlStateManager.depthMask(false);
            this.entityOutlineShader.loadShaderGroup(tickDelta);
            GlStateManager.enableLighting();
            GlStateManager.depthMask(true);
            this.mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.enableFog();
            GlStateManager.enableBlend();
            GlStateManager.enableColorMaterial();
            GlStateManager.depthFunc(515);
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();
        }

        // Main entity pass — replaces vanilla's viewFrustum iteration with sodium's visibility graph:
        // dispatcher.shouldRender runs the vanilla distance/frustum/dimension checks plus sodium's
        // entity culling (injected in EntityRendererMixin).
        this.theWorld.theProfiler.startSection("entities");

        for (int jx = 0; jx < list.size(); jx++) {
            Entity entity4 = list.get(jx);
            boolean bl3 = this.renderManager.shouldRender(entity4, cameraView, d, e, f) || entity4.riddenByEntity == this.mc.thePlayer;
            if (bl3) {
                boolean bl4 = this.mc.getRenderViewEntity() instanceof EntityPlayer ? ((EntityPlayer)this.mc.getRenderViewEntity()).isPlayerSleeping() : false;
                if (entity4 == this.mc.getRenderViewEntity() && this.mc.gameSettings.thirdPersonView == 0 && !bl4
                    || entity4.posY >= 0.0 && entity4.posY < 256.0 && !this.theWorld.isBlockLoaded(new BlockPos(entity4))) {
                    continue;
                }

                this.countEntitiesHidden++;
                this.renderManager.renderEntitySimple(entity4, tickDelta);
            } else if (entity4 instanceof EntityWitherSkull) {
                this.renderManager.renderWitherSkull(entity4, tickDelta);
            }
        }

        // Block entities — sources sodium's per-section caches instead of vanilla's (never-built)
        // viewFrustum chunk data, which would otherwise always be empty.
        this.theWorld.theProfiler.startSection("blockentities");
        RenderHelper.enableStandardItemLighting();

        this.renderer.renderBlockEntities(tickDelta);

        synchronized (this.setTileEntities) {
            for (TileEntity blockEntity2 : this.setTileEntities) {
                TileEntityRendererDispatcher.instance.renderTileEntity(blockEntity2, tickDelta, -1);
            }
        }

        this.preRenderDamagedBlocks();

        for (DestroyBlockProgress blockBreakingInfo : this.damagedBlocks.values()) {
            BlockPos blockPos = blockBreakingInfo.getPosition();
            TileEntity blockEntity3 = this.theWorld.getTileEntity(blockPos);
            if (blockEntity3 instanceof TileEntityChest) {
                TileEntityChest chestBlockEntity = (TileEntityChest)blockEntity3;
                if (chestBlockEntity.adjacentChestXNeg != null) {
                    blockPos = blockPos.offset(EnumFacing.WEST);
                    blockEntity3 = this.theWorld.getTileEntity(blockPos);
                } else if (chestBlockEntity.adjacentChestZNeg != null) {
                    blockPos = blockPos.offset(EnumFacing.NORTH);
                    blockEntity3 = this.theWorld.getTileEntity(blockPos);
                }
            }

            Block block = this.theWorld.getBlockState(blockPos).getBlock();
            if (blockEntity3 != null
                && (block instanceof BlockChest || block instanceof BlockEnderChest || block instanceof BlockSign || block instanceof BlockSkull)) {
                TileEntityRendererDispatcher.instance.renderTileEntity(blockEntity3, tickDelta, blockBreakingInfo.getPartialBlockDamage());
            }
        }

        this.postRenderDamagedBlocks();
        this.mc.entityRenderer.disableLightmap();
        this.theWorld.theProfiler.endSection();
    }

    /**
     * @author JellySquid
	 * @modified Scaffold
     * @reason Vanilla's builtin chunk graph (viewFrustum) is replaced by sodium's section graph;
     * the world-space {@link ICamera} of 1.8.9 is wrapped into a sodium viewport (see
     * {@link SodiumWorldRenderer#updateFrustum()}) so the visibility graph still frustum-culls.
     */
    @Overwrite
    public void setupTerrain(Entity entity, double tickDelta, ICamera cameraView, int frame, boolean spectator) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * tickDelta;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * tickDelta;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * tickDelta;

        // The vanilla camera position tracks the smooth (interpolated) entity position; the pitch
        // and yaw fields hold the current fixed values for this frame (exactly like vanilla's
        // frustum rebuild entry points use them).
        Viewport viewport = new Viewport(
                this.renderer.updateFrustum(),
                new Vector3d(x, y, z));

        boolean updateChunksImmediately = FlawlessFrames.isActive();

        RenderDevice.enterManagedCode();

        try {
            this.renderer.setupTerrain(
                    new net.minecraft.util.Vec3(x, y, z),
                    entity.rotationPitch,
                    entity.rotationYaw,
                    viewport,
                    this.frame++,
                    spectator,
                    updateChunksImmediately);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Overwrite
    public void markBlockForUpdate(BlockPos pos) {
        this.renderer.scheduleRebuildForBlockArea(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, false);
    }

    @Overwrite
    public void notifyLightSet(BlockPos pos) {
        this.renderer.scheduleRebuildForBlockArea(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, false);
    }

    @Overwrite
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.renderer.scheduleRebuildForBlockArea(x1 - 1, y1 - 1, z1 - 1, x2 + 1, y2 + 1, z2 + 1, false);
    }

    /**
     * @author JellySquid
     * @reason Replace the debug string with our own renderer's counters
     */
    @Overwrite
    public String getDebugInfoRenders() {
        return this.renderer.getChunksDebugString();
    }
}