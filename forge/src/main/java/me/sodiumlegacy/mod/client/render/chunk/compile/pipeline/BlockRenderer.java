package me.sodiumlegacy.mod.client.render.chunk.compile.pipeline;

import me.sodiumlegacy.mod.client.ctm.CtmCompat;
import me.sodiumlegacy.mod.client.ctm.CtmManager;
import me.sodiumlegacy.mod.client.model.color.ColorProvider;
import me.sodiumlegacy.mod.client.model.color.ColorProviderRegistry;
import me.sodiumlegacy.mod.client.model.light.LightMode;
import me.sodiumlegacy.mod.client.model.light.LightPipeline;
import me.sodiumlegacy.mod.client.model.light.LightPipelineProvider;
import me.sodiumlegacy.mod.client.model.light.data.QuadLightData;
import me.sodiumlegacy.mod.client.model.quad.BakedQuadView;
import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFacing;
import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadOrientation;
import me.sodiumlegacy.mod.client.render.chunk.compile.ChunkBuildBuffers;
import me.sodiumlegacy.mod.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.sodiumlegacy.mod.client.render.chunk.terrain.material.DefaultMaterials;
import me.sodiumlegacy.mod.client.render.chunk.terrain.material.Material;
import me.sodiumlegacy.mod.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import me.sodiumlegacy.mod.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.sodiumlegacy.mod.client.util.DirectionUtil;
import me.sodiumlegacy.mod.api.util.ColorABGR;
import me.sodiumlegacy.mod.client.SodiumClientMod;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockPane;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockPos;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class BlockRenderer {
    // The per-block PRNG seed is not consumed by 1.8.9's IBakedModel (getQuads() takes no random);
    // it is kept on the context for later behavior refinement.

    private final ColorProviderRegistry colorProviderRegistry;

    // 1.8.9 port: vanilla-exact face gating renders through an IBlockAccess (world slice adapter).
    private final IBlockAccess blockAccess;

    private final QuadLightData quadLightData = new QuadLightData();

    private final LightPipelineProvider lighters;

    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();

    private final boolean useAmbientOcclusion;

    // Quality option: connected-translucent.
    private final boolean cullIdenticalTranslucent;

    // Quality option "Leaves": true = fancy (transparent, cutout), false = fast (opaque, solid).
    // Resolved once per renderer; changing the option reloads the renderer.
    private final boolean fancyLeaves;

    private final int[] quadColors = new int[4];

    // Reused across calls (the build context is thread-local) to avoid allocating a BlockPos per face
    private final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

    private static final Vec3 NO_OFFSET = new Vec3(0.0D, 0.0D, 0.0D);

    public BlockRenderer(ColorProviderRegistry colorRegistry, LightPipelineProvider lighters, IBlockAccess blockAccess) {
        this.colorProviderRegistry = colorRegistry;
        this.lighters = lighters;
        this.blockAccess = blockAccess;

        this.useAmbientOcclusion = Minecraft.isAmbientOcclusionEnabled();
        this.cullIdenticalTranslucent = SodiumClientMod.options().quality.connectedTranslucentCulling;
        this.fancyLeaves = SodiumClientMod.options().quality.leavesQuality
                .isFancy(Minecraft.getMinecraft().gameSettings.fancyGraphics);
    }

    public void renderModel(BlockRenderContext ctx, ChunkBuildBuffers buffers) {
        if (ctx.model() == null) {
            // Unmapped state (would be vanilla's missing model); nothing safe to render without
            // crossing into vanilla meshing, so treat like 1.8.9's type -1 skip.
            return;
        }

        Material material = this.getMaterial(ctx.state());
        ChunkModelBuilder meshBuilder = buffers.get(material);

        ColorProvider<IBlockState> colorizer = this.colorProviderRegistry.getColorProvider(ctx.state().getBlock());

        LightPipeline lighter = this.lighters.getLighter(this.getLightingMode(ctx.state(), ctx.model()));
        // 1.8.9 has no per-state model offset (that API arrived with 1.10+); the render offset is
        // always zero here.
        Vec3 renderOffset = NO_OFFSET;

        // 1.8.9 port: vanilla-exact replication of BlockModelRenderer.render: the block's dynamic
        // bounding box is materialized first (isSideInvisible's "partial box" fast-skips read from
        // it), then each directional quad list is gated by block.isSideInvisible(...) — which
        // returns TRUE when the face must be drawn (and unconditionally draws the general list).
        ctx.state().getBlock().setBlockBoundsBasedOnState(this.blockAccess, ctx.pos());

        for (EnumFacing face : DirectionUtil.ALL_DIRECTIONS) {
            List<BakedQuad> quads = ctx.model().getFaceQuads(face);

            if (!quads.isEmpty() && this.isFaceVisible(ctx, face)) {
                this.renderQuadList(ctx, material, lighter, colorizer, renderOffset, meshBuilder, quads, face);
            }
        }

        List<BakedQuad> general = ctx.model().getGeneralQuads();

        if (!general.isEmpty()) {
            this.renderQuadList(ctx, material, lighter, colorizer, renderOffset, meshBuilder, general, null);
        }
    }

    /**
     * 1.8.9 port: neighbor visibility gate.
     *
     * <p>Vanilla's decision (BlockModelRenderer.render → block.isSideInvisible) is honored, but
     * the neighbor side of the test is replaced by the opaque-cube rule: a face that is flush with
     * the block's full extent against any opaque full-cube neighbor is never needed — vanilla's
     * transparency heuristic would otherwise keep interior faces alive (perf bug fix: too many
     * quads between adjacent opaque blocks). Faces that are not flush (partial bounding boxes:
     * doors, slabs, fences...) keep vanilla's behavior so their geometry is never culled.</p>
     */
    private boolean isFaceVisible(BlockRenderContext ctx, EnumFacing face) {
        BlockPos pos = ctx.pos();
        BlockPos offset = this.neighborPos;
        this.neighborPos.set(pos.getX() + face.getFrontOffsetX(), pos.getY() + face.getFrontOffsetY(), pos.getZ() + face.getFrontOffsetZ());
        Block neighborBlock = this.blockAccess.getBlockState(offset).getBlock();

        if (isOpaqueFullCube(neighborBlock)) {
            Block selfBlock = ctx.state().getBlock();

            // Only faces flush with the block's full extent can be culled; partial geometry
            // (doors/slabs/plants...) always renders. Mirrors vanilla's own isSideInvisible
            // partial-box fast-skips (which are honoured for the non-culling branch below).
            switch (face) {
                case DOWN: if (selfBlock.getBlockBoundsMinY() > 0.0D) return true; break;
                case UP:   if (selfBlock.getBlockBoundsMaxY() < 1.0D) return true; break;
                case NORTH: if (selfBlock.getBlockBoundsMinZ() > 0.0D) return true; break;
                case SOUTH: if (selfBlock.getBlockBoundsMaxZ() < 1.0D) return true; break;
                case WEST: if (selfBlock.getBlockBoundsMinX() > 0.0D) return true; break;
                case EAST: if (selfBlock.getBlockBoundsMaxX() < 1.0D) return true; break;
            }

            // Opaque full-cube neighbor + flush face: cull unconditionally, regardless of the
            // two blocks' identities (previously vanilla's transparency heuristic kept faces
            // between different opaque block types).
            return false;
        }

        // "Leaves" option: opaque (fast) leaves never show faces against other leaves. Fancy leaves
        // ignore vanilla's graphics-dependent same-block rule (it would hide interior faces
        // whenever the global graphics setting is Fast, whatever this option says).
        // Perf: with "Hide Inner Faces" enabled, fancy leaves cull the interior face against ANY
        // adjacent leaf block (oak next to birch, oak with/without decay state...). What can be
        // seen through the texture gaps are the outer faces of the neighboring leaf, not the
        // interior faces, so hiding them roughly halves the leaf-leaf quad count in dense canopies
        // at no visible cost. With the option off, only identical-type neighbors stay culled.
        if (ctx.state().getBlock() instanceof BlockLeaves) {
            if (neighborBlock instanceof BlockLeaves && (!this.fancyLeaves ||
                    this.cullIdenticalTranslucent || this.isIdenticalTranslucentNeighbor(ctx, offset))) {
                return false;
            }

            if (this.fancyLeaves) {
                return true;
            }
        }

        // Identical translucent neighbor (glass next to glass): the shared interior face
        // is invisible from both sides, so skip it ("connected textures" phase 1).
        if (this.isIdenticalTranslucentNeighbor(ctx, offset)) {
            return false;
        }

        // Fancy leaves are cutout (see-through) whatever the global graphics flag says, but vanilla's
        // gate below asks the neighbor's isOpaqueCube(), and a leaf block answers that from the vanilla
        // graphics flag (opaque when vanilla graphics = Fast). Without this the face of the block
        // under/next to a leaf is culled and a hole shows through the leaves.
        if (this.fancyLeaves && neighborBlock instanceof BlockLeaves) {
            return true;
        }

        // Not culled by the opaque rule: fall back to vanilla's own gate (true = draw — the name
        // is inverted) so per-block overrides (fluids, panes, carpets...) keep their behavior.
        return ctx.state().getBlock().shouldSideBeRendered(this.blockAccess, offset, face);
    }

    /**
     * The interior face between two adjacent <b>identical</b> translucent full-cube blocks 
	 * is never visible from either side,  so both copies can be skipped. This turns a wall 
	 * of glass / stained glass / ice  / leaves into one seamless body (no inner seams).
     *
     * <p>Only applies when the neighbor has the exact same {@link IBlockState} (reference
     * equality — states are interned) and the block's mesh fills its full 1x1x1 extent
     * (checked against the contextual bounding box, since {@code isFullCube()} is an
     * opacity check that returns false for glass and leaves). Partial geometry (panes,
     * doors, fences, slabs...) keeps vanilla's behavior so real geometry is never
     * culled.</p>
     */
    private boolean isIdenticalTranslucentNeighbor(BlockRenderContext ctx, BlockPos offset) {
        if (!this.cullIdenticalTranslucent) {
            return false;
        }

        Block selfBlock = ctx.state().getBlock();

        if (selfBlock.isOpaqueCube() && !(selfBlock instanceof BlockLeaves)) {
            return false;
        }

        // Full-extent mesh test (the bounding box was materialized by setBoundingBox in
        // renderModel before the face loop). Faces that are flush with all 6 planes can
        // share their interior with an identical full-cube neighbor.
        boolean fullExtent = selfBlock.getBlockBoundsMinX() <= 0.0D && selfBlock.getBlockBoundsMaxX() >= 1.0D
                && selfBlock.getBlockBoundsMinY() <= 0.0D && selfBlock.getBlockBoundsMaxY() >= 1.0D
                && selfBlock.getBlockBoundsMinZ() <= 0.0D && selfBlock.getBlockBoundsMaxZ() >= 1.0D;

        if (!fullExtent) {
            return false;
        }

        IBlockState neighbor = this.blockAccess.getBlockState(offset);

        if (neighbor == ctx.state()) {
            return true;
        }

        // Leaves carry decay state props (CHECK_DECAY / DECAYABLE) that vanilla toggles on
        // block updates, so two adjacent oak-leaf blocks can have different IBlockState
        // instances and would keep their interior faces. OptiFine-class behavior culls
        // interior leaf faces by block identity instead.
        return selfBlock instanceof BlockLeaves && neighbor.getBlock() == selfBlock;
    }

    /**
     * The bounding box was materialized by setBoundingBox before the face loop, so this tells
     * full cubes (glass, leaves) apart from partial geometry (panes, slabs...).
     */
    private boolean isFullExtent(Block block) {
        return block.getBlockBoundsMinX() <= 0.0D && block.getBlockBoundsMaxX() >= 1.0D
                && block.getBlockBoundsMinY() <= 0.0D && block.getBlockBoundsMaxY() >= 1.0D
                && block.getBlockBoundsMinZ() <= 0.0D && block.getBlockBoundsMaxZ() >= 1.0D;
    }

    /**
     * Per-quad version of the "Hide Inner Faces" rule. A quad is an invisible interior face when it
     * lies exactly on the block boundary, faces a neighbor of the same kind, and that neighbor
     * covers it:
     * <ul>
     *   <li>leaves and panes/bars: any neighbor of the same block (their {@link IBlockState}s differ by
     *       decay flags / connections, so state equality never matches);</li>
     *   <li>everything else (glass, stained glass, ice, slime...): identical state and a full-extent
     *       mesh, exactly as before.</li>
     * </ul>
     * Only quads flush with the boundary plane are ever dropped, so thin geometry inside the block
     * (the faces of a pane) is never affected.
     */
    private boolean isHiddenInteriorQuad(BlockRenderContext ctx, BakedQuadView quad) {
        EnumFacing face = quad.getLightFace();

        if (face == null || !isFlushWithBoundary(quad, face)) {
            return false;
        }

        BlockPos pos = ctx.pos();
        this.neighborPos.set(pos.getX() + face.getFrontOffsetX(), pos.getY() + face.getFrontOffsetY(), pos.getZ() + face.getFrontOffsetZ());

        IBlockState self = ctx.state();
        IBlockState neighbor = this.blockAccess.getBlockState(this.neighborPos);
        Block selfBlock = self.getBlock();

        if (selfBlock instanceof BlockLeaves) {
            // Fast leaves: any leaf neighbor hides the interior face (their whole block renders
            // solid). Fancy leaves with "Hide Inner Faces": any leaf kind, same reasoning as the
            // per-face gate in isFaceVisible (dense canopies keep their outer faces only).
            if (!this.fancyLeaves || this.cullIdenticalTranslucent) {
                return neighbor.getBlock() instanceof BlockLeaves;
            }
        }

        if (neighbor.getBlock() != selfBlock) {
            return false;
        }

        if (selfBlock instanceof BlockPane) {
            return true;
        }

        // Leaves with "Hide Inner Faces" off: old parity — any state of the same leaf type
        // (decay flags may differ between sister blocks).
        if (selfBlock instanceof BlockLeaves) {
            return true;
        }

        return neighbor == self && this.isFullExtent(selfBlock);
    }

    private static boolean isFlushWithBoundary(BakedQuadView quad, EnumFacing face) {
        final float eps = 1.0E-4F;

        for (int i = 0; i < 4; i++) {
            float coord;
            float target;

            if (face.getFrontOffsetX() != 0) {
                coord = quad.getX(i);
                target = face.getFrontOffsetX() > 0 ? 1.0F : 0.0F;
            } else if (face.getFrontOffsetY() != 0) {
                coord = quad.getY(i);
                target = face.getFrontOffsetY() > 0 ? 1.0F : 0.0F;
            } else {
                coord = quad.getZ(i);
                target = face.getFrontOffsetZ() > 0 ? 1.0F : 0.0F;
            }

            if (Math.abs(coord - target) > eps) {
                return false;
            }
        }

        return true;
    }

    /**
     * Leaves follow the "Leaves" quality option instead of the vanilla global graphics flag:
     * fancy = cutout (transparent holes), fast = solid (opaque).
     */
    private Material getMaterial(IBlockState state) {
        if (state.getBlock() instanceof BlockLeaves) {
            return this.fancyLeaves ? DefaultMaterials.CUTOUT_MIPPED : DefaultMaterials.SOLID;
        }

        return DefaultMaterials.forBlockState(state);
    }

    private static boolean isOpaqueFullCube(Block block) {
        return block != Blocks.air && block.getLightOpacity() >= 255 && block.isFullCube();
    }

    private void renderQuadList(BlockRenderContext ctx, Material material, LightPipeline lighter, ColorProvider<IBlockState> colorizer, Vec3 offset,
                                ChunkModelBuilder builder, List<BakedQuad> quads, EnumFacing cullFace) {

        // This is a very hot allocation, iterate over it manually
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            BakedQuadView quad = (BakedQuadView) quads.get(i);

            // "Hide Inner Faces": the per-EnumFacing gate in isFaceVisible only sees quads whose model
            // declares a cullface, and only for full-cube blocks. Models without cullface (the
            // "general" list) and partial geometry such as glass panes / iron bars bypassed it
            // entirely, so their interior faces were always drawn. Decide per quad instead.
            if ((this.cullIdenticalTranslucent && material != DefaultMaterials.SOLID || !this.fancyLeaves && ctx.state().getBlock() instanceof BlockLeaves)
                    && this.isHiddenInteriorQuad(ctx, quad)) {
                continue;
            }

            final QuadLightData lightData = this.getVertexLight(ctx, lighter, cullFace, quad);
            final int[] vertexColors = this.getVertexColors(ctx, colorizer, quad);

            // Connected textures: swap the quad's TextureAtlasSprite for the tile that matches its neighbors.
            TextureAtlasSprite ctmFrom = null;
            TextureAtlasSprite ctmTo = null;

            if (CtmManager.isActive()) {
                ctmFrom = quad.getCtmSprite();

                if (ctmFrom != null) {
                    ctmTo = CtmManager.resolve(ctx.state(), ctx.pos(), this.blockAccess, quad, ctmFrom,
                            quad.getLightFace(), this.isFullExtent(ctx.state().getBlock()));
                }
            }

            this.writeGeometry(ctx, builder, offset, material, quad, vertexColors, lightData, ctmFrom, ctmTo);

            TextureAtlasSprite TextureAtlasSprite = quad.getSprite();

            if (TextureAtlasSprite != null) {
                builder.addSprite(TextureAtlasSprite);
            }
        }
    }

    private QuadLightData getVertexLight(BlockRenderContext ctx, LightPipeline lighter, EnumFacing cullFace, BakedQuadView quad) {
        QuadLightData light = this.quadLightData;
        lighter.calculate(quad, ctx.pos(), light, cullFace, quad.getLightFace(), quad.hasShade());

        return light;
    }

    private int[] getVertexColors(BlockRenderContext ctx, ColorProvider<IBlockState> colorProvider, BakedQuadView quad) {
        final int[] vertexColors = this.quadColors;

        if (colorProvider != null && quad.hasColor()) {
            colorProvider.getColors(ctx.world(), ctx.pos(), ctx.state(), quad, vertexColors);
        } else {
            Arrays.fill(vertexColors, 0xFFFFFFFF);
        }

        return vertexColors;
    }

    private void writeGeometry(BlockRenderContext ctx,
                               ChunkModelBuilder builder,
                               Vec3 offset,
                               Material material,
                               BakedQuadView quad,
                               int[] colors,
                               QuadLightData light,
                               TextureAtlasSprite ctmFrom,
                               TextureAtlasSprite ctmTo)
    {
        ModelQuadOrientation orientation = ModelQuadOrientation.orientByBrightness(light.br, light.lm);
        ChunkVertexEncoder.Vertex[] vertices = this.vertices;

        ModelQuadFacing normalFace = quad.getNormalFace();

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = orientation.getVertexIndex(dstIndex);

            ChunkVertexEncoder.Vertex out = vertices[dstIndex];
            out.x = ctx.origin().x() + quad.getX(srcIndex) + (float) offset.xCoord;
            out.y = ctx.origin().y() + quad.getY(srcIndex) + (float) offset.yCoord;
            out.z = ctx.origin().z() + quad.getZ(srcIndex) + (float) offset.zCoord;

            out.color = ColorABGR.withAlpha(colors != null ? colors[srcIndex] : 0xFFFFFFFF, light.br[srcIndex]);

            if (ctmTo != null) {
                // Keep the quad's position inside the TextureAtlasSprite, but land it on the replacement tile.
                float fromMinU = CtmCompat.minU(ctmFrom), fromMinV = CtmCompat.minV(ctmFrom);
                float ru = (quad.getTexU(srcIndex) - fromMinU) / (CtmCompat.maxU(ctmFrom) - fromMinU);
                float rv = (quad.getTexV(srcIndex) - fromMinV) / (CtmCompat.maxV(ctmFrom) - fromMinV);

                out.u = CtmCompat.minU(ctmTo) + ru * (CtmCompat.maxU(ctmTo) - CtmCompat.minU(ctmTo));
                out.v = CtmCompat.minV(ctmTo) + rv * (CtmCompat.maxV(ctmTo) - CtmCompat.minV(ctmTo));
            } else {
                out.u = quad.getTexU(srcIndex);
                out.v = quad.getTexV(srcIndex);
            }

            out.light = light.lm[srcIndex];
        }

        ChunkMeshBufferBuilder vertexBuffer = builder.getVertexBuffer(normalFace);
        vertexBuffer.push(vertices, material);
    }

    private LightMode getLightingMode(IBlockState state, IBakedModel model) {
        // 1.8.9 has no per-state luminance on the state itself; Block#getLightLevel() serves here.
        if (this.useAmbientOcclusion && model.isAmbientOcclusion() && state.getBlock().getLightValue() == 0) {
            return LightMode.SMOOTH;
        } else {
            return LightMode.FLAT;
        }
    }
}