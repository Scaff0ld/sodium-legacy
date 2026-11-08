package me.sodiumlegacy.mod.client.render.chunk.compile.pipeline;

import me.sodiumlegacy.mod.client.model.color.ColorProviderRegistry;
import me.sodiumlegacy.mod.client.model.light.LightPipelineProvider;
import me.sodiumlegacy.mod.client.model.light.data.ArrayLightDataCache;
import me.sodiumlegacy.mod.client.world.SlicedBlockView;
import me.sodiumlegacy.mod.client.world.WorldSlice;
import me.sodiumlegacy.mod.client.world.cloned.ChunkRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.multiplayer.WorldClient;

public class BlockRenderCache {
    private final ArrayLightDataCache lightDataCache;

    private final BlockRenderer blockRenderer;

    private final BlockModelShapes blockModelShapes;
    private final WorldSlice worldSlice;
    private final SlicedBlockView blockView;
    private final VanillaFluidBridge fluidBridge;
    private final BlockRendererDispatcher BlockRendererDispatcher;

    public BlockRenderCache(Minecraft client, WorldClient world) {
        this.worldSlice = new WorldSlice(world);
        this.lightDataCache = new ArrayLightDataCache(this.worldSlice);

        LightPipelineProvider lightPipelineProvider = new LightPipelineProvider(this.lightDataCache);

        // 1.8.9 has no vanilla BlockColors service; colors are resolved through sodium's registry
        // (populated by mixins later) and biome colors through the WorldSlice.
        ColorProviderRegistry colorRegistry = new ColorProviderRegistry();

        // 1.8.9 port: vanilla-exact rendering support. The adapter exposes the world slice through
        // vanilla's BlockView interface (used by FluidRenderer and Block#isSideInvisible), and the
        // bridge tessellates fluid blocks through vanilla's FluidRenderer.
        this.blockView = new SlicedBlockView(this.worldSlice, world);
        this.fluidBridge = new VanillaFluidBridge();
        this.BlockRendererDispatcher = client.getBlockRendererDispatcher();

        this.blockRenderer = new BlockRenderer(colorRegistry, lightPipelineProvider, this.blockView);

        // Model mapping: 1.8.9 routes per-state models through BlockModelShapes (obtained from the
        // client's BlockRendererDispatcher); there is no net.minecraft.client.render.block.BlockModels.
        this.blockModelShapes = client.getBlockRendererDispatcher().getBlockModelShapes();
    }

    /**
     * @return The 1.8.9 per-block-state model mapper (modern equivalent: BlockModels#getModel).
     */
    public BlockModelShapes getBlockShapes() {
        return this.blockModelShapes;
    }

    public BlockRenderer getBlockRenderer() {
        return this.blockRenderer;
    }

    public void init(ChunkRenderContext context) {
        this.lightDataCache.reset(context.getOrigin());
        this.worldSlice.copyData(context);
    }

    public WorldSlice getWorldSlice() {
        return this.worldSlice;
    }

    /**
     * 1.8.9 port: the slice wrapped as a vanilla {@code BlockView} for vanilla-exact renderer calls.
     */
    public SlicedBlockView getBlockView() {
        return this.blockView;
    }

    /**
     * 1.8.9 port: bridges fluid block tessellation from vanilla's {@code FluidRenderer}.
     */
    public VanillaFluidBridge getFluidBridge() {
        return this.fluidBridge;
    }

    /**
     * 1.8.9 port: the client's render manager, whose context-sensitive
     * {@code getModel(state, view, pos)} resolves the per-position baked model exactly as vanilla.
     */
    public BlockRendererDispatcher getBlockRenderManager() {
        return this.BlockRendererDispatcher;
    }

    public void cleanup() {
        this.worldSlice.reset();
    }
}
