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
        // wmic is deprecated and no longer shipped on recent Windows builds (Windows 11 24H2+ purges
        // wmic.exe entirely). Trying to spawn a missing executable both throws a noisy IOException with
        // a full stack trace in the log and blocks while CreateProcess fails, so only attempt it when
        // the binary is actually present.
        // Note the JVM is 64-bit while on some Windows builds wmic lives only in the 32-bit tree, so
        // probe both System32 and SysWOW64 (Sysnative is not resolvable from 64-bit processes).
        if (hasExecutable("wbem", "wmic.exe")) {
            List<GraphicsAdapterInfo> results = queryAdapters(
                    Arrays.asList("wmic", "path", "win32_videocontroller", "get",
                            "AdapterCompatibility,DriverVersion,Name", "/format:list"),
                    "=");

            if (!results.isEmpty()) {
                return results;
            }
        }

        List<GraphicsAdapterInfo> results = queryAdapters(
                Arrays.asList("powershell", "-NoProfile", "-Command",
                        "Get-CimInstance Win32_VideoController | Format-List AdapterCompatibility,DriverVersion,Name"),
                " : ");

        if (results.isEmpty()) {
            LOGGER.warn("Unable to probe the graphics adapters; using a placeholder adapter with an unknown vendor");
            results.add(new GraphicsAdapterInfo(GraphicsAdapterVendor.UNKNOWN, "unknown", "unknown"));
        }

        return results;
    }

    /**
     * Executes the given command and parses its output into one adapter entry per adapter. The
     * output is a sequence of blank-line separated blocks of {@code Key<separator>Value} pairs
     * (the shape of both {@code wmic /format:list} and PowerShell's {@code Format-List}).
     *
     * <p>Known quirk: {@code wmic} can split the fields of a single adapter into several
     * consecutive blocks, each holding a different subset of the keys (for example, a driver
     * version block with no name, and a name block with no version). Blocks that share no keys
     * are therefore merged into the same adapter; a new adapter always re-reports every key.</p>
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

        // Collect the raw field blocks first (merging split blocks), then build the adapter
        // entries from the merged blocks.
        List<Map<String, String>> blocks = new ArrayList<Map<String, String>>();

        Map<String, String> block = new HashMap<String, String>();

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.isEmpty()) {
                if (!block.isEmpty()) {
                    blocks.add(block);
                    block = new HashMap<String, String>();
                }
                continue;
            }

            int delimiter = line.indexOf(separator);
            if (delimiter < 0) {
                continue;
            }

            String key = line.substring(0, delimiter).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(delimiter + separator.length()).trim();

            // wmic reports empty values for fields it could not read; normalize them away
            block.put(key, value.isEmpty() ? null : value);
        }

        if (!block.isEmpty()) {
            blocks.add(block);
        }

        // Merge consecutive blocks that share no keys into a single adapter block
        for (int i = 1; i < blocks.size(); i++) {
            Map<String, String> previous = blocks.get(i - 1);
            Map<String, String> current = blocks.get(i);

            if (!intersects(previous.keySet(), current.keySet())) {
                // null values in the merged map mean "field absent", so the non-null value wins
                for (Map.Entry<String, String> entry : current.entrySet()) {
                    String existing = previous.get(entry.getKey());

                    if (existing == null) {
                        previous.put(entry.getKey(), entry.getValue());
                    }
                }

                blocks.remove(i);
                i--;
            }
        }

        for (Map<String, String> adapterBlock : blocks) {
            addAdapterFromBlock(adapterBlock, results);
        }

        return results;
    }

    private static boolean intersects(java.util.Set<String> a, java.util.Set<String> b) {
        for (String key : b) {
            if (a.contains(key)) {
                return true;
            }
        }

        return false;
    }

    private static void addAdapterFromBlock(Map<String, String> block, List<GraphicsAdapterInfo> results) {
        // The block keys are normalized to lowercase for lookup
        String compatibility = block.get("adaptercompatibility");
        String name = block.get("name");
        String version = block.get("driverversion");

        GraphicsAdapterVendor vendor = GraphicsAdapterVendor.identifyVendorFromString(
                compatibility != null ? compatibility : "");

        // Old/broken wmic output often loses the AdapterCompatibility field; the human-readable
        // name almost always identifies the vendor ("Intel(R) HD Graphics", "NVIDIA GeForce ...").
        if (vendor == GraphicsAdapterVendor.UNKNOWN && name != null) {
            vendor = GraphicsAdapterVendor.identifyVendorFromString(name);
        }

        GraphicsAdapterInfo info = new GraphicsAdapterInfo(
                vendor,
                name != null ? name : "unknown",
                version != null ? version : "unknown");

        results.add(info);

        LOGGER.info("Found graphics card: {} ({}) [{}]", info.name(), info.version(), vendor);
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

    /**
     * Checks whether an executable lives in one of the system directories, without spawning a
     * process. Used to skip commands that were removed from recent Windows builds (e.g. wmic).
     */
    private static boolean hasExecutable(String subdirectory, String executable) {
        String windir = System.getenv("WINDIR");

        if (windir != null) {
            for (String parent : new String[] { "System32", "SysWOW64" }) {
                if (new File(new File(windir, parent), subdirectory)
                        .toPath()
                        .resolve(executable)
                        .toFile()
                        .isFile()) {
                    return true;
                }
            }
        }

        return false;
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
