package me.sodiumlegacy.mod.client.dynamiclights;

import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.gui.SodiumGameOptions.DynamicLightsMode;
import me.sodiumlegacy.mod.client.render.SodiumWorldRenderer;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dynamic lights.
 *
 * <p>Light is baked into the chunk meshes, so a moving light source works by (1) keeping a small
 * immutable snapshot of the current sources, (2) letting the chunk builders add the snapshot's light
 * on top of the world's block light ({@code WorldSlice#getLightLevel}), and (3) scheduling a rebuild
 * of the sections around every source that appeared, disappeared, changed level or moved to another
 * block.</p>
 *
 * <p>Sources are the item held by every player and every dropped item entity whose item is a block
 * that emits light in vanilla (torch, glowstone, lit furnace...) or one of a few glowing items (lava
 * bucket, blaze rod, magma cream...), plus entities on fire and creepers about to explode. Light falls off with the
 * euclidean distance from the source's block, one level per block, like OptiFine (it is not
 * occluded by walls).</p>
 */
public final class DynamicLights {
    private static final int MAX_SOURCES = 32;
    private static final long FAST_INTERVAL_NANOS = 500_000_000L; // OptiFine "Fast": every 500 ms
    private static final long FANCY_INTERVAL_NANOS = 50_000_000L;  // OptiFine "Fancy": every tick

    // A creeper's swell state flips back and forth whenever its AI briefly loses sight of the target.
    // Without a hold time that made the light (and the chunk rebuilds around it) blink on and off.
    private static final long CREEPER_HOLD_NANOS = 1_000_000_000L;

    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    // Render-thread only: creeper -> time until which it keeps glowing
    private static final Map<Entity, Long> creeperGlowUntil = new HashMap<Entity, Long>();

    // Render-thread only
    private static WorldClient lastWorld;
    private static long lastUpdateNanos;

    private DynamicLights() {
    }

    /**
     * Extra block light (0..15) the dynamic sources contribute at the given block. Called from the
     * chunk builder threads.
     */
    public static int getLevel(int x, int y, int z) {
        Snapshot s = snapshot;
        int n = s.level.length;

        if (n == 0) {
            return 0;
        }

        int best = 0;

        for (int i = 0; i < n; i++) {
            int l = s.level[i];
            int dx = x - s.x[i];

            if (dx >= l || dx <= -l) {
                continue;
            }

            int dy = y - s.y[i];

            if (dy >= l || dy <= -l) {
                continue;
            }

            int dz = z - s.z[i];

            if (dz >= l || dz <= -l) {
                continue;
            }

            int d2 = dx * dx + dy * dy + dz * dz;

            if (d2 >= l * l) {
                continue;
            }

            int v = l - (int) Math.ceil(Math.sqrt(d2));

            if (v > best) {
                best = v;
            }
        }

        return best;
    }

    /**
     * Render thread, once per frame (from {@code SodiumWorldRenderer#setupTerrain}).
     */
    public static void update(Minecraft client, WorldClient world, SodiumWorldRenderer renderer) {
        if (world == null) {
            return;
        }

        if (world != lastWorld) {
            // New world: the old sources mean nothing here and its sections are gone.
            lastWorld = world;
            snapshot = Snapshot.EMPTY;
            lastUpdateNanos = 0L;
            creeperGlowUntil.clear();
        }

        DynamicLightsMode mode = SodiumClientMod.options().quality.dynamicLights;
        Snapshot old = snapshot;

        if (mode == DynamicLightsMode.OFF) {
            if (old != Snapshot.EMPTY) {
                snapshot = Snapshot.EMPTY;
                scheduleDifference(renderer, old.sources, Collections.<Source>emptyList());
            }

            creeperGlowUntil.clear();

            return;
        }

        long now = System.nanoTime();
        long interval = mode == DynamicLightsMode.FAST ? FAST_INTERVAL_NANOS : FANCY_INTERVAL_NANOS;

        if (now - lastUpdateNanos < interval) {
            return;
        }

        lastUpdateNanos = now;

        List<Source> found = collect(client, world);

        if (sameSources(old.sources, found)) {
            return;
        }

        snapshot = new Snapshot(found);
        scheduleDifference(renderer, old.sources, found);
    }

