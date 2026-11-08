package me.sodiumlegacy.mod.client.compatibility.checks;

import me.sodiumlegacy.mod.client.platform.MessageBox;
import me.sodiumlegacy.mod.client.platform.windows.api.version.LanguageCodePage;
import me.sodiumlegacy.mod.client.platform.windows.api.version.Version;
import me.sodiumlegacy.mod.client.platform.windows.api.version.VersionInfo;
import net.minecraft.util.Util;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for determining whether the current process has been injected into or otherwise modified. This should
 * generally only be accessed after OpenGL context creation, as most third-party software waits until the OpenGL ICD
 * is initialized before injecting.
 *
 * <p>Unlike Minecraft 1.20.1 (which exposes {@code WinNativeModuleUtil} for this purpose), Minecraft 1.8.9 provides no
 * such utility. The module scan is therefore implemented here directly, using only standard APIs and without any
 * reflection into the game: on Linux the loaded modules are read from {@code /proc/self/maps}, and on Windows the
 * module list of the current process is queried with {@code tasklist /m}.</p>
 */
public class ModuleScanner {
    private static final Logger LOGGER = LogManager.getLogger("SodiumLegacy-Win32ModuleChecks");

    private static final String[] RTSS_HOOKS_MODULE_NAMES = { "RTSSHooks64.dll", "RTSSHooks.dll" };

    /**
     * Cached result of the module scan. Once RivaTuner has injected into the process, the injected module will remain
     * loaded for the lifetime of the process, so the scan only needs to run (successfully) once.
     */
    private static volatile Boolean rtssHooksFound = null;

    public static void checkModules() {
        if (!Configuration.WIN32_RTSS_HOOKS) {
            return;
        }

        // RivaTuner hooks the wglCreateContext function, and leaves itself behind as a loaded module
        if (isModuleLoaded(RTSS_HOOKS_MODULE_NAMES)) {
            checkRTSSModules();
        }
    }

    private static void checkRTSSModules() {
        LOGGER.warn("RivaTuner Statistics Server (RTSS) has injected into the process! Attempting to apply workarounds for compatibility...");

        String version = null;

        try {
            version = findRTSSModuleVersion();
        } catch (Throwable t) {
            LOGGER.warn("Exception thrown while reading file version", t);
        }

        if (version == null) {
            LOGGER.warn("Could not determine version of RivaTuner Statistics Server");
        } else {
            LOGGER.info("Detected RivaTuner Statistics Server version: {}", version);
        }

        if (version == null || !isRTSSCompatible(version)) {
            try {
                MessageBox.showMessageBox(null, MessageBox.IconType.ERROR, "Sodium Legacy",
                        "You appear to be using an older version of RivaTuner Statistics Server (RTSS) which is not compatible with Sodium Legacy. " +
                                "You must either update to a newer version (7.3.4 and later) or close the RivaTuner Statistics Server application.\n\n" +
                                "For more information on how to solve this problem, click the 'Help' button.",
                        "https://github.com/CaffeineMC/sodium-fabric/wiki/Known-Issues#rtss-incompatible");
            } catch (Throwable t) {
                LOGGER.error("Could not display the compatibility message box", t);
            }

            throw new RuntimeException("RivaTuner Statistics Server (RTSS) is not compatible with Sodium, " +
                    "see here for more details: https://github.com/CaffeineMC/sodium-fabric/wiki/Known-Issues#rtss-incompatible");
        }
    }

    // BUG: For some reason, the version string can either be in the format of "X.Y.Z.W" or "X, Y, Z, W"...
    // This does not make sense, and probably points to our handling of code pages being wrong. But for the time being,
    // the regex has been made to handle both formats, because looking at Win32 code any longer is going to break me.
    private static final Pattern RTSS_VERSION_PATTERN = Pattern.compile("^(?<x>\\d*)(, |\\.)(?<y>\\d*)(, |\\.)(?<z>\\d*)(, |\\.)(?<w>\\d*)$");

    private static boolean isRTSSCompatible(String version) {
        Matcher matcher = RTSS_VERSION_PATTERN.matcher(version);

        if (!matcher.matches()) {
            return false;
        }

        try {
            int x = Integer.parseInt(matcher.group("x"));
            int y = Integer.parseInt(matcher.group("y"));
            int z = Integer.parseInt(matcher.group("z"));

            // >=7.3.4
            return x > 7 || (x == 7 && y > 3) || (x == 7 && y == 3 && z >= 4);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid version string: {}", version);
        }

        return false;
    }

