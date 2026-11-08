package me.sodiumlegacy.mod.client.render.chunk.compile.pipeline;

import me.sodiumlegacy.mod.client.world.WorldSlice;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.BlockPos;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class BlockRenderContext {
    private final WorldSlice world;

    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private final Vector3f origin = new Vector3f();

    private IBlockState state;
    private IBakedModel model;

    private long seed;


    public BlockRenderContext(WorldSlice world) {
        this.world = world;
    }

    public void update(BlockPos pos, BlockPos origin, IBlockState state, IBakedModel model, long seed) {
        this.pos.set(pos.getX(), pos.getY(), pos.getZ());
        this.origin.set(origin.getX(), origin.getY(), origin.getZ());

        this.state = state;
        this.model = model;

        this.seed = seed;
    }

    /**
     * @return The position (in world space) of the block being rendered
     */
    public BlockPos pos() {
        return this.pos;
    }

    /**
     * @return The world which the block is being rendered from
     */
    public WorldSlice world() {
        return this.world;
    }

    /**
     * @return The state of the block being rendered
     */
    public IBlockState state() {
        return this.state;
    }

    /**
     * @return The model used for this block
     */
    public IBakedModel model() {
        return this.model;
    }

    /**
     * @return The origin of the block within the model
     */
    public Vector3fc origin() {
        return this.origin;
    }

    /**
     * @return The PRNG seed for rendering this block
     */
    public long seed() {
        return this.seed;
    }
}