    private static List<Source> collect(Minecraft client, WorldClient world) {
        List<Source> found = new ArrayList<Source>();

        Entity camera = client.getRenderViewEntity();

        if (camera == null) {
            return found;
        }

        double maxDist = client.gameSettings.renderDistanceChunks * 16.0D + 32.0D;
        double maxDistSq = maxDist * maxDist;

        long now = System.nanoTime();
        List<Entity> entities = world.loadedEntityList;

        for (int i = 0, size = entities.size(); i < size; i++) {
            Entity entity = entities.get(i);

            ItemStack stack = null;
            double yOffset = 1.0D; // roughly the middle of a mob / where a player's hand is

            if (entity instanceof EntityPlayer) {
                stack = ((EntityPlayer) entity).getHeldItem();
            } else if (entity instanceof EntityItem) {
                stack = ((EntityItem) entity).getEntityItem();
                yOffset = 0.25D;
            }

            int level = stack == null ? 0 : getLuminance(stack);

            // Anything on fire, and creepers that are about to explode, glow at full brightness
            if (entity.isBurning()) {
                level = 15;
            } else if (entity instanceof EntityCreeper) {
                if (((EntityCreeper) entity).getCreeperState() > 0) {
                    creeperGlowUntil.put(entity, now + CREEPER_HOLD_NANOS);
                    level = 15;
                } else {
                    Long until = creeperGlowUntil.get(entity);

                    if (until != null && until > now) {
                        level = 15;
                    }
                }
            }

            if (level <= 0) {
                continue;
            }

            double dx = entity.posX - camera.posX;
            double dy = entity.posY - camera.posY;
            double dz = entity.posZ - camera.posZ;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > maxDistSq) {
                continue;
            }

            found.add(new Source(
                    (int) Math.floor(entity.posX),
                    (int) Math.floor(entity.posY + yOffset),
                    (int) Math.floor(entity.posZ),
                    level, distSq));
        }

        if (creeperGlowUntil.size() > 32) {
            // Forget creepers whose glow ran out (or that left the world without being seen again)
            for (Iterator<Long> it = creeperGlowUntil.values().iterator(); it.hasNext(); ) {
                if (it.next() <= now) {
                    it.remove();
                }
            }
        }

        if (found.size() > MAX_SOURCES) {
            Collections.sort(found);
            found = new ArrayList<Source>(found.subList(0, MAX_SOURCES));
        }

        return found;
    }

    /**
     * Light level of an item: the vanilla light level of the block it places, or a fixed level for the
     * few non-block items that glow (lava bucket, blaze rod, magma cream...). 0 for everything else.
     */
    private static int getLuminance(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return 0;
        }

        Item item = stack.getItem();
        Block block = Block.getBlockFromItem(item);

        if (block != null && block != Blocks.air) {
            return block.getLightValue();
        }

        // Items that are not blocks but glow anyway
        if (item == Items.lava_bucket) {
            return 15;
        } else if (item == Items.blaze_rod || item == Items.nether_star) {
            return 10;
        } else if (item == Items.magma_cream || item == Items.glowstone_dust || item == Items.prismarine_crystals) {
            return 8;
        }

        return 0;
    }

    private static boolean sameSources(List<Source> a, List<Source> b) {
        return a.size() == b.size() && new HashSet<Source>(a).equals(new HashSet<Source>(b));
    }

    /**
     * Rebuild the sections around every source that is in only one of the two lists.
     */
    private static void scheduleDifference(SodiumWorldRenderer renderer, List<Source> before, List<Source> after) {
        Set<Source> beforeSet = new HashSet<Source>(before);
        Set<Source> afterSet = new HashSet<Source>(after);

        for (Source s : before) {
            if (!afterSet.contains(s)) {
                scheduleAround(renderer, s);
            }
        }

        for (Source s : after) {
            if (!beforeSet.contains(s)) {
                scheduleAround(renderer, s);
            }
        }
    }

    private static void scheduleAround(SodiumWorldRenderer renderer, Source s) {
        int r = s.level;

        // Not "important": important rebuilds are awaited synchronously, and a light that keeps moving
        // or blinking would stall the frame every time it changes.
        renderer.scheduleRebuildForBlockArea(s.x - r, s.y - r, s.z - r, s.x + r, s.y + r, s.z + r, false);
    }

    private static final class Source implements Comparable<Source> {
        final int x, y, z, level;
        final double distSq; // not part of equality

        Source(int x, int y, int z, int level, double distSq) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
            this.distSq = distSq;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof Source)) {
                return false;
            }

            Source other = (Source) o;

            return this.x == other.x && this.y == other.y && this.z == other.z && this.level == other.level;
        }

        @Override
        public int hashCode() {
            int h = this.x;
            h = 31 * h + this.y;
            h = 31 * h + this.z;
            return 31 * h + this.level;
        }

        @Override
        public int compareTo(Source o) {
            return Double.compare(this.distSq, o.distSq);
        }
    }

    /**
     * Immutable, so the chunk builder threads can read it without locking.
     */
    private static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(Collections.<Source>emptyList());

        final List<Source> sources;
        final int[] x, y, z, level;

        Snapshot(List<Source> sources) {
            this.sources = Collections.unmodifiableList(new ArrayList<Source>(sources));

            int n = sources.size();
            this.x = new int[n];
            this.y = new int[n];
            this.z = new int[n];
            this.level = new int[n];

            for (int i = 0; i < n; i++) {
                Source s = sources.get(i);
                this.x[i] = s.x;
                this.y[i] = s.y;
                this.z[i] = s.z;
                this.level[i] = s.level;
            }
        }
    }
}

