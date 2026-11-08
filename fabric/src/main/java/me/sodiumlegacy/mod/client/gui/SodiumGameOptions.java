package me.sodiumlegacy.mod.client.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import me.sodiumlegacy.mod.client.gui.options.TextProvider;
import me.sodiumlegacy.mod.client.particle.ParticleToggle;
import me.sodiumlegacy.mod.client.util.FileUtil;
import me.sodiumlegacy.mod.platform.ModPlatform;
import net.minecraft.client.resource.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class SodiumGameOptions {
    private static final String DEFAULT_FILE_NAME = "sodiumlegacy-options.json";

    public final QualitySettings quality = new QualitySettings();
    public final AdvancedSettings advanced = new AdvancedSettings();
    public final PerformanceSettings performance = new PerformanceSettings();
    public final ParticleSettings particles = new ParticleSettings();
    public final MiscellaneousSettings misc = new MiscellaneousSettings();

    private boolean readOnly;

    private SodiumGameOptions() {
        // NO-OP
    }

    public static SodiumGameOptions defaults() {
        return new SodiumGameOptions();
    }

    public static class PerformanceSettings {
        public int chunkBuilderThreads = 0;
        @SerializedName("always_defer_chunk_updates_v2") // this will reset the option in older configs
        public boolean alwaysDeferChunkUpdates = true;

        public boolean animateOnlyVisibleTextures = true;
        public boolean useEntityCulling = true;
        public boolean useFogOcclusion = true;
        public boolean useBlockFaceCulling = true;
    }

    public static class AdvancedSettings {
        public boolean enableMemoryTracing = false;
        public boolean useAdvancedStagingBuffers = true;

        public int cpuRenderAheadLimit = 3;
    }

    public static class ParticleSettings {
        // Keys of the particles the player turned off. Anything not listed is enabled.
        public Set<String> disabled = new LinkedHashSet<>();

        public boolean isEnabled(ParticleToggle toggle) {
            return this.disabled == null || !this.disabled.contains(toggle.getKey());
        }

        public void setEnabled(ParticleToggle toggle, boolean enabled) {
            if (this.disabled == null) {
                this.disabled = new LinkedHashSet<>();
            }

            if (enabled) {
                this.disabled.remove(toggle.getKey());
            } else {
                this.disabled.add(toggle.getKey());
            }
        }
    }

    /**
     * Miscellaneous client features (currently: the camera zoom), registered 
	 * under the "Miscellaneous" page of the video options screen.
     */
    public static class MiscellaneousSettings {
        /** How much the field of view is narrowed while the zoom key is held (default to 4). */
        public int zoomFactor = 4;

        public ZoomMode zoomMode = ZoomMode.HOLD;

        public boolean smoothZoom = true;

        /**
         * Zoom behaviour of the zoom keybind (default key: C, Miscellaneous category).
         * Two modes.
         */
        public enum ZoomMode implements TextProvider {
            HOLD("Hold"),
            TOGGLE("Toggle");

            private final String name;

            ZoomMode(String name) {
                this.name = name;
            }

            @Override
            public String getLocalizedName() {
                return this.name;
            }
        }
    }

    public static class QualitySettings {
        public GraphicsQuality weatherQuality = GraphicsQuality.DEFAULT;
        public GraphicsQuality leavesQuality = GraphicsQuality.DEFAULT;

        // Dynamic lights (held / dropped light-emitting blocks). Off by default.
        public DynamicLightsMode dynamicLights = DynamicLightsMode.OFF;

        public boolean enableVignette = true;

        /**
         * Interior faces between two adjacent <b>identical</b> translucent full-cube blocks 
		 * are never visible, so they are skipped in both directions, making e.g. a wall of 
		 * glass render as one seamless  body instead of a pile of seams (and saving quads).
         */
        public boolean connectedTranslucentCulling = true;

        /**
         * Connected textures: glass and stained glass connect on their own (tiles
         * generated at load time) and resource packs with OptiFine/MCPatcher CTM rules are honored.
         * Changing it needs a resource reload (F3 + T).
         */
        public boolean connectedTextures = true;

        // "Mipmap Type" and "Anisotropic Filtering" settings.
        public MipmapType mipmapType = MipmapType.LINEAR;
        public int anisotropicLevel = 1; // 1, 2, 4, 8 or 16
    }

    /**
     * How the block atlas is filtered when a texture is minified (i.e. in the distance). 
	 * The magnification filter is always GL_NEAREST so blocks stay crisp up close.
     */
    public enum MipmapType implements TextProvider {
        // GL_NEAREST_MIPMAP_NEAREST / GL_LINEAR_MIPMAP_NEAREST / GL_NEAREST_MIPMAP_LINEAR / GL_LINEAR_MIPMAP_LINEAR
        NEAREST("Nearest", 0x2700),
        LINEAR("Linear", 0x2702),     // vanilla 1.8.9 behaviour
        BILINEAR("Bilinear", 0x2701),
        TRILINEAR("Trilinear", 0x2703);

        private final String name;
        private final int glMinFilter;

        MipmapType(String name, int glMinFilter) {
            this.name = name;
            this.glMinFilter = glMinFilter;
        }

        public int getGlMinFilter() {
            return this.glMinFilter;
        }

        @Override
        public String getLocalizedName() {
            return this.name;
        }
    }

    /**
     * Fast re-evaluates the sources every 500 ms,
     * Fancy every tick.
     */
    public enum DynamicLightsMode implements TextProvider {
        OFF("options.off"),
        FAST("options.graphics.fast"),
        FANCY("options.graphics.fancy");

        private final String translationKey;

        DynamicLightsMode(String translationKey) {
            this.translationKey = translationKey;
        }

        @Override
        public String getLocalizedName() {
            // Translated lazily (see GraphicsQuality): I18n is not ready during class init.
            return I18n.translate(this.translationKey);
        }
    }

    public enum GraphicsQuality implements TextProvider {
        DEFAULT("Default", true),
        FANCY("options.graphics.fancy", false),
        FAST("options.graphics.fast", false);

        private final String name;
        private final @Nullable String translationKey;

        GraphicsQuality(String name, boolean literal) {
            this.name = name;
            this.translationKey = literal ? null : name;
        }

        @Override
        public String getLocalizedName() {
            // 1.8.9: the client entrypoint runs before the LanguageManager loads its resources,
            // so resolving I18n during enum class-initialization throws NPE on I18n.storage.
            // Translate lazily (render time), when the language storage is available.
            return this.translationKey == null ? this.name : I18n.translate(this.translationKey);
        }

        public boolean isFancy(boolean fancyGraphicsOrBetter) {
            return (this == FANCY) || (this == DEFAULT && fancyGraphicsOrBetter);
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static SodiumGameOptions loadFromDisk() {
        Path path = getConfigPath();
        SodiumGameOptions config;

        if (Files.exists(path)) {
            try (FileReader reader = new FileReader(path.toFile())) {
                config = GSON.fromJson(reader, SodiumGameOptions.class);
            } catch (IOException e) {
                throw new RuntimeException("Could not parse config", e);
            }
        } else {
            config = new SodiumGameOptions();
        }

        try {
            writeToDisk(config);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't update config file", e);
        }

        return config;
    }

    private static Path getConfigPath() {
        return ModPlatform.get()
                .getConfigDir()
                .resolve(DEFAULT_FILE_NAME);
    }

    public static void writeToDisk(SodiumGameOptions config) throws IOException {
        if (config.isReadOnly()) {
            throw new IllegalStateException("Config file is read-only");
        }

        Path path = getConfigPath();
        Path dir = path.getParent();

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } else if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }

        FileUtil.writeTextRobustly(GSON.toJson(config), path);
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public void setReadOnly() {
        this.readOnly = true;
    }
}
