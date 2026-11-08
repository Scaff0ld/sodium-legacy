package me.sodiumlegacy.mod.client.render.chunk;

import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.gl.attribute.GlVertexAttributeBinding;
import me.sodiumlegacy.mod.client.gl.device.CommandList;
import me.sodiumlegacy.mod.client.gl.device.DrawCommandList;
import me.sodiumlegacy.mod.client.gl.device.MultiDrawBatch;
import me.sodiumlegacy.mod.client.gl.device.RenderDevice;
import me.sodiumlegacy.mod.client.gl.tessellation.GlIndexType;
import me.sodiumlegacy.mod.client.gl.tessellation.GlPrimitiveType;
import me.sodiumlegacy.mod.client.gl.tessellation.GlTessellation;
import me.sodiumlegacy.mod.client.gl.tessellation.TessellationBinding;
import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFacing;
import me.sodiumlegacy.mod.client.render.chunk.data.SectionRenderDataStorage;
import me.sodiumlegacy.mod.client.render.chunk.data.SectionRenderDataUnsafe;
import me.sodiumlegacy.mod.client.render.chunk.lists.ChunkRenderListIterable;
import me.sodiumlegacy.mod.client.render.chunk.lists.ChunkRenderList;
import me.sodiumlegacy.mod.client.render.chunk.region.RenderRegion;
import me.sodiumlegacy.mod.client.render.chunk.shader.ChunkShaderBindingPoints;
import me.sodiumlegacy.mod.client.render.chunk.shader.ChunkShaderInterface;
import me.sodiumlegacy.mod.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.sodiumlegacy.mod.client.render.chunk.terrain.TerrainRenderPass;
import me.sodiumlegacy.mod.client.render.chunk.vertex.format.ChunkMeshAttribute;
import me.sodiumlegacy.mod.client.render.chunk.vertex.format.ChunkVertexType;
import me.sodiumlegacy.mod.client.render.viewport.CameraTransform;
import me.sodiumlegacy.mod.client.util.BitwiseMath;
import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;

import java.util.Iterator;

public class DefaultChunkRenderer extends ShaderChunkRenderer {
    private final MultiDrawBatch batch;

    private final SharedQuadIndexBuffer sharedIndexBuffer;

