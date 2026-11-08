package me.sodiumlegacy.mod.client.util.sorting;

import org.joml.Vector3f;

/**
 * Sorting strategies for vertex arrays. On 1.20.x, this class provided adapters for the vanilla
 * {@code VertexSorter} helper (which does not exist in the 1.8.9 fork); here the same distance-key
 * sorting is provided by a sodium-owned interface so callers outside of this area can sort vertices
 * uniformly.
 */
public class VertexSorters {
    public static VertexSorter sortByDistance(Vector3f origin) {
        return new SortByDistance(origin);
    }

    /**
     * A sodium-owned replacement for vanilla's {@code VertexSorter} interface from 1.19+.
     */
    public interface VertexSorter {
        int[] sort(Vector3f[] vertices);
    }

    private static class SortByDistance implements VertexSorter {
        private final Vector3f origin;

        private SortByDistance(Vector3f origin) {
            this.origin = origin;
        }

        @Override
        public int[] sort(Vector3f[] positions) {
            float[] keys = new float[positions.length];

            for (int index = 0; index < positions.length; index++) {
                keys[index] = this.origin.distanceSquared(positions[index]);
            }

            return MergeSort.mergeSort(keys);
        }
    }
}
