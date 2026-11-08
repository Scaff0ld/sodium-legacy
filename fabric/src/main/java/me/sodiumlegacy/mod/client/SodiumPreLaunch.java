package me.sodiumlegacy.mod.client;

import me.sodiumlegacy.mod.client.compatibility.checks.EarlyDriverScanner;
import me.sodiumlegacy.mod.client.compatibility.workarounds.Workarounds;
import me.sodiumlegacy.mod.client.compatibility.environment.probe.GraphicsAdapterProbe;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class SodiumPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        GraphicsAdapterProbe.findAdapters();
        EarlyDriverScanner.scanDrivers();
        Workarounds.init();
    }
}
