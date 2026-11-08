package me.sodiumlegacy.mod;

import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.SodiumPreLaunch;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * The Forge 1.8.9 entry point. Fabric offered two lifecycle hooks (pre-launch and client
 * initialization); both are mapped onto FML state events:
 *
 * <ul>
 *   <li>construction + {@code FMLPreInitializationEvent} (early, before the game loop and
 *   before an OpenGL context exists) runs what was SodiumPreLaunch's {@code onPreLaunch};</li>
 *   <li>{@code init} runs {@link SodiumClientMod}'s client initialization (config load,
 *   keybind registration, ...).</li>
 * </ul>
 */
@Mod(modid = SodiumLegacyForge.MOD_ID, name = "Sodium Legacy", clientSideOnly = true,
        version = SodiumLegacyForge.VERSION)
public class SodiumLegacyForge {
    public static final String MOD_ID = "sodiumlegacy";
    // Reads the stamped build-time value (see SodiumLegacyForge#version); the annotation needs
    // a constant, so it is only a placeholder for the @Mod metadata.
    public static final String VERSION = "1.0.0";

    public SodiumLegacyForge() {
        // Runs as early as FML can see the mod jar - the moral equivalent of pre-launch.
        SodiumPreLaunch.run();
    }

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        FMLLog.info("[SodiumLegacy] Client initialization");
        SodiumClientMod.init();
    }
}
