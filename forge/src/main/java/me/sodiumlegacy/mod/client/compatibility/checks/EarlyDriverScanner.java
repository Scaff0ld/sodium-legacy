package me.sodiumlegacy.mod.client.compatibility.checks;

import me.sodiumlegacy.mod.client.platform.MessageBox;
import me.sodiumlegacy.mod.client.platform.windows.WindowsDriverStoreVersion;
import me.sodiumlegacy.mod.client.compatibility.environment.probe.GraphicsAdapterInfo;
import me.sodiumlegacy.mod.client.compatibility.environment.probe.GraphicsAdapterProbe;
import me.sodiumlegacy.mod.client.compatibility.environment.probe.GraphicsAdapterVendor;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Performs OpenGL driver validation before the game creates an OpenGL context. This runs during the earliest possible
 * opportunity at game startup, and uses a custom hardware prober to search for problematic drivers.
 */
public class EarlyDriverScanner {
    private static final Logger LOGGER = LogManager.getLogger("SodiumLegacy-EarlyDriverScanner");

    private static final String CONSOLE_MESSAGE_TEMPLATE = "###ERROR_DESCRIPTION###\n"
            + "\n"
            + "For more information, please see: ###HELP_URL###\n";

    private static final String INTEL_GEN7_DRIVER_MESSAGE = "The game failed to start because the currently installed Intel Graphics Driver is not compatible.\n"
            + "\n"
            + "Installed version: ###CURRENT_DRIVER###\n"
            + "Required version: 15.33.53.5161 (or newer)\n"
            + "\n"
            + "You must update your graphics card driver in order to continue.";

    private static final String INTEL_GEN7_DRIVER_HELP_URL = "https://github.com/CaffeineMC/sodium-fabric/wiki/Driver-Compatibility#windows-intel-gen7";

    public static void scanDrivers() {
        if (Configuration.WIN32_DRIVER_INTEL_GEN7) {
            WindowsDriverStoreVersion installedVersion = findBrokenIntelGen7GraphicsDriver();

            if (installedVersion != null) {
                showUnsupportedDriverMessageBox(
                        INTEL_GEN7_DRIVER_MESSAGE
                                .replace("###CURRENT_DRIVER###", installedVersion.getFriendlyString()),
                        INTEL_GEN7_DRIVER_HELP_URL);
            }
        }
    }

    private static void showUnsupportedDriverMessageBox(String message, String url) {
        // Always print the information to the log file first, just in case we can't show the message box.
        LOGGER.error(CONSOLE_MESSAGE_TEMPLATE
                .replace("###ERROR_DESCRIPTION###", message)
                .replace("###HELP_URL###", url));

        // Try to show a graphical message box (if the platform supports it) and shut down the game.
        try {
            MessageBox.showMessageBox(null, MessageBox.IconType.ERROR, "Sodium Legacy - Unsupported Driver", message, url);
        } catch (Throwable t) {
            LOGGER.error("Could not display the compatibility message box", t);
        }

        System.exit(1 /* failure code */);
    }

    // https://github.com/CaffeineMC/sodium-fabric/issues/899
    private static @Nullable WindowsDriverStoreVersion findBrokenIntelGen7GraphicsDriver() {
        if (Util.getOSType() != Util.EnumOS.WINDOWS) {
            return null;
        }

        for (GraphicsAdapterInfo adapter : GraphicsAdapterProbe.getAdapters()) {
            if (adapter.vendor() != GraphicsAdapterVendor.INTEL) {
                continue;
            }

            try {
                WindowsDriverStoreVersion version = WindowsDriverStoreVersion.parse(adapter.version());

                if (version.driverModel() == 10 && version.featureLevel() == 18 && version.major() == 10) {
                    return version;
                }
            } catch (WindowsDriverStoreVersion.ParseException ignored) { }
        }

        return null;
    }
}
