package me.sodiumlegacy.mod.client.model.light.flat;

import me.sodiumlegacy.mod.client.model.light.LightPipeline;
import me.sodiumlegacy.mod.client.model.light.data.LightDataAccess;
import me.sodiumlegacy.mod.client.model.light.data.QuadLightData;
import me.sodiumlegacy.mod.client.model.quad.ModelQuadView;
import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFlags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Arrays;

import static me.sodiumlegacy.mod.client.model.light.data.LightDataAccess.*;

/**
 * A light pipeline which implements "classic-style" lighting through simply using the light value of the adjacent
 * block to a face.
 */
public class FlatLightPipeline implements LightPipeline {
    /**
     * The cache which light data will be accessed from.
     */
    private final LightDataAccess lightCache;

    public FlatLightPipeline(LightDataAccess lightCache) {
        this.lightCache = lightCache;
    }

    @Override
    public void calculate(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction cullFace, Direction lightFace, boolean shade) {
        int lightmap;

        // To match vanilla behavior, use the cull face if it exists/is available
        if (cullFace != null) {
            lightmap = getOffsetLightmap(pos, cullFace);
        } else {
            int flags = quad.getFlags();
            // If the face is aligned, use the light data above it
            // To match vanilla behavior, also treat the face as aligned if it is parallel and the block state is a full cube
            if ((flags & ModelQuadFlags.IS_ALIGNED) != 0 || ((flags & ModelQuadFlags.IS_PARALLEL) != 0 && unpackFC(this.lightCache.get(pos)))) {
                lightmap = getOffsetLightmap(pos, lightFace);
            } else {
                lightmap = getEmissiveLightmap(this.lightCache.get(pos));
            }
        }

        Arrays.fill(out.lm, lightmap);

        // The per-face directional brightness for vanilla 1.8.9 comes from hard-coded multipliers
        // (see LightDataAccess#getBrightness)
        Arrays.fill(out.br, LightDataAccess.getBrightness(lightFace, shade));
    }

    /**
     * When vanilla computes an offset lightmap with flat lighting, it uses the world light values from the
     * offset position, but the luminance from the origin position. {@link LightDataAccess} cannot efficiently
     * store lightmaps computed with inconsistent values so this method exists to mirror vanilla behavior as
     * closely as possible.
     */
    private int getOffsetLightmap(BlockPos pos, Direction face) {
        int word = this.lightCache.get(pos);

        // Check emissivity of the origin state
        if (unpackEM(word)) {
            return LightDataAccess.MAX_LIGHT_COORDINATE;
        }

        // Use world light values from the offset pos, but luminance from the origin pos
        int adjWord = this.lightCache.get(pos, face);
        return packLightmap(Math.max(unpackBL(adjWord), unpackLU(word)), unpackSL(adjWord));
    }
}
