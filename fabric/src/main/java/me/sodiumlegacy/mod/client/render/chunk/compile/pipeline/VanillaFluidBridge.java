package me.sodiumlegacy.mod.client.render.chunk.compile.pipeline;

import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFacing;
import me.sodiumlegacy.mod.client.render.chunk.compile.ChunkBuildBuffers;
import me.sodiumlegacy.mod.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.sodiumlegacy.mod.client.render.chunk.terrain.material.DefaultMaterials;
import me.sodiumlegacy.mod.client.render.chunk.terrain.material.Material;
import me.sodiumlegacy.mod.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import me.sodiumlegacy.mod.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.sodiumlegacy.mod.client.world.SlicedBlockView;
import net.minecraft.block.AbstractFluidBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.math.BlockPos;

import java.nio.ByteBuffer;

/**
 * 1.8.9 port: bridges vanilla's tessellated {@code FluidRenderer} output into sodium's chunk
 * meshing pipeline.
 *
 * <p>On 1.8.9, fluid blocks report block type {@code 1} and are tessellated by vanilla's
 * {@code FluidRenderer} (they are excluded from the state-to-model map, so resolving them as
 * baked models would produce the "missing texture" cube). Since sodium must not use vanilla's
 * chunk assembly (and wants section-local, per-face-grouped vertices), this bridge runs vanilla's
 * tessellation on a per-worker scratch {@link Tessellator} and then converts the buffered quads
 * into sodium chunk vertices.</p>
 *
 * <p>The bridge replicates vanilla's {@code BuiltChunk} setup exactly: draw mode 7 (GL_QUADS),
 * {@code VertexFormats.BLOCK}, and a world-to-section translation applied through
 * {@link BufferBuilder#offset(double, double, double)}, which vanilla's {@code #vertex(...)}
 * bakes into the written positions.</p>
 *
 * <p>Sodium's 1.8.9 mapping hazard: {@code Block#getBox()} returns null in these mappings;
 * nothing here relies on it.</p>
 *
 * <p>Instances are single-thread confined (one per worker context).</p>
 */
public class VanillaFluidBridge {
    /**
     * {@link VertexFormats#BLOCK} layout: position FLOAT3 (12 B), color UBYTE4 (4 B),
     * diffuse UV FLOAT2 (8 B), lightmap UV SHORT2 (4 B) = 28 bytes / 7 ints per vertex.
     */
    private static final int VERTEX_BYTES = 28;
    private static final int POS_OFFSET = 0;
    private static final int COLOR_OFFSET = 12;
    private static final int UV_OFFSET = 16;
    private static final int LIGHT_OFFSET = 24;

    private final Tessellator tessellator = new Tessellator(1 << 17);

    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();