    public DefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);

        this.batch = new MultiDrawBatch((ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1);
        this.sharedIndexBuffer = new SharedQuadIndexBuffer(device.createCommandList(), SharedQuadIndexBuffer.IndexType.INTEGER);
    }

    @Override
    public void render(ChunkRenderMatrices matrices,
                       CommandList commandList,
                       ChunkRenderListIterable renderLists,
                       TerrainRenderPass renderPass,
                       CameraTransform camera) {
        super.begin(renderPass);

        boolean useBlockFaceCulling = SodiumClientMod.options().performance.useBlockFaceCulling;

        ChunkShaderInterface shader = this.activeProgram.getInterface();
        shader.setProjectionMatrix(matrices.projection());
        shader.setModelViewMatrix(matrices.modelView());

        Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isReverseOrder());

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();

            RenderRegion region = renderList.getRegion();
            SectionRenderDataStorage storage = region.getStorage(renderPass);

            if (storage == null) {
                continue;
            }

            fillCommandBuffer(this.batch, region, storage, renderList, camera, renderPass, useBlockFaceCulling);

            if (this.batch.isEmpty()) {
                continue;
            }

            this.sharedIndexBuffer.ensureCapacity(commandList, this.batch.getIndexBufferSize());

            GlTessellation tessellation = this.prepareTessellation(commandList, region);

            setModelMatrixUniforms(shader, region, camera);
            executeDrawBatch(commandList, tessellation, this.batch);
        }

        super.end(renderPass);
    }

    private static void fillCommandBuffer(MultiDrawBatch batch,
                                          RenderRegion renderRegion,
                                          SectionRenderDataStorage renderDataStorage,
                                          ChunkRenderList renderList,
                                          CameraTransform camera,
                                          TerrainRenderPass pass,
                                          boolean useBlockFaceCulling) {
        batch.clear();

        me.sodiumlegacy.mod.client.util.iterator.ByteIterator iterator = renderList.sectionsWithGeometryIterator(pass.isReverseOrder());

        if (iterator == null) {
            return;
        }

        int originX = renderRegion.getChunkX();
        int originY = renderRegion.getChunkY();
        int originZ = renderRegion.getChunkZ();

        while (iterator.hasNext()) {
            int sectionIndex = iterator.nextByteAsInt();

            int chunkX = originX + LocalSectionIndex.unpackX(sectionIndex);
            int chunkY = originY + LocalSectionIndex.unpackY(sectionIndex);
            int chunkZ = originZ + LocalSectionIndex.unpackZ(sectionIndex);

            long pMeshData = renderDataStorage.getDataPointer(sectionIndex);

            int slices;

            if (useBlockFaceCulling) {
                slices = getVisibleFaces(camera.intX, camera.intY, camera.intZ, chunkX, chunkY, chunkZ);
            } else {
                slices = ModelQuadFacing.ALL;
            }

            slices &= SectionRenderDataUnsafe.getSliceMask(pMeshData);

            if (slices != 0) {
                addDrawCommands(batch, pMeshData, slices);
            }
        }
    }

    /**
     * Emits draw commands for the facings selected by {@code mask}. A section's vertices are stored
     * contiguously in facing order (see ChunkBuildBuffers#createMesh) and every draw uses the shared
     * quad index pattern, so runs of consecutive facings collapse into a single command: the base
     * vertex of the first facing and the sum of the element counts. This cuts the number of GL draw
     * calls (which are emulated one-by-one on LWJGL 2) to roughly one or two per section.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    private static void addDrawCommands(MultiDrawBatch batch, long pMeshData, int mask) {
        final long pBaseVertex = batch.pBaseVertex;
        final long pElementCount = batch.pElementCount;

        int size = batch.size;
        int facing = 0;

        while (mask != 0) {
            // skip facings that are not part of the mask
            int skip = Integer.numberOfTrailingZeros(mask);
            mask >>>= skip;
            facing += skip;

            // length of the run of consecutive facings present in the mask
            int run = Integer.numberOfTrailingZeros(~mask);
            int elements = 0;

            for (int i = 0; i < run; i++) {
                elements += SectionRenderDataUnsafe.getElementCount(pMeshData, facing + i);
            }

            MemoryUtil.memPutInt(pBaseVertex + (size << 2), SectionRenderDataUnsafe.getVertexOffset(pMeshData, facing));
            MemoryUtil.memPutInt(pElementCount + (size << 2), elements);
            size++;

            mask >>>= run;
            facing += run;
        }

        batch.size = size;
    }

    private static final int MODEL_UNASSIGNED = ModelQuadFacing.UNASSIGNED.ordinal();
    private static final int MODEL_POS_X = ModelQuadFacing.POS_X.ordinal();
    private static final int MODEL_POS_Y = ModelQuadFacing.POS_Y.ordinal();
    private static final int MODEL_POS_Z = ModelQuadFacing.POS_Z.ordinal();

    private static final int MODEL_NEG_X = ModelQuadFacing.NEG_X.ordinal();
    private static final int MODEL_NEG_Y = ModelQuadFacing.NEG_Y.ordinal();
    private static final int MODEL_NEG_Z = ModelQuadFacing.NEG_Z.ordinal();

    private static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
        int boundsMinX = (chunkX << 4), boundsMaxX = boundsMinX + 16;
        int boundsMinY = (chunkY << 4), boundsMaxY = boundsMinY + 16;
        int boundsMinZ = (chunkZ << 4), boundsMaxZ = boundsMinZ + 16;

        // the "unassigned" plane is always front-facing, since we can't check it
        int planes = (1 << MODEL_UNASSIGNED);

        planes |= BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_POS_X;
        planes |= BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_POS_Y;
        planes |= BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_POS_Z;

        planes |= BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_NEG_X;
        planes |= BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_NEG_Y;
        planes |= BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_NEG_Z;

        return planes;
    }

    private static void setModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);

        shader.setRegionOffset(x, y, z);
    }

    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

    private GlTessellation prepareTessellation(CommandList commandList, RenderRegion region) {
        RenderRegion.DeviceResources resources = region.getResources();
        GlTessellation tessellation = resources.getTessellation();

        if (tessellation == null) {
            resources.updateTessellation(commandList, tessellation = this.createRegionTessellation(commandList, resources));
        }

        return tessellation;
    }

    private GlTessellation createRegionTessellation(CommandList commandList, RenderRegion.DeviceResources resources) {
        return commandList.createTessellation(GlPrimitiveType.TRIANGLES, new TessellationBinding[] {
                TessellationBinding.forVertexBuffer(resources.getVertexBuffer(), new GlVertexAttributeBinding[] {
                        new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_POSITION_ID,
                                this.vertexFormat.getAttribute(ChunkMeshAttribute.POSITION_MATERIAL_MESH)),
                        new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_COLOR,
                                this.vertexFormat.getAttribute(ChunkMeshAttribute.COLOR_SHADE)),
                        new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_BLOCK_TEXTURE,
                                this.vertexFormat.getAttribute(ChunkMeshAttribute.BLOCK_TEXTURE)),
                        new GlVertexAttributeBinding(ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_TEXTURE,
                                this.vertexFormat.getAttribute(ChunkMeshAttribute.LIGHT_TEXTURE))
                }),
                TessellationBinding.forElementBuffer(this.sharedIndexBuffer.getBufferObject())
        });
    }

    private static void executeDrawBatch(CommandList commandList, GlTessellation tessellation, MultiDrawBatch batch) {
        try (DrawCommandList drawCommandList = commandList.beginTessellating(tessellation)) {
            drawCommandList.multiDrawElementsBaseVertex(batch, GlIndexType.UNSIGNED_INT);
        }
    }

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);

        this.sharedIndexBuffer.delete(commandList);
        this.batch.delete();
    }
}

