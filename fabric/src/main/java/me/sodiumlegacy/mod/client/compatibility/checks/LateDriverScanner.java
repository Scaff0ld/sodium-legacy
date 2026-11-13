package me.sodiumlegacy.mod.client.compatibility.checks;
import me.sodiumlegacy.mod.client.util.SodiumLang;

import me.sodiumlegacy.mod.client.compatibility.workarounds.nvidia.NvidiaDriverVersion;
import me.sodiumlegacy.mod.client.gui.console.Console;
import me.sodiumlegacy.mod.client.gui.console.message.MessageLevel;
import me.sodiumlegacy.mod.client.compatibility.environment.GLContextInfo;
import net.minecraft.util.Util;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Performs OpenGL driver validation after the game creates an OpenGL context. This runs immediately after OpenGL
 * context creation, and uses the implementation details of the OpenGL context to perform validation.
 */
public class LateDriverScanner {
    private static final Logger LOGGER = LogManager.getLogger("SodiumLegacy-PostlaunchChecks");

    public static void onContextInitialized() {
        checkContextImplementation();

        if (isUsingPojavLauncher()) {
            Console.instance().logMessage(MessageLevel.SEVERE,
                    translate("sodiumlegacy.console.pojav_launcher",
                            "PojavLauncher is not supported when using Sodium Legacy.\n  * You are very likely to run into extreme " +
                                    "performance issues, graphical bugs, and crashes.\n  * You will be on your own if you decide " +
                                    "to continue -- we will not help you with any bugs or crashes!"), 30.0);
            LOGGER.error("It appears that PojavLauncher is being used with an OpenGL compatibility layer. This will " +
                    "likely cause severe performance issues, graphical issues, and crashes when used with Sodium Legacy. This " +
                    "configuration is not supported -- you are on your own!");
        }
    }

    private static void checkContextImplementation() {
        GLContextInfo driver = GLContextInfo.create();

        if (driver == null) {
            LOGGER.warn("Could not retrieve identifying strings for OpenGL implementation");
            return;
        }

        LOGGER.info("OpenGL Vendor: {}", driver.vendor());
        LOGGER.info("OpenGL Renderer: {}", driver.renderer());
        LOGGER.info("OpenGL Version: {}", driver.version());

        // Upstream installs an NVIDIA workaround here (masking the process command line and
        // setting environment variables) to stop the driver's "Threaded Optimizations" from
        // detecting the game. That mechanism relies on native helpers which do not exist on
        // the MC 1.8.9 runtime, so the effect cannot be reproduced; the manual equivalent is
        // pointed out instead.
        if (driver.renderer().contains("NVIDIA")) {
            LOGGER.info("NVIDIA GPU detected. If you experience unexplained crashes or severe performance issues, " +
                    "disable 'Threaded Optimizations' for javaw.exe in the NVIDIA Control Panel " +
                    "(Manage 3D settings -> Program Settings).");
        }

        if (!isSupportedNvidiaDriver(driver)) {
            Console.instance()
                    .logMessage(MessageLevel.SEVERE,
                            translate("sodiumlegacy.console.broken_nvidia_driver",
                                    "Your NVIDIA graphics drivers are out of date!\n  * This will cause severe performance " +
                                            "issues and crashes when Sodium Legacy is installed.\n  * Please update your graphics " +
                                            "drivers to the latest version (version 536.23 or newer.)"), 30.0);

            LOGGER.error("The NVIDIA graphics driver appears to be out of date. This will likely cause severe " +
                    "performance issues and crashes when used with Sodium Legacy. The graphics driver should be updated to " +
                    "the latest version (version 536.23 or newer).");
        }
    }

    // https://github.com/CaffeineMC/sodium-fabric/issues/1486
    // The way which NVIDIA tries to detect the Minecraft process could not be circumvented until fairly recently
    // So we require that an up-to-date graphics driver is installed so that our workarounds can disable the Threaded
    // Optimizations driver hack.
    private static boolean isSupportedNvidiaDriver(GLContextInfo driver) {
        // The Linux driver has two separate branches which have overlapping version numbers, despite also having
        // different feature sets. As a result, we can't reliably determine which Linux drivers are broken...
        if (Util.getOperatingSystem() != Util.OperatingSystem.WINDOWS) {
            return true;
        }

        NvidiaDriverVersion version = NvidiaDriverVersion.tryParse(driver);

        if (version != null) {
            return !version.isWithinRange(
                    new NvidiaDriverVersion(526, 47), // Broken in 526.47
                    new NvidiaDriverVersion(536, 23) // Fixed in 536.23
            );
        }

        // If we couldn't determine the version, then it's supported either way.
        return true;
    }

    // https://github.com/CaffeineMC/sodium-fabric/issues/1916
    private static boolean isUsingPojavLauncher() {
        if (System.getenv("POJAV_RENDERER") != null) {
            LOGGER.warn("Detected presence of environment variable POJAV_LAUNCHER, which seems to indicate we are running on Android");

            return true;
        }

        String librarySearchPaths = System.getProperty("java.library.path", null);

        if (librarySearchPaths != null) {
            for (String path : librarySearchPaths.split(":")) {
                if (isKnownAndroidPathFragment(path)) {
                    LOGGER.warn("Found a library search path which seems to be hosted in an Android filesystem: {}", path);

                    return true;
                }
            }
        }

        String workingDirectory = System.getProperty("user.home", null);

        if (workingDirectory != null) {
            if (isKnownAndroidPathFragment(workingDirectory)) {
                LOGGER.warn("Working directory seems to be hosted in an Android filesystem: {}", workingDirectory);
            }
        }

        return false;
    }

    private static boolean isKnownAndroidPathFragment(String path) {
        return path.matches("/data/user/[0-9]+/net\\.kdt\\.pojavlaunch");
    }

    /**
     * Looks up a translated string through the resource pack language, falling back to the given
     * (English) default when the language storage is not available yet.
     */
    private static String translate(String key, String fallback) {
        return SodiumLang.translateOr(key, fallback);
    }
}