    public void renderFluid(BlockState state, BlockPos pos, SlicedBlockView view, ChunkBuildBuffers buffers,
                            int originX, int originY, int originZ) {
        BufferBuilder buffer = this.tessellator.getBuffer();

        buffer.begin(7, VertexFormats.BLOCK);
        // Translate world-space tessellation into section-local coordinates (vanilla applies the
        // same trick per chunk in BuiltChunk).
        buffer.offset(-originX, -originY, -originZ);

        try {
            MinecraftClient.getInstance().getBlockRenderManager().renderBlock(state, pos, view, buffer);
        } finally {
            buffer.end();
        }

        int vertexCount = buffer.getVertexCount();

        if (vertexCount == 0) {
            return;
        }

        Material material = DefaultMaterials.forBlockState(state);
        ByteBuffer data = buffer.getByteBuffer();

        for (int start = 0; start + 3 < vertexCount; start += 4) {
            int base = start * VERTEX_BYTES;

            // Infer the quad's facing from its geometry so sodium can bucket the quad into the
            // per-axis vertex buffers it renders as separate draw sections.
            float x0 = data.getFloat(base + POS_OFFSET) + originX;
            float y0 = data.getFloat(base + POS_OFFSET + 4) + originY;
            float z0 = data.getFloat(base + POS_OFFSET + 8) + originZ;
            float x1 = data.getFloat((start + 1) * VERTEX_BYTES + POS_OFFSET) + originX;
            float y1 = data.getFloat((start + 1) * VERTEX_BYTES + POS_OFFSET + 4) + originY;
            float z1 = data.getFloat((start + 1) * VERTEX_BYTES + POS_OFFSET + 8) + originZ;
            float x2 = data.getFloat((start + 2) * VERTEX_BYTES + POS_OFFSET) + originX;
            float y2 = data.getFloat((start + 2) * VERTEX_BYTES + POS_OFFSET + 4) + originY;
            float z2 = data.getFloat((start + 2) * VERTEX_BYTES + POS_OFFSET + 8) + originZ;

            ModelQuadFacing facing = this.resolveFacing(
                    x1 - x0, y1 - y0, z1 - z0,
                    x2 - x0, y2 - y0, z2 - z0);

            for (int i = 0; i < 4; i++) {
                int vBase = (start + i) * VERTEX_BYTES;

                ChunkVertexEncoder.Vertex out = this.vertices[i];
                out.x = data.getFloat(vBase + POS_OFFSET);
                out.y = data.getFloat(vBase + POS_OFFSET + 4);
                out.z = data.getFloat(vBase + POS_OFFSET + 8);

                // Vanilla writes RGBA bytes sequentially (little-endian buffer); sodium's shader
                // expects the same ABGR-packed order. Keep alpha (vanilla uses it as the shade
                // multiplier and writes 1.0 for fluids).
                int r = data.get(vBase + COLOR_OFFSET) & 0xFF;
                int g = data.get(vBase + COLOR_OFFSET + 1) & 0xFF;
                int b = data.get(vBase + COLOR_OFFSET + 2) & 0xFF;
                int a = data.get(vBase + COLOR_OFFSET + 3) & 0xFF;
                out.color = (a << 24) | (b << 16) | (g << 8) | r;

                out.u = data.getFloat(vBase + UV_OFFSET);
                out.v = data.getFloat(vBase + UV_OFFSET + 4);

                // Lightmap element is the second UV (SHORT2): first short = block light << 4,
                // second short = sky light << 4. Sodium packs its light int identically:
                // low short = block light coordinate, high short = sky light coordinate.
                int blockLight = data.getShort(vBase + LIGHT_OFFSET) & 0xFFFF;
                int skyLight = data.getShort(vBase + LIGHT_OFFSET + 2) & 0xFFFF;
                out.light = (skyLight << 16) | blockLight;
            }

            ChunkModelBuilder meshBuilder = buffers.get(material);
            ChunkMeshBufferBuilder vertexBuffer = meshBuilder.getVertexBuffer(facing);
            vertexBuffer.push(this.vertices, material);

            Sprite sprite = this.resolveSprite(state, pos, view);
            if (sprite != null) {
                meshBuilder.addSprite(sprite);
            }
        }
    }

    /**
     * Resolves the sprite whose animation frame the tessellated UVs belong to, matching the
     * sprite picked by vanilla's {@code FluidRenderer} (still sprite when the fluid is not
     * flowing, flow sprite otherwise).
     */
    private Sprite resolveSprite(BlockState state, BlockPos pos, SlicedBlockView view) {
        if (!(state.getBlock() instanceof AbstractFluidBlock)) {
            return null;
        }

        AbstractFluidBlock fluid = (AbstractFluidBlock) state.getBlock();
        net.minecraft.block.material.Material material = fluid.getMaterial();
        String base = material == net.minecraft.block.material.Material.LAVA ? "lava" : "water";
        double direction = AbstractFluidBlock.getDirection(view, pos, material);

        String name = direction > -999.0D ? base + "_flow" : base + "_still";
        SpriteAtlasTexture atlas = MinecraftClient.getInstance().getSpriteAtlasTexture();

        return atlas.getSprite("minecraft:blocks/" + name);
    }

    /**
     * Chooses the dominant axis of the cross product normal as the quad's facing; the sign of
     * that axis component selects NEG/POS. (Fluid quads are planar within a block, so this is
     * unambiguous after dropping the two smaller components.)
     */
    private ModelQuadFacing resolveFacing(float ex, float ey, float ez, float fx, float fy, float fz) {
        float nx = ey * fz - ez * fy;
        float ny = ez * fx - ex * fz;
        float nz = ex * fy - ey * fx;

        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);

        if (ax >= ay && ax >= az) {
            return nx > 0.0F ? ModelQuadFacing.POS_X : ModelQuadFacing.NEG_X;
        } else if (ay >= az) {
            return ny > 0.0F ? ModelQuadFacing.POS_Y : ModelQuadFacing.NEG_Y;
        } else {
            return nz > 0.0F ? ModelQuadFacing.POS_Z : ModelQuadFacing.NEG_Z;
        }
    }
}
