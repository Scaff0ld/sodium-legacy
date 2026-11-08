package me.sodiumlegacy.mod.client.particle;

import me.sodiumlegacy.mod.client.gui.SodiumGameOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFirework;

import java.util.Arrays;

/**
 * Fast, allocation-free lookup of which particles are currently disabled. The particle spawn
 * path is extremely hot (hundreds of calls per frame in busy scenes), so the user's config is
 * flattened into plain arrays/booleans whenever it changes instead of being queried per call.
 *
 * <p>Only ever touched from the client thread.</p>
 */
public final class ParticleFilter {
    // EnumParticleTypes ids in 1.8.9 go from 0 to 41.
    private static final boolean[] BLOCKED_IDS = new boolean[64];

    private static boolean blockBreakBlocked;
    private static boolean blockBreakingBlocked;
    private static boolean fireworkBlocked;

    private static String fireworkClassPrefix;
    private static boolean vanillaSettingNormalized;

    private ParticleFilter() {
    }

    /**
     * Rebuilds the lookup tables from the given config. Must be called after the config is
     * loaded and every time a particle option changes.
     */
    public static void rebuild(SodiumGameOptions options) {
        Arrays.fill(BLOCKED_IDS, false);

        blockBreakBlocked = false;
        blockBreakingBlocked = false;
        fireworkBlocked = false;

        for (ParticleToggle toggle : ParticleToggle.values()) {
            if (options.particles.isEnabled(toggle)) {
                continue;
            }

            for (int id : toggle.getIds()) {
                if (id >= 0 && id < BLOCKED_IDS.length) {
                    BLOCKED_IDS[id] = true;
                }
            }

            if (toggle == ParticleToggle.BLOCK_BREAK) {
                blockBreakBlocked = true;
            } else if (toggle == ParticleToggle.BLOCK_BREAKING) {
                blockBreakingBlocked = true;
            } else if (toggle == ParticleToggle.FIREWORK) {
                fireworkBlocked = true;
            }
        }
    }

    public static boolean isIdBlocked(int id) {
        return id >= 0 && id < BLOCKED_IDS.length && BLOCKED_IDS[id];
    }

    public static boolean isBlockBreakBlocked() {
        return blockBreakBlocked;
    }

    public static boolean isBlockBreakingBlocked() {
        return blockBreakingBlocked;
    }

    /**
     * Fireworks are spawned by vanilla directly as particle objects (a starter particle that
     * itself spawns spark particles), so they never go through the id-based entry point.
     */
    public static boolean isFireworkParticleBlocked(Object particle) {
        if (!fireworkBlocked) {
            return false;
        }

        if (fireworkClassPrefix == null) {
            fireworkClassPrefix = EntityFirework.class.getName();
        }

        // Covers the nested Starter/Spark/Flash classes too.
        return particle.getClass().getName().startsWith(fireworkClassPrefix);
    }

    /**
     * The vanilla "Particles: All/Decreased/Minimal" setting is no longer exposed in the video
     * settings. If an old options.txt left it on Decreased or Minimal, the player would have no
     * way to undo it, so it is reset to "All" once; per-particle toggles are the control now.
     */
    public static void normalizeVanillaSetting() {
        if (vanillaSettingNormalized) {
            return;
        }

        Minecraft client = Minecraft.getMinecraft();

        if (client == null || client.gameSettings == null) {
            return;
        }

        vanillaSettingNormalized = true;

        if (client.gameSettings.particleSetting != 0) {
            client.gameSettings.particleSetting = 0;
            client.gameSettings.saveOptions();
        }
    }
}
