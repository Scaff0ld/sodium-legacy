package me.sodiumlegacy.mod.client.render.chunk.compile.tasks;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.sodiumlegacy.mod.client.render.chunk.RenderSection;
import me.sodiumlegacy.mod.client.render.chunk.compile.ChunkBuildBuffers;
import me.sodiumlegacy.mod.client.render.chunk.compile.ChunkBuildContext;
import me.sodiumlegacy.mod.client.render.chunk.compile.ChunkBuildOutput;
import me.sodiumlegacy.mod.client.render.chunk.compile.pipeline.BlockRenderCache;
import me.sodiumlegacy.mod.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.sodiumlegacy.mod.client.render.chunk.data.BuiltSectionInfo;
import me.sodiumlegacy.mod.client.render.chunk.data.BuiltSectionMeshParts;
import me.sodiumlegacy.mod.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.sodiumlegacy.mod.client.render.chunk.terrain.TerrainRenderPass;
import me.sodiumlegacy.mod.client.util.task.CancellationToken;
import me.sodiumlegacy.mod.client.world.WorldSlice;
import me.sodiumlegacy.mod.client.world.cloned.ChunkRenderContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.ReportedException;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.BlockPos;

import java.util.Map;
/**
 * Rebuilds all the meshes of a chunk for each given render pass with non-occluded blocks. The result is then uploaded
 * to graphics memory on the main thread.
 *
 * This task takes a slice of the world from the thread it is created on. Since these slices require rather large
 * array allocations, they are pooled to ensure that the garbage collector doesn't become overloaded.
 */
public class ChunkBuilderMeshingTask extends ChunkBuilderTask<ChunkBuildOutput> {
    private final RenderSection render;
    private final ChunkRenderContext renderContext;

    private final int buildTime;

    public ChunkBuilderMeshingTask(RenderSection render, ChunkRenderContext renderContext, int time) {
        this.render = render;
        this.renderContext = renderContext;
        this.buildTime = time;
    }

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext buildContext, CancellationToken cancellationToken) {
        BuiltSectionInfo.Builder renderData = new BuiltSectionInfo.Builder();
        VisGraph occluder = new VisGraph();

        ChunkBuildBuffers buffers = buildContext.buffers;
        buffers.init(renderData, this.render.getSectionIndex());

        BlockRenderCache cache = buildContext.cache;
        cache.init(this.renderContext);

        WorldSlice slice = cache.getWorldSlice();

        int minX = this.render.getOriginX();
        int minY = this.render.getOriginY();
        int minZ = this.render.getOriginZ();

        int maxX = minX + 16;
        int maxY = minY + 16;
        int maxZ = minZ + 16;

        // Initialise with minX/minY/minZ so initial getBlockState crash context is correct
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(minX, minY, minZ);
        BlockPos.MutableBlockPos modelOffset = new BlockPos.MutableBlockPos();

        BlockRenderContext context = new BlockRenderContext(slice);
        try {
            for (int y = minY; y < maxY; y++) {
                if (cancellationToken.isCancelled()) {
                    return null;
                }

                for (int z = minZ; z < maxZ; z++) {
                    for (int x = minX; x < maxX; x++) {
                        IBlockState state = slice.getBlockState(x, y, z);

                        if (this.isBlockRenderedAsAir(state)) {
                            continue;
                        }

                        blockPos.set(x, y, z);
                        modelOffset.set(x & 15, y & 15, z & 15);

                        // 1.8.9 port: vanilla's mesher dispatches on Block#getRenderType():
                        //  3 = baked model terrain (context-sensitive model lookup),
                        //  1 = tessellated by vanilla's FluidRenderer,
                        //  2 = rendered only through its block entity (chest, ender chest...),
                        // -1 = never tessellated into chunk terrain (BlockWithEntity subclasses).
                        // Blocks missing from the state->model map (fluids, chests) previously
                        // resolved to the "missing texture" cube here.
                        int blockType = state.getBlock().getRenderType();

                        if (blockType == 1) {
                            cache.getFluidBridge().renderFluid(
                                    state, blockPos, cache.getBlockView(), buffers, minX, minY, minZ);
                        } else if (blockType != -1 && blockType != 2) {
                            // 1.8.9 vanilla-exact: context-sensitive model resolution through
                            // BlockRenderManager#getModel (applies block.getBlockState(...)).
                            IBakedModel model = cache.getBlockRenderManager()
                                     .getModelFromBlockState(state, cache.getBlockView(), blockPos);

                            context.update(blockPos, modelOffset, state, model, 0L);
                            cache.getBlockRenderer()
                                .renderModel(context, buffers);
                        }

                        if (state.getBlock().hasTileEntity()) {
                            TileEntity entity = slice.getBlockEntity(blockPos);

                            if (entity != null) {
                                TileEntitySpecialRenderer<TileEntity> renderer = TileEntityRendererDispatcher.instance.getSpecialRenderer(entity);

                                if (renderer != null) {
                                    renderData.addBlockEntity(entity, !renderer.forceTileEntityRender());
                                }
                            }
                        }

                        if (this.isOpaqueFullCube(state)) {
                            occluder.func_178606_a(blockPos);
                        }
                    }
                }
            }
        } catch (ReportedException ex) {
            // Propagate existing crashes (add context)
            throw fillCrashInfo(ex.getCrashReport(), slice, blockPos);
        } catch (Exception ex) {
            // Create a new crash report for other exceptions (e.g. thrown in getQuads)
            throw fillCrashInfo(CrashReport.makeCrashReport(ex, "Encountered exception while building chunk meshes"), slice, blockPos);
        }

        Map<TerrainRenderPass, BuiltSectionMeshParts> meshes = new Reference2ReferenceOpenHashMap<>();

        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            BuiltSectionMeshParts mesh = buffers.createMesh(pass);

            if (mesh != null) {
                meshes.put(pass, mesh);
                renderData.addRenderPass(pass);
            }
        }

        renderData.setOcclusionData(occluder.computeVisibility());

        return new ChunkBuildOutput(this.render, renderData.build(), meshes, this.buildTime);
    }

    private static boolean isBlockRenderedAsAir(IBlockState state) {
        return state.getBlock() == Blocks.air && !state.getBlock().hasTileEntity();
    }

    private static boolean isOpaqueFullCube(IBlockState state) {
        return state.getBlock().getLightOpacity() >= 255 && state.getBlock().isFullCube();
    }

    private ReportedException fillCrashInfo(CrashReport report, WorldSlice slice, BlockPos pos) {
        CrashReportCategory crashReportCategory = report.makeCategoryDepth("Block being rendered", 1);

        IBlockState state = null;
        try {
            state = slice.getBlockState(pos);
        } catch (Exception ignored) {}
        crashReportCategory.addBlockInfo(crashReportCategory, pos, state);

        crashReportCategory.addCrashSection("Chunk section", String.valueOf(this.render));
        if (this.renderContext != null) {
            crashReportCategory.addCrashSection("Render context volume", String.valueOf(this.renderContext.getVolume()));
        }

        return new ReportedException(report);
    }
}

