package me.sodiumlegacy.mod.client;

import me.sodiumlegacy.mod.client.gui.SodiumGameOptions;
import me.sodiumlegacy.mod.client.gui.console.Console;
import me.sodiumlegacy.mod.client.gui.console.message.MessageLevel;
import me.sodiumlegacy.mod.client.particle.ParticleFilter;
import me.sodiumlegacy.mod.client.util.FlawlessFrames;
import me.sodiumlegacy.mod.client.zoom.ZoomFeature;
import me.sodiumlegacy.mod.platform.ModPlatform;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;

/**
 * Client-side initialization, driven by FML (see {@code me.sodiumlegacy.mod.SodiumLegacyForge}).
 */
public class SodiumClientMod {
    private static SodiumGameOptions CONFIG;
    private static Logger LOGGER;

    private static String MOD_VERSION;

    public static void init() {
        MOD_VERSION = ModPlatform.get().getModVersion();

        LOGGER = LogManager.getLogger("SodiumLegacy");
        CONFIG = loadConfig();
        ParticleFilter.rebuild(CONFIG);

        // The 1.8.9 Controls screen iterates GameOptions#allKeys, not KeyBinding#KEYS,
        // so the keybind additionally has to be appended to that array to show up
        // under "Options -> Controls" (vanilla's "Miscellaneous" category).
        ZoomFeature.registerKeyBinding();

        FlawlessFrames.onClientInitialization();
    }

    public static SodiumGameOptions options() {
        if (CONFIG == null) {
            throw new IllegalStateException("Config not yet available");
        }

        return CONFIG;
    }

    public static Logger logger() {
        if (LOGGER == null) {
            throw new IllegalStateException("Logger not yet available");
        }

        return LOGGER;
    }

    private static SodiumGameOptions loadConfig() {
        try {
            return SodiumGameOptions.loadFromDisk();
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration file", e);
            LOGGER.error("Using default configuration file in read-only mode");

            Console.instance().logMessage(MessageLevel.SEVERE, "Sodium could not load its configuration file; the default configuration will be used in read-only mode", 12.5);

            SodiumGameOptions config = SodiumGameOptions.defaults();
            config.setReadOnly();

            return config;
        }
    }

    public static void restoreDefaultOptions() {
        CONFIG = SodiumGameOptions.defaults();
        ParticleFilter.rebuild(CONFIG);

        try {
            SodiumGameOptions.writeToDisk(CONFIG);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write config file", e);
        }
    }

    public static String getVersion() {
        if (MOD_VERSION == null) {
            throw new NullPointerException("Mod version hasn't been populated yet");
        }

        return MOD_VERSION;
    }
}