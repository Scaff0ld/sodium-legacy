package me.sodiumlegacy.mod.client.ctm;

import me.sodiumlegacy.mod.client.model.quad.ModelQuadView;
import net.minecraft.util.EnumFacing;

/**
 * Works out, from the quad itself, which world directions the texture's U and V axes follow.
 * This makes connections correct for every face and any UV rotation baked into the model,
 * without hard-coding per-face orientation tables.
 */
public final class CtmGeometry {
    private CtmGeometry() {
    }

    /**
     * @param out [0] receives the EnumFacing in which U increases (texture "right"),
     *            [1] the EnumFacing in which V increases (texture "down")
     * @return false if the quad is degenerate
     */
    public static boolean axes(ModelQuadView quad, EnumFacing[] out) {
        float mu = 0, mv = 0, mx = 0, my = 0, mz = 0;

        for (int i = 0; i < 4; i++) {
            mu += quad.getTexU(i);
            mv += quad.getTexV(i);
            mx += quad.getX(i);
            my += quad.getY(i);
            mz += quad.getZ(i);
        }

        mu /= 4; mv /= 4; mx /= 4; my /= 4; mz /= 4;

        float[] du = new float[3];
        float[] dv = new float[3];

        for (int i = 0; i < 4; i++) {
            float u = quad.getTexU(i) - mu;
            float v = quad.getTexV(i) - mv;
            float x = quad.getX(i) - mx;
            float y = quad.getY(i) - my;
            float z = quad.getZ(i) - mz;

            du[0] += u * x; du[1] += u * y; du[2] += u * z;
            dv[0] += v * x; dv[1] += v * y; dv[2] += v * z;
        }

        out[0] = dominant(du);
        out[1] = dominant(dv);

        return out[0] != null && out[1] != null && out[0].getAxis() != out[1].getAxis();
    }

    private static EnumFacing dominant(float[] d) {
        int axis = 0;
        float best = Math.abs(d[0]);

        for (int i = 1; i < 3; i++) {
            if (Math.abs(d[i]) > best) {
                best = Math.abs(d[i]);
                axis = i;
            }
        }

        if (best < 1.0E-6F) {
            return null;
        }

        boolean positive = d[axis] > 0;

        switch (axis) {
            case 0: return positive ? EnumFacing.EAST : EnumFacing.WEST;
            case 1: return positive ? EnumFacing.UP : EnumFacing.DOWN;
            default: return positive ? EnumFacing.SOUTH : EnumFacing.NORTH;
        }
    }
}

