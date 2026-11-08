package me.sodiumlegacy.mod.client.compatibility.environment.probe;

import net.minecraft.util.Util;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Probes the system for graphics adapters and identifies their vendor (and, where possible, driver
 * version). Minecraft 1.8.9 does not ship with a hardware prober such as OSHI, so this is
 * implemented with simple process-based heuristics: on Windows, the adapter list is queried with
 * {@code wmic path win32_VideoController}, and on Linux the PCI buses of {@code /sys/bus/pci} are
 * scanned, using {@code lspci} to resolve human-readable device names.
 */
public class GraphicsAdapterProbe {
    private static final Logger LOGGER = LogManager.getLogger("SodiumLegacy-GraphicsAdapterProbe");

    private static List<GraphicsAdapterInfo> ADAPTERS;

    public static void findAdapters() {
        LOGGER.info("Searching for graphics cards...");

        // We rely on separate detection logic for Linux because the fall-back helper commands fail
        // to find GPUs without display outputs, and we can also retrieve the driver version for
        // NVIDIA GPUs this way.
        Util.EnumOS operatingSystem = Util.getOSType();

        List<GraphicsAdapterInfo> results;

        if (operatingSystem == Util.EnumOS.WINDOWS) {
            results = findAdaptersWindows();
        } else if (operatingSystem == Util.EnumOS.LINUX) {
            results = findAdaptersLinux();
        } else {
            // No probing support for other platforms yet; reporting zero adapters is the safe fallback
            results = new ArrayList<GraphicsAdapterInfo>();
        }

        if (results.isEmpty()) {
            LOGGER.warn("No graphics cards were found. Either you have no hardware devices supporting 3D acceleration, or " +
                    "something has gone terribly wrong!");
        }

        ADAPTERS = results;
    }

    /**
     * Best-effort detection across platforms (kept for API compatibility); dispatches to the
     * platform-specific prober.
     */
    public static List<GraphicsAdapterInfo> findAdaptersCrossPlatform() {
        return Util.getOSType() == Util.EnumOS.LINUX
                ? findAdaptersLinux()
                : findAdaptersWindows();
    }

    /**
     * Queries the graphics adapters on Windows via the {@code wmic} command line utility. If
     * {@code wmic} is unavailable or returns nothing (newer versions of Windows deprecate it), a
     * fallback using PowerShell is attempted. As a final fallback, a placeholder adapter with an
     * unknown vendor is returned, so that the downstream compatibility checks still behave
     * gracefully.
     */
    private static List<GraphicsAdapterInfo> findAdaptersWindows() {
        List<GraphicsAdapterInfo> results = queryAdapters(
                Arrays.asList("wmic", "path", "win32_videocontroller", "get",
                        "AdapterCompatibility,DriverVersion,Name", "/format:list"),
                "=");

        if (results.isEmpty()) {
            results = queryAdapters(
                    Arrays.asList("powershell", "-NoProfile", "-Command",
                            "Get-CimInstance Win32_VideoController | Format-List AdapterCompatibility,DriverVersion,Name"),
                    " : ");
        }

        if (results.isEmpty()) {
            LOGGER.warn("Unable to probe the graphics adapters; using a placeholder adapter with an unknown vendor");
            results.add(new GraphicsAdapterInfo(GraphicsAdapterVendor.UNKNOWN, "unknown", "unknown"));
        }

        return results;
    }

    /**
     * Executes the given command and parses its output into one adapter entry per blank-line
     * separated block of {@code Key<separator>Value} pairs (this is the shape of both {@code wmic
     * /format:list} and PowerShell's {@code Format-List}).
     */
    private static List<GraphicsAdapterInfo> queryAdapters(List<String> command, String separator) {
        List<GraphicsAdapterInfo> results = new ArrayList<GraphicsAdapterInfo>();

        List<String> lines;

        try {
            lines = runCommand(command);
        } catch (Throwable t) {
            LOGGER.warn("Failed to query the graphics adapters with " + command.get(0), t);
            return results;
        }

        Map<String, String> block = new HashMap<String, String>();

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.isEmpty()) {
                addAdapterFromBlock(block, results);
                block.clear();
                continue;
            }

            int delimiter = line.indexOf(separator);
            if (delimiter < 0) {
                continue;
            }

            String key = line.substring(0, delimiter).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(delimiter + separator.length()).trim();

