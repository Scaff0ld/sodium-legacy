package me.sodiumlegacy.mod.client;

import me.sodiumlegacy.mod.client.compatibility.checks.EarlyDriverScanner;
import me.sodiumlegacy.mod.client.compatibility.workarounds.Workarounds;
import me.sodiumlegacy.mod.client.compatibility.environment.probe.GraphicsAdapterProbe;

/**
 * 1.8.9 port: Fabric's pre-launch hook is dropped; taken over by FML, which invokes
 * {@link #run()} from the mod container's constructor (see {@code SodiumLegacyForge}).
 */
public final class SodiumPreLaunch {
    private SodiumPreLaunch() {
    }

    public static void run() {
        GraphicsAdapterProbe.findAdapters();
        EarlyDriverScanner.scanDrivers();
        Workarounds.init();
    }
}
