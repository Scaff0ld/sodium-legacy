package me.sodiumlegacy.mod.client.model.light.data;

import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import me.sodiumlegacy.mod.client.world.WorldSlice;

/**
 * A light data cache which uses a hash table to store previously accessed values.
 */
public class HashLightDataCache extends LightDataAccess {
    private final Long2IntLinkedOpenHashMap map = new Long2IntLinkedOpenHashMap(1024, 0.50f);

    public HashLightDataCache(WorldSlice world) {
        this.world = world;
    }

    @Override
    public int get(int x, int y, int z) {
        long key = LightDataAccess.packLong(x, y, z);
        int word = this.map.getAndMoveToFirst(key);

        if (word == 0) {
            if (this.map.size() > 1024) {
                this.map.removeLastInt();
            }

            this.map.put(key, word = this.compute(x, y, z));
        }

        return word;
    }

    public void clearCache() {
        this.map.clear();
    }
}