            block.put(key, value);
        }

        addAdapterFromBlock(block, results);

        return results;
    }

    private static void addAdapterFromBlock(Map<String, String> block, List<GraphicsAdapterInfo> results) {
        if (block.isEmpty()) {
            return;
        }

        // The block keys are normalized to lowercase for lookup
        String compatibility = block.get("adaptercompatibility");
        String name = block.containsKey("name") ? block.get("name") : "unknown";
        String version = block.containsKey("driverversion") ? block.get("driverversion") : "unknown";

        GraphicsAdapterVendor vendor = GraphicsAdapterVendor.identifyVendorFromString(
                compatibility != null ? compatibility : "");

        GraphicsAdapterInfo info = new GraphicsAdapterInfo(vendor, name, version);
        results.add(info);

        LOGGER.info("Found graphics card: {} ({})", info.name(), info.version());
    }

    /**
     * Enumerates the display and 3D-capable PCI devices from sysfs.
     */
    private static List<GraphicsAdapterInfo> findAdaptersLinux() {
        List<GraphicsAdapterInfo> results = new ArrayList<GraphicsAdapterInfo>();

        File devicesDirectory = new File("/sys/bus/pci/devices/");

        if (!devicesDirectory.isDirectory()) {
            return results;
        }

        File[] deviceFiles = devicesDirectory.listFiles();

        if (deviceFiles == null) {
            return results;
        }

        Arrays.sort(deviceFiles);

        for (File devicePath : deviceFiles) {
            // 0x030000 = VGA compatible controller
            // 0x030200 = 3D controller (GPUs with no outputs attached, e.g. hybrid graphics laptops)
            String deviceClass = readFileText(new File(devicePath, "class"));

            if (!"0x030000".equals(deviceClass) && !"0x030200".equals(deviceClass)) {
                continue;
            }

            String deviceVendor = readFileText(new File(devicePath, "vendor"));
            GraphicsAdapterVendor vendor = GraphicsAdapterVendor.identifyVendorFromString(
                    deviceVendor != null ? deviceVendor : "");

            // The Linux kernel doesn't provide a way to get the device name, so we need to use lspci,
            // since it comes with a list of known device names mapped to device IDs.
            String deviceId = readFileText(new File(devicePath, "device"));
            String name = "unknown";

            if (deviceVendor != null && deviceId != null && deviceVendor.length() > 2 && deviceId.length() > 2) {
                name = findLspciDeviceName(deviceVendor, deviceId);
            }

            // This works for the NVIDIA driver, not for i915/amdgpu/etc. though (for obvious reasons).
            String versionInfo = readFileText(new File(devicePath, "driver/module/version"));

            if (versionInfo == null) {
                versionInfo = "unknown";
            }

            GraphicsAdapterInfo info = new GraphicsAdapterInfo(vendor, name, versionInfo);
            results.add(info);

            LOGGER.info("Found graphics card: {} ({})", info.name(), info.version());
        }

        return results;
    }

    private static String findLspciDeviceName(String deviceVendor, String deviceId) {
        List<String> lines;

        try {
            lines = runCommand(Arrays.asList("lspci", "-vmm", "-d",
                    deviceVendor.substring(2) + ":" + deviceId.substring(2)));
        } catch (Throwable t) {
            LOGGER.debug("lspci could not be executed", t);
            return "unknown";
        }

        // See `man lspci` for more information
        for (String line : lines) {
            if (line.startsWith("Device:")) {
                return line.substring("Device:".length()).trim();
            }
        }

        return "unknown";
    }

    private static String readFileText(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {
            // The file does not exist or cannot be read
            return null;
        }
    }

    private static List<String> runCommand(List<String> command) throws IOException, InterruptedException {
        List<String> lines = new ArrayList<String>();

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true) // consume stderr together, so the pipe cannot fill up and deadlock
                .start();
        BufferedReader reader = null;

        try {
            InputStream stream = process.getInputStream();
            reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));

            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                lines.add(line);
            }

            reader.close();
            reader = null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }

        process.waitFor();
        process.destroy();

        return lines;
    }

    public static Collection<GraphicsAdapterInfo> getAdapters() {
        if (ADAPTERS == null) {
            throw new RuntimeException("Graphics adapters not probed yet");
        }

        return ADAPTERS;
    }
}
