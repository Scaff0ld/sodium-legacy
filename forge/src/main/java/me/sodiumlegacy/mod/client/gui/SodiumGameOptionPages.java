package me.sodiumlegacy.mod.client.gui;
import me.sodiumlegacy.mod.client.util.SodiumLang;

import com.google.common.collect.ImmutableList;
import me.sodiumlegacy.mod.client.gl.arena.staging.MappedStagingBuffer;
import me.sodiumlegacy.mod.client.gl.device.RenderDevice;
import me.sodiumlegacy.mod.client.gui.options.*;
import me.sodiumlegacy.mod.client.gui.options.binding.compat.VanillaBooleanOptionBinding;
import me.sodiumlegacy.mod.client.gui.options.control.ControlValueFormatter;
import me.sodiumlegacy.mod.client.gui.options.control.CyclingControl;
import me.sodiumlegacy.mod.client.gui.options.control.SliderControl;
import me.sodiumlegacy.mod.client.gui.options.control.TickBoxControl;
import me.sodiumlegacy.mod.client.gui.options.storage.MinecraftOptionsStorage;
import me.sodiumlegacy.mod.client.gui.options.storage.SodiumOptionsStorage;
import me.sodiumlegacy.mod.client.particle.ParticleFilter;
import me.sodiumlegacy.mod.client.particle.ParticleToggle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.gui.ScaledResolution;

import java.util.ArrayList;
import java.util.List;

public class SodiumGameOptionPages {
    private static final SodiumOptionsStorage sodiumOpts = new SodiumOptionsStorage();
    private static final MinecraftOptionsStorage vanillaOpts = new MinecraftOptionsStorage();

    public static OptionPage general() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.renderDistance"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.view_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 2, (int) GameSettings.Options.RENDER_DISTANCE.getValueMax(), 1, (v) -> v + " chunks"))
                        .setBinding((options, value) -> {
                            options.renderDistanceChunks = value;
                            Minecraft.getMinecraft().renderGlobal.loadRenderers();
                        }, options -> options.renderDistanceChunks)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.gamma"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.brightness.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 100, 1, ControlValueFormatter.brightness()))
                        .setBinding((opts, value) -> opts.gammaSetting = value * 0.01F, (opts) -> (int) (opts.gammaSetting / 0.01F))
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.guiScale"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.gui_scale.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, calculateMaxGuiScale(), 1, ControlValueFormatter.guiScale()))
                        .setBinding((opts, value) -> {
                            opts.guiScale = value;

                            Minecraft client = Minecraft.getMinecraft();
                            ScaledResolution resolution = new ScaledResolution(client);

                            if (client.currentScreen != null) {
                                client.currentScreen.setWorldAndResolution(client, resolution.getScaledWidth(), resolution.getScaledHeight());
                            }
                        }, opts -> opts.guiScale)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.fullscreen"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.fullscreen.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            if (opts.fullScreen != value) {
                                opts.fullScreen = value;

                                Minecraft client = Minecraft.getMinecraft();