    /**
     * Attempts to locate the RivaTuner Statistics Server executable and read its file version. On Minecraft 1.8.9 we
     * cannot rely on the native Windows API helpers (they are stubbed on this runtime), so the well-known installation
     * paths of RTSS are probed instead.
     */
    private static String findRTSSModuleVersion() {
        Path executablePath = findRTSSExecutable();

        if (executablePath == null) {
            LOGGER.warn("Could not find the RivaTuner Statistics Server executable");
            return null;
        }

        LOGGER.info("Parsing file: {}", executablePath);

        VersionInfo version = Version.getModuleFileVersion(executablePath.toAbsolutePath().toString());

        if (version == null) {
            LOGGER.warn("Couldn't find version structure");
            return null;
        }

        LanguageCodePage translation = version.queryEnglishTranslation();

        if (translation == null) {
            LOGGER.warn("Couldn't find suitable translation");
            return null;
        }

        String fileVersion = version.queryValue("FileVersion", translation);

        if (fileVersion == null) {
            LOGGER.warn("Couldn't query file version");
            return null;
        }

        return fileVersion;
    }

    /**
     * Looks for the RTSS executable ({@code RTSS.exe}) in the standard installation directories.
     */
    private static Path findRTSSExecutable() {
        String[] environmentKeys = new String[] { "ProgramFiles(x86)", "ProgramFiles" };

        for (String key : environmentKeys) {
            String directory = System.getenv(key);

            if (directory == null || directory.isEmpty()) {
                continue;
            }

            Path executablePath = Paths.get(directory, "RivaTuner Statistics Server", "RTSS.exe");

            if (Files.exists(executablePath)) {
                return executablePath;
            }
        }

        return null;
    }

    private static boolean isModuleLoaded(String[] names) {
        Boolean cached = rtssHooksFound;

        if (cached != null) {
            return cached.booleanValue();
        }

        boolean result = scanModules(names);

        // Only cache successful scans; a transient failure (e.g. tasklist not existing) should be retried.
        if (result) {
            rtssHooksFound = Boolean.TRUE;
        }

        return result;
    }

    /**
     * Performs a best-effort scan of the modules loaded within this process, without using any Minecraft or native
     * helper APIs. On Windows, the module list is queried by executing {@code tasklist}; elsewhere, the loaded modules
     * are read from {@code /proc/self/maps} (where available).
     */
    private static boolean scanModules(String[] names) {
        List<String> moduleNames = listLoadedModuleNames();

        for (String name : names) {
            for (String moduleName : moduleNames) {
                if (moduleName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<String> listLoadedModuleNames() {
        try {
            if (Util.getOSType() == Util.EnumOS.WINDOWS) {
                return listLoadedModuleNamesWindows();
            } else if (Util.getOSType() == Util.EnumOS.LINUX) {
                return listLoadedModuleNamesLinux();
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to scan the currently loaded modules", t);
        }

        return new ArrayList<String>();
    }

    private static List<String> listLoadedModuleNamesWindows() throws IOException, InterruptedException {
        List<String> modules = new ArrayList<String>();

        String processId = null;

        try {
            // "pid@host" format under Java 8
            String managedId = java.lang.management.ManagementFactory.getRuntimeMXBean()
                    .getName();
            int separator = managedId.indexOf('@');

            if (separator > 0) {
                processId = managedId.substring(0, separator);
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not determine the current process id", t);
        }

        if (processId == null) {
            return modules;
        }

        String[] command = new String[] { "tasklist", "/fi", "PID eq " + processId, "/m", "/fo:csv" };

        Process process = Runtime.getRuntime().exec(command);
        BufferedReader reader = null;

        try {
            // The output looks like:
            // "Image Name","PID","Modules"
            // "javaw.exe","3160","KERNEL32.dll ntdll.dll RTSSHooks64.dll ..."
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                // The third CSV field contains the space-separated module names
                String[] fields = line.split(",", 3);

                if (fields.length < 3) {
                    continue;
                }

                for (String module : fields[2].replace("\"", " ").split("\\s+")) {
                    if (!module.isEmpty()) {
                        modules.add(module);
                    }
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }

        process.waitFor();

        return modules;
    }

    private static List<String> listLoadedModuleNamesLinux() throws IOException {
        List<String> modules = new ArrayList<String>();

        File procMaps = new File("/proc/self/maps");

        if (!procMaps.isFile()) {
            return modules;
        }

        for (String line : Files.readAllLines(procMaps.toPath(), StandardCharsets.UTF_8)) {
            int lastSeparator = line.lastIndexOf('/');
            if (lastSeparator < 0) {
                continue;
            }

            modules.add(line.substring(lastSeparator + 1));
        }

        return modules;
    }
}
