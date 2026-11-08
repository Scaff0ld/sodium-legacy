package me.sodiumlegacy.mod.client.compatibility.checks;
import me.sodiumlegacy.mod.client.util.SodiumLang;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import me.sodiumlegacy.mod.client.gui.console.Console;
import me.sodiumlegacy.mod.client.gui.console.message.MessageLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ResourcePackScanner {
    private static final Logger LOGGER = LogManager.getLogger("SodiumLegacy-ResourcePackScanner");

    private static final Set<String> SHADER_PROGRAM_BLACKLIST = new HashSet<String>(Arrays.asList(
            "rendertype_solid.vsh",
            "rendertype_solid.fsh",
            "rendertype_solid.json",
            "rendertype_cutout_mipped.vsh",
            "rendertype_cutout_mipped.fsh",
            "rendertype_cutout_mipped.json",
            "rendertype_cutout.vsh",
            "rendertype_cutout.fsh",
            "rendertype_cutout.json",
            "rendertype_translucent.vsh",
            "rendertype_translucent.fsh",
            "rendertype_translucent.json",
            "rendertype_tripwire.vsh",
            "rendertype_tripwire.fsh",
            "rendertype_tripwire.json"
    ));

    private static final Set<String> SHADER_INCLUDE_BLACKLIST = new HashSet<String>(Arrays.asList(
            "light.glsl",
            "fog.glsl"
    ));

    /**
     * Directories (relative to the "minecraft" namespace) which are inspected for blacklisted
     * shader files. {@code shaders/core} is where Sodium's terrain core shaders live,
     * {@code shaders/include} contains their shared include files, and the remaining entries cover
     * alternate layouts used by resource packs.
     */
    private static final String[] SHADER_SEARCH_DIRECTORIES = new String[] {
            "shaders",
            "shaders/core",
            "shaders/include",
            "shaders/program"
    };

    /**
     * <a href="https://github.com/CaffeineMC/sodium-fabric/issues/1569">#1569</a>
     * Iterate through all active resource packs, and detect resource packs which contain files matching the blacklist.
     * An error message is shown for resource packs which replace terrain core shaders.
     * A warning is shown for resource packs which replace the default light.glsl and fog.glsl shaders.
     * Detailed information on shader files replaced by resource packs is printed in the client log.
     */
    public static void checkIfCoreShaderLoaded(IResourceManager manager) {
        List<ScannedResourcePack> outputs = new ArrayList<ScannedResourcePack>();

        for (IResourcePack resourcePack : getLoadedResourcePacks()) {
            if (isExternalResourcePack(resourcePack)) {
                outputs.add(scanResources(resourcePack));
            }
        }

        printToasts(outputs);
        printCompatibilityReport(outputs);
    }

    /**
     * Minecraft 1.8.9's resource manager does not allow the loaded resource packs to be enumerated,
     * so the (external) packs are collected through the resource pack loader instead.
     */
    private static List<IResourcePack> getLoadedResourcePacks() {
        List<IResourcePack> packs = new ArrayList<IResourcePack>();

        try {
            ResourcePackRepository repository = Minecraft.getMinecraft().getResourcePackRepository();

            if (repository != null) {
                for (ResourcePackRepository.Entry entry : repository.getRepositoryEntries()) {
                    packs.add(entry.getResourcePack());
                }

                IResourcePack serverPack = repository.getResourcePackInstance(); // pack loaded by a server ("pack" download)

                if (serverPack != null) {
                    packs.add(serverPack);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to enumerate the loaded resource packs", t);
        }

        return packs;
    }

    private static void printToasts(Collection<ScannedResourcePack> resourcePacks) {
        List<ScannedResourcePack> incompatibleResourcePacks = new ArrayList<ScannedResourcePack>();

        for (ScannedResourcePack pack : resourcePacks) {
            if (!pack.shaderPrograms.isEmpty()) {
                incompatibleResourcePacks.add(pack);
            }
        }

        List<ScannedResourcePack> likelyIncompatibleResourcePacks = new ArrayList<ScannedResourcePack>();

        for (ScannedResourcePack pack : resourcePacks) {
            if (!pack.shaderIncludes.isEmpty()) {
                likelyIncompatibleResourcePacks.add(pack);
            }
        }

        boolean shown = false;

        if (!incompatibleResourcePacks.isEmpty()) {
            showConsoleMessage(translate("sodiumlegacy.console.core_shaders_error",
                    "The following resource packs are incompatible with Sodium Legacy:"), MessageLevel.SEVERE);

            for (ScannedResourcePack entry : incompatibleResourcePacks) {
                showConsoleMessage(getResourcePackName(entry.resourcePack), MessageLevel.SEVERE);
            }

            shown = true;
        }

        if (!likelyIncompatibleResourcePacks.isEmpty()) {
            showConsoleMessage(translate("sodiumlegacy.console.core_shaders_warn",
                    "The following resource packs may be incompatible with Sodium Legacy:"), MessageLevel.WARN);

            for (ScannedResourcePack entry : likelyIncompatibleResourcePacks) {
                showConsoleMessage(getResourcePackName(entry.resourcePack), MessageLevel.WARN);
            }

            shown = true;
        }

        if (shown) {
            showConsoleMessage(translate("sodiumlegacy.console.core_shaders_info",
                    "Check the game log for detailed information."), MessageLevel.INFO);
        }
    }

    private static void printCompatibilityReport(Collection<ScannedResourcePack> scanResults) {
        StringBuilder builder = new StringBuilder();

        for (ScannedResourcePack entry : scanResults) {
            if (entry.shaderPrograms.isEmpty() && entry.shaderIncludes.isEmpty()) {
                continue;
            }

            builder.append("- Resource pack: ")
                    .append(getResourcePackName(entry.resourcePack))
                    .append("\n");

            if (!entry.shaderPrograms.isEmpty()) {
                emitProblem(builder,
                        "The resource pack replaces terrain shaders, which are not supported",
                        "https://github.com/CaffeineMC/sodium-fabric/wiki/Resource-Packs",
                        entry.shaderPrograms);
            }

            if (!entry.shaderIncludes.isEmpty()) {
                emitProblem(builder,
                        "The resource pack modifies shader include files, which are not fully supported",
                        "https://github.com/CaffeineMC/sodium-fabric/wiki/Resource-Packs",
                        entry.shaderIncludes);
            }
        }

        if (builder.length() > 0) {
            LOGGER.error("The following compatibility issues were found with installed resource packs:\n{}", builder);
        }
    }

    private static void emitProblem(StringBuilder builder, String description, String url, List<String> resources) {
        builder.append("\t- Problem found: ").append("\n");
        builder.append("\t\t- Description:\n\t\t\t").append(description).append("\n");
        builder.append("\t\t- More information: ").append(url).append("\n");
        builder.append("\t\t- Files: ").append("\n");

        for (String resource : resources) {
            builder.append("\t\t\t- ").append(resource).append("\n");
        }
    }

    @NotNull
    private static ScannedResourcePack scanResources(IResourcePack resourcePack) {
        final List<String> ignoredShaders = determineIgnoredShaders(resourcePack);

        if (!ignoredShaders.isEmpty()) {
            LOGGER.warn("Resource pack '{}' indicates the following shaders should be ignored: {}",
                    getResourcePackName(resourcePack), String.join(", ", ignoredShaders));
        }

        final ArrayList<String> unsupportedShaderPrograms = new ArrayList<String>();
        final ArrayList<String> unsupportedShaderIncludes = new ArrayList<String>();

        // Minecraft 1.8.9's resource packs cannot be asked to enumerate their contents, so the
        // known problem shader files are probed directly with contains() over the directories
        // listed in SHADER_SEARCH_DIRECTORIES.
        for (String directory : SHADER_SEARCH_DIRECTORIES) {
            for (String name : SHADER_PROGRAM_BLACKLIST) {
                if (ignoredShaders.contains(name)) {
                    continue;
                }

                String path = directory + "/" + name;

                if (resourcePack.resourceExists(new ResourceLocation("minecraft", path))) {
                    unsupportedShaderPrograms.add("assets/minecraft/" + path);
                }
            }

            for (String name : SHADER_INCLUDE_BLACKLIST) {
                if (ignoredShaders.contains(name)) {
                    continue;
                }

                String path = directory + "/" + name;

                if (resourcePack.resourceExists(new ResourceLocation("minecraft", path))) {
                    unsupportedShaderIncludes.add("assets/minecraft/" + path);
                }
            }
        }

        return new ScannedResourcePack(resourcePack, unsupportedShaderPrograms, unsupportedShaderIncludes);
    }

    private static boolean isExternalResourcePack(IResourcePack pack) {
        return pack instanceof FolderResourcePack || pack instanceof FileResourcePack;
    }

    private static String getResourcePackName(IResourcePack pack) {
        String path = pack.getPackName();

        // Omit 'file/' prefix for the in-game message
        return path.startsWith("file/") ? path.substring(5) : path;
    }

    /**
     * Looks at a resource pack's metadata to find a list of shaders that can be gracefully
     * ignored. This offers resource packs the ability to acknowledge they are shipping shaders
     * which will not work with Sodium, but that Sodium can ignore.
     *
     * @param resourcePack The resource pack to fetch the ignored shaders of
     * @return A list of shaders to ignore, this is the filename only without the path
     */
    private static List<String> determineIgnoredShaders(IResourcePack resourcePack) {
        ArrayList<String> ignoredShaders = new ArrayList<String>();

        try {
            File packFile = locateResourcePackFile(resourcePack);

            if (packFile == null) {
                return ignoredShaders;
            }

            String packMetadata = readPackMetadataJson(packFile).trim();

            if (packMetadata.isEmpty()) {
                return ignoredShaders;
            }

            JsonObject root = new JsonParser().parse(packMetadata).getAsJsonObject();

            SodiumResourcePackMetadata meta = SodiumResourcePackMetadata.fromJson(root);

            if (meta != null) {
                ignoredShaders.addAll(meta.ignoredShaders());
            }
        } catch (JsonSyntaxException | IOException | IllegalStateException x) {
            LOGGER.error("Failed to load pack.mcmeta file for resource pack '{}'", resourcePack.getPackName(), x);
        }

        return ignoredShaders;
    }

    /**
     * Tries to determine the file backing the given resource pack. Minecraft 1.8.9 keeps the
     * resource pack directory (usually "resourcepacks") out of the pack type itself, so the pack's
     * base file is reconstructed from its name. Packs loaded by remote servers live outside of the
     * resource pack directory and cannot be located this way ({@code null} is returned).
     */
    private static @Nullable File locateResourcePackFile(IResourcePack resourcePack) {
        try {
            File resourcePackDir = Minecraft.getMinecraft()
                    .getResourcePackRepository()
                    .getDirResourcepacks();

            if (resourcePackDir == null) {
                return null;
            }

            File packFile = new File(resourcePackDir, resourcePack.getPackName());

            if (packFile.isFile() || packFile.isDirectory()) {
                return packFile;
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to locate the file of resource pack '{}'", resourcePack.getPackName(), t);
        }

        return null;
    }

    /**
     * Reads (and returns unvalidated) the JSON content of a resource pack's `pack.mcmeta` file,
     * which is either the base directory for directory-based packs or a zip archive for
     * archive-based packs.
     */
    private static String readPackMetadataJson(File packFile) throws IOException {
        if (packFile.isDirectory()) {
            File metadataFile = new File(packFile, "pack.mcmeta");

            if (!metadataFile.isFile()) {
                throw new IOException("The pack.mcmeta file does not exist");
            }

            return new String(Files.readAllBytes(metadataFile.toPath()), StandardCharsets.UTF_8);
        } else {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(packFile);

            try {
                java.util.zip.ZipEntry entry = findZipEntry(zipFile, "pack.mcmeta");

                if (entry == null) {
                    throw new IOException("The pack.mcmeta file does not exist");
                }

                InputStream stream = zipFile.getInputStream(entry);

                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8));

                    StringBuilder builder = new StringBuilder();

                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        builder.append(line).append('\n');
                    }

                    reader.close();

                    return builder.toString();
                } finally {
                    stream.close();
                }
            } finally {
                zipFile.close();
            }
        }
    }

    private static @Nullable java.util.zip.ZipEntry findZipEntry(java.util.zip.ZipFile zipFile, String name) {
        Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            java.util.zip.ZipEntry entry = entries.nextElement();

            if (entry.getName().equals(name)) {
                return entry;
            }
        }

        return null;
    }

    private static void showConsoleMessage(String message, MessageLevel messageLevel) {
        Console.instance().logMessage(messageLevel, message, 12.5);
    }

    /**
     * Looks up a translated string through the resource pack language, falling back to the given
     * (English) default when the language storage is not available yet.
     */
    private static String translate(String key, String fallback) {
        return SodiumLang.translateOr(key, fallback);
    }

    private static final class ScannedResourcePack {
        private final IResourcePack resourcePack;
        private final ArrayList<String> shaderPrograms;
        private final ArrayList<String> shaderIncludes;

        private ScannedResourcePack(IResourcePack resourcePack,
                                    ArrayList<String> shaderPrograms,
                                    ArrayList<String> shaderIncludes)
        {
            this.resourcePack = resourcePack;
            this.shaderPrograms = shaderPrograms;
            this.shaderIncludes = shaderIncludes;
        }
    }
}