                                if (client.isFullScreen() != opts.fullScreen) {
                                    client.toggleFullscreen();

                                    // The client might not be able to enter full-screen mode
                                    opts.fullScreen = client.isFullScreen();
                                }
                            }
                        }, (opts) -> opts.fullScreen)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.vsync"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.v_sync.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding(new VanillaBooleanOptionBinding(GameSettings.Options.ENABLE_VSYNC))
                        .setImpact(OptionImpact.VARIES)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.framerateLimit"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.fps_limit.tooltip"))
                        .setControl(option -> new SliderControl(option, 10, 260, 10, ControlValueFormatter.fpsLimit()))
                        .setBinding((opts, value) -> opts.limitFramerate = value, opts -> opts.limitFramerate)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.viewBobbing"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.view_bobbing.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding(new VanillaBooleanOptionBinding(GameSettings.Options.VIEW_BOBBING))
                        .build())
                .build());

        return new OptionPage(SodiumLang.translate("stat.generalButton"), ImmutableList.copyOf(groups));
    }

    public static OptionPage quality() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                // 1.8.9 vanilla: graphics is a Fast/Fancy cycle button.
                .add(OptionImpl.createBuilder(SodiumGameOptions.GraphicsQuality.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.graphics"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.graphics_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.GraphicsQuality.class, new SodiumGameOptions.GraphicsQuality[] { SodiumGameOptions.GraphicsQuality.FAST, SodiumGameOptions.GraphicsQuality.FANCY }))
                        .setBinding((opts, value) -> {
                            boolean fancy = value == SodiumGameOptions.GraphicsQuality.FANCY;

                            if (opts.fancyGraphics != fancy) {
                                opts.fancyGraphics = fancy;
                                Minecraft.getMinecraft().renderGlobal.loadRenderers();
                            }
                        }, opts -> opts.fancyGraphics ? SodiumGameOptions.GraphicsQuality.FANCY : SodiumGameOptions.GraphicsQuality.FAST)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(CloudRenderMode.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.renderClouds"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.clouds_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, CloudRenderMode.class, new CloudRenderMode[] { CloudRenderMode.OFF, CloudRenderMode.FAST, CloudRenderMode.FANCY }))
                        .setBinding((opts, value) -> opts.clouds = value.getVanillaValue(), opts -> CloudRenderMode.fromVanillaValue(opts.clouds))
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.GraphicsQuality.class, sodiumOpts)
                        .setName(SodiumLang.translate("soundCategory.weather"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.weather_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.weatherQuality = value, opts -> opts.quality.weatherQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.GraphicsQuality.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.leaves_quality.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.leaves_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.leavesQuality = value, opts -> opts.quality.leavesQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.DynamicLightsMode.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.dynamic_lights.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.dynamic_lights.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.DynamicLightsMode.class))
                        .setBinding((opts, value) -> opts.quality.dynamicLights = value, opts -> opts.quality.dynamicLights)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.ao"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.smooth_lighting.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            if (opts.ambientOcclusion != (value ? 2 : 0)) {
                                opts.ambientOcclusion = value ? 2 : 0;
                                Minecraft.getMinecraft().renderGlobal.loadRenderers();
                            }
                        }, opts -> opts.ambientOcclusion != 0)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.entityShadows"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.entity_shadows.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.entityShadows = value, opts -> opts.entityShadows)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.vignette.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.vignette.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.enableVignette = value, opts -> opts.quality.enableVignette)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.translucent_culling.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.translucent_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.connectedTranslucentCulling = value, opts -> opts.quality.connectedTranslucentCulling)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.connected_textures.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.connected_textures.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.connectedTextures = value, opts -> opts.quality.connectedTextures)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                        .build())
                .build());


        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(SodiumLang.translate("options.mipmapLevels"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.mipmap_levels.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.multiplier()))
                        .setBinding(SodiumGameOptionPages::setMipmapLevels, opts -> opts.mipmapLevels)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.MipmapType.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.mipmap_type.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.mipmap_type.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.MipmapType.class))
                        .setBinding((opts, value) -> opts.quality.mipmapType = value, opts -> opts.quality.mipmapType)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.anisotropic_filtering.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.anisotropic_filtering.tooltip"))
                        // Slider position is the exponent: 0 -> off, 1 -> 2x, 2 -> 4x, 3 -> 8x, 4 -> 16x
                        .setControl(option -> new SliderControl(option, 0, 4, 1,
                                v -> v == 0 ? SodiumLang.translate("options.off") : (1 << v) + "x"))
                        .setBinding((opts, value) -> opts.quality.anisotropicLevel = 1 << value,
                                opts -> Math.min(4, Integer.numberOfTrailingZeros(Math.max(1, opts.quality.anisotropicLevel))))
                        .setImpact(OptionImpact.LOW)
                        .build())
                .build());


        return new OptionPage(SodiumLang.translate("sodiumlegacy.options.pages.quality"), ImmutableList.copyOf(groups));
    }

    /**
     * Mirrors what the vanilla {@code GameSettings} setter does when the mipmap level
     * changes on 1.8.9, since there is no SimpleOption to bind to.
     */
    private static void setMipmapLevels(GameSettings opts, int value) {
        int previous = opts.mipmapLevels;
        opts.mipmapLevels = value;

        if (previous != value) {
            Minecraft client = Minecraft.getMinecraft();
            client.getTextureMapBlocks().setMipmapLevels(value);
            client.scheduleResourcesRefresh();
        }
    }

    public static OptionPage performance() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.chunk_update_threads.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.chunk_update_threads.tooltip"))
                        .setControl(o -> new SliderControl(o, 0, Runtime.getRuntime().availableProcessors(), 1, ControlValueFormatter.quantityOrDisabled("threads", "Default")))
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.performance.chunkBuilderThreads = value, opts -> opts.performance.chunkBuilderThreads)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.always_defer_chunk_updates.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.always_defer_chunk_updates.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.performance.alwaysDeferChunkUpdates = value, opts -> opts.performance.alwaysDeferChunkUpdates)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                        .build())
                .build()
        );

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.use_block_face_culling.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.use_block_face_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.performance.useBlockFaceCulling = value, opts -> opts.performance.useBlockFaceCulling)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.use_fog_occlusion.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.use_fog_occlusion.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.performance.useFogOcclusion = value, opts -> opts.performance.useFogOcclusion)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.use_entity_culling.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.use_entity_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.performance.useEntityCulling = value, opts -> opts.performance.useEntityCulling)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.animate_only_visible_textures.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.animate_only_visible_textures.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.performance.animateOnlyVisibleTextures = value, opts -> opts.performance.animateOnlyVisibleTextures)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                        .build()
                )
                .build());

        return new OptionPage(SodiumLang.translate("sodiumlegacy.options.pages.performance"), ImmutableList.copyOf(groups));
    }

    public static OptionPage miscellaneous() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.zoom_factor.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.zoom_factor.tooltip"))
                        .setControl(option -> new SliderControl(option, 2, 10, 1, v -> v + "x"))
                        .setBinding((opts, value) -> opts.misc.zoomFactor = value, opts -> opts.misc.zoomFactor)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.MiscellaneousSettings.ZoomMode.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.zoom_mode.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.zoom_mode.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.MiscellaneousSettings.ZoomMode.class))
                        .setBinding((opts, value) -> opts.misc.zoomMode = value, opts -> opts.misc.zoomMode)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.zoom_smooth.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.zoom_smooth.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.misc.smoothZoom = value, opts -> opts.misc.smoothZoom)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .build());

        return new OptionPage(SodiumLang.translate("sodiumlegacy.options.pages.miscellaneous"), ImmutableList.copyOf(groups));
    }

    public static OptionPage advanced() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.use_persistent_mapping.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.use_persistent_mapping.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setEnabled(MappedStagingBuffer.isSupported(RenderDevice.INSTANCE))
                        .setBinding((opts, value) -> opts.advanced.useAdvancedStagingBuffers = value, opts -> opts.advanced.useAdvancedStagingBuffers)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setName(SodiumLang.translate("sodiumlegacy.options.cpu_render_ahead_limit.name"))
                        .setTooltip(SodiumLang.translate("sodiumlegacy.options.cpu_render_ahead_limit.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 9, 1, ControlValueFormatter.translateVariable("sodiumlegacy.options.cpu_render_ahead_limit.value")))
                        .setBinding((opts, value) -> opts.advanced.cpuRenderAheadLimit = value, opts -> opts.advanced.cpuRenderAheadLimit)
                        .build()
                )
                .build());

        // Per-particle switches (OptiFine style). If this ever gets too long for the Advanced
        // page, move this call into its own OptionPage and register it in SodiumOptionsGUI.
        groups.addAll(particleGroups());

        return new OptionPage(SodiumLang.translate("sodiumlegacy.options.pages.advanced"), ImmutableList.copyOf(groups));
    }

    private static List<OptionGroup> particleGroups() {
        List<OptionGroup> groups = new ArrayList<>();

        for (ParticleToggle.Group group : ParticleToggle.Group.values()) {
            OptionGroup.Builder builder = OptionGroup.createBuilder();

            for (ParticleToggle toggle : ParticleToggle.values()) {
                if (toggle.getGroup() == group) {
                    builder.add(createParticleOption(toggle));
                }
            }

            groups.add(builder.build());
        }

        return groups;
    }

    private static Option<Boolean> createParticleOption(ParticleToggle toggle) {
        String name = SodiumLang.translate(toggle.getNameTranslationKey());

        return OptionImpl.createBuilder(boolean.class, sodiumOpts)
                .setName(name)
                .setTooltip(SodiumLang.translate("sodiumlegacy.options.particles.tooltip", name))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> {
                    opts.particles.setEnabled(toggle, value);
                    ParticleFilter.rebuild(opts);
                }, opts -> opts.particles.isEnabled(toggle))
                .build();
    }

    private static int calculateMaxGuiScale() {
        Minecraft client = Minecraft.getMinecraft();

        int scale = 0;

        while (client.displayWidth / (scale + 1) >= 320 && client.displayHeight / (scale + 1) >= 240) {
            scale++;
        }

        return scale;
    }

    /**
     * Stand-in for the removed 1.20 {@code CloudRenderMode} option class.
     * Values map onto the vanilla 1.8.9 {@code GameSettings.cloudMode} field
     * (0 = off, 1 = fast, 2 = fancy).
     */
    private enum CloudRenderMode implements TextProvider {
        OFF("options.off"),
        FAST("options.graphics.fast"),
        FANCY("options.graphics.fancy");

        private final String name;

        CloudRenderMode(String name) {
            this.name = SodiumLang.translate(name);
        }

        @Override
        public String getLocalizedName() {
            return this.name;
        }

        public int getVanillaValue() {
            return this.ordinal();
        }

        public static CloudRenderMode fromVanillaValue(int value) {
            if (value >= 0 && value < values().length) {
                return values()[value];
            }

            return OFF;
        }
    }
}
