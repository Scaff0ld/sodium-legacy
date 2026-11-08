package me.sodiumlegacy.mod.client.util.color;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.function.Function;

public class FastCubicSampler {
    private static final double[] DENSITY_CURVE = new double[] { 0.0D, 1.0D, 4.0D, 6.0D, 4.0D, 1.0D, 0.0D };
    private static final int DIAMETER = 6;

    public static Vec3d sampleColor(Vec3d pos, ColorFetcher colorFetcher, Function<Vec3d, Vec3d> transformer) {
        int intX = MathHelper.floor(pos.x);
        int intY = MathHelper.floor(pos.y);
        int intZ = MathHelper.floor(pos.z);

        int[] values = new int[DIAMETER * DIAMETER * DIAMETER];

        for(int x = 0; x < DIAMETER; ++x) {
            int blockX = (intX - 2) + x;

            for(int y = 0; y < DIAMETER; ++y) {
                int blockY = (intY - 2) + y;

                for(int z = 0; z < DIAMETER; ++z) {
                    int blockZ = (intZ - 2) + z;

                    values[index(x, y, z)] = colorFetcher.fetch(blockX, blockY, blockZ);
                }
            }
        }

        // Fast path! Skip blending the colors if all inputs are the same
        if (isHomogenousArray(values)) {
            // Take the first color if it's homogenous (all elements are the same...)
            return transformer.apply(unpackRgb(values[0]));
        }

        double deltaX = pos.x - (double)intX;
        double deltaY = pos.y - (double)intY;
        double deltaZ = pos.z - (double)intZ;

        double sumRed = 0.0D;
        double sumGreen = 0.0D;
        double sumBlue = 0.0D;
        double totalFactor = 0.0D;

        for(int x = 0; x < DIAMETER; ++x) {
            double densityX = lerp(DENSITY_CURVE[x + 1], DENSITY_CURVE[x], deltaX);

            for(int y = 0; y < DIAMETER; ++y) {
                double densityY = lerp(DENSITY_CURVE[y + 1], DENSITY_CURVE[y], deltaY);

                for(int z = 0; z < DIAMETER; ++z) {
                    double densityZ = lerp(DENSITY_CURVE[z + 1], DENSITY_CURVE[z], deltaZ);

                    double factor = densityX * densityY * densityZ;
                    totalFactor += factor;

                    Vec3d color = transformer.apply(unpackRgb(values[index(x, y, z)]));
                    sumRed += color.x * factor;
                    sumGreen += color.y * factor;
                    sumBlue += color.z * factor;
                }
            }
        }

        double totalFactorInv = 1.0D / totalFactor;

        return new Vec3d(sumRed * totalFactorInv, sumGreen * totalFactorInv, sumBlue * totalFactorInv);
    }

    private static int index(int x, int y, int z) {
        return (DIAMETER * DIAMETER * z) + (DIAMETER * y) + x;
    }

    public interface ColorFetcher {
        int fetch(int x, int y, int z);
    }

    private static boolean isHomogenousArray(int[] arr) {
        int val = arr[0];

        for (int i = 1; i < arr.length; i++) {
            if (arr[i] != val) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return The given 0xRRGGBB encoded color as a Vec3d with each component in the range of 0.0..1.0
     * (an equivalent of the {@code Vec3d#unpackRgb} helper which does not exist in the 1.8.9 fork)
     */
    private static Vec3d unpackRgb(int packedRgb) {
        return new Vec3d((packedRgb >> 16 & 0xFF) / 255.0D,
                (packedRgb >> 8 & 0xFF) / 255.0D,
                (packedRgb & 0xFF) / 255.0D);
    }

    /**
     * An unclamped linear interpolation helper
     * ({@code MathHelper#lerp} does not exist in the 1.8.9 fork)
     */
    private static double lerp(double start, double end, double delta) {
        return start + ((end - start) * delta);
    }
}
