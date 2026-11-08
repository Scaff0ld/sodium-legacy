package me.sodiumlegacy.mod.client.ctm;

import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.model.quad.BakedQuadView;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.GlassBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OptiFine-style connected textures.
 *
 * <p>Lifecycle (driven by {@code SpriteAtlasTextureMixin}):
 * {@link #beforeStitch} scans the active resource packs for CTM rules, generates the built-in
 * glass tiles and registers every tile with the block atlas; {@link #afterStitch} resolves the
 * stitched sprites and publishes an immutable {@link Snapshot}. While chunks are meshed,
 * {@link #resolve} swaps a quad's sprite for the right tile.</p>
 */
public final class CtmManager {
    private static final Logger LOGGER = LogManager.getLogger("SodiumLegacy-CTM");

    /** Atlas path prefix for everything this system adds: "minecraft:sodiumlegacyctm/...". */
    public static final String TILE_PREFIX = "sodiumlegacyctm/";

    private static final String[] GLASS_BASES = {
            "glass", "glass_white", "glass_orange", "glass_magenta", "glass_light_blue", "glass_yellow",
            "glass_lime", "glass_pink", "glass_gray", "glass_silver", "glass_cyan", "glass_purple",
            "glass_blue", "glass_brown", "glass_green", "glass_red", "glass_black"
    };

    /** PNG bytes served by the resource hook, keyed by resource path ("textures/sodiumctm/..png"). */
    private static final Map<String, byte[]> SERVED = new ConcurrentHashMap<>();

    private static Plan plan;
    private static volatile Snapshot snapshot;

    private static final class Plan {
        final List<CtmRule> rules = new ArrayList<>();
        /** glass base name ("glass_red") -> canonical mask -> registered atlas name */
        final Map<String, Map<Integer, String>> glassTiles = new LinkedHashMap<>();
    }

    private static final class Snapshot {
        final Map<Sprite, List<CtmRule>> byTile = new IdentityHashMap<>();
        final Map<Block, List<CtmRule>> byBlock = new IdentityHashMap<>();
        Sprite[] sprites = new Sprite[0];
        float[] bounds = new float[0]; // minU, maxU, minV, maxV per sprite
    }

    private CtmManager() {
    }

    // resource hook

    /** @return PNG bytes for "textures/sodiumlegacyctm/...png", or null if this path is not ours */
    public static byte[] getServedResource(String path) {
        return path.startsWith("textures/" + TILE_PREFIX) ? SERVED.get(path) : null;
    }

    /** @return true once the atlas has been stitched with connected-texture data */
    public static boolean isActive() {
        return snapshot != null;
    }

    public static boolean isEnabled() {
        return SodiumClientMod.options().quality.connectedTextures;
    }

    // atlas lifecycle

    public static void beforeStitch(SpriteAtlasTexture atlas) {
        snapshot = null;
        plan = null;
        SERVED.clear();

        if (!isEnabled()) {
            return;
        }

        try {
            Plan p = new Plan();

            readPackRules(p);
            planGlass(p);
            registerAll(atlas, p);

            plan = p;
        } catch (Throwable t) {
            LOGGER.error("Connected textures failed to prepare; they will be disabled", t);
            plan = null;
            SERVED.clear();
        }
    }

    public static void afterStitch(SpriteAtlasTexture atlas) {
        Plan p = plan;
        plan = null;

        if (p == null) {
            return;
        }

        try {
            Snapshot s = new Snapshot();

            resolveSprites(atlas, p, s);
            indexRules(atlas, p, s);
            indexAtlas(atlas, s);

            snapshot = s;

            LOGGER.info("Connected textures ready: {} rule(s)", p.rules.size());
        } catch (Throwable t) {
            LOGGER.error("Connected textures failed to initialize; they will be disabled", t);
            snapshot = null;
        }
    }

    // pack rules

    private static void readPackRules(Plan plan) {
        List<CtmPackReader.Source> packs = CtmPackReader.openActivePacks();
        // path -> rule, higher priority packs (later in the list) overwrite earlier ones
        Map<String, CtmRule> byPath = new LinkedHashMap<>();

        try {
            for (CtmPackReader.Source pack : packs) {
                for (String entry : pack.list()) {
                    String dir = ctmDir(entry);

                    if (dir == null || !entry.endsWith(".properties")) {
                        continue;
                    }

                    try {
                        CtmRule rule = parseRule(pack, entry);

                        if (rule != null) {
                            byPath.put(entry.substring(entry.indexOf("/ctm/")), rule);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Ignoring CTM rule {} in {}: {}", entry, pack.label(), e.toString());
                    }
                }
            }
        } finally {
            for (CtmPackReader.Source pack : packs) {
                pack.close();
            }
        }

        plan.rules.addAll(byPath.values());
    }

    /** @return the "assets/minecraft/(optifine|mcpatcher)/ctm" directory prefix if the entry lives in one */
    private static String ctmDir(String entry) {
        if (entry.startsWith("assets/minecraft/optifine/ctm/")) {
            return "assets/minecraft/optifine/ctm/";
        }

        if (entry.startsWith("assets/minecraft/mcpatcher/ctm/")) {
            return "assets/minecraft/mcpatcher/ctm/";
        }

        return null;
    }

    private static CtmRule parseRule(CtmPackReader.Source pack, String entry) throws Exception {
        byte[] data = pack.read(entry);

        if (data == null) {
            return null;
        }

        Properties props = new Properties();
        props.load(new ByteArrayInputStream(data));

        String methodName = props.getProperty("method", "ctm").trim().toLowerCase();
        CtmRule rule = new CtmRule();
        rule.name = entry;

        switch (methodName) {
            case "ctm": rule.method = CtmRule.Method.CTM; break;
            case "horizontal": rule.method = CtmRule.Method.HORIZONTAL; break;
            case "vertical": rule.method = CtmRule.Method.VERTICAL; break;
            case "top": rule.method = CtmRule.Method.TOP; break;
            case "fixed": rule.method = CtmRule.Method.FIXED; break;
            case "random": rule.method = CtmRule.Method.RANDOM; break;
            case "repeat": rule.method = CtmRule.Method.REPEAT; break;
            default:
                LOGGER.info("CTM method '{}' is not supported yet, skipping {}", methodName, entry);
                return null;
        }

        String baseName = entry.substring(entry.lastIndexOf('/') + 1, entry.length() - ".properties".length());
        String dir = entry.substring(0, entry.lastIndexOf('/'));

        rule.priority = parseInt(props.getProperty("weight"), 0);

        // matchBlocks / matchTiles (the file name is the default tile, like OptiFine)
        String matchBlocks = props.getProperty("matchBlocks");
        String matchTiles = props.getProperty("matchTiles");

        if (matchBlocks != null) {
            rule.matchBlocks = new HashSet<>();

            for (String token : split(matchBlocks)) {
                String id = token.contains(":") && token.indexOf(':') != token.lastIndexOf(':')
                        ? token.substring(0, token.lastIndexOf(':')) : token; // drop a ":meta" suffix
                Block block = CtmCompat.blockByName(id);

                if (block != null) {
                    rule.matchBlocks.add(block);
                }
            }

            if (rule.matchBlocks.isEmpty()) {
                return null;
            }
        }

        if (matchTiles != null) {
            rule.matchTiles = new HashSet<>();

            for (String token : split(matchTiles)) {
                rule.matchTiles.add(normalizeTileName(token));
            }
        }

        if (rule.matchBlocks == null && rule.matchTiles == null) {
            rule.matchTiles = new HashSet<>();
            rule.matchTiles.add(normalizeTileName(baseName));
        }

        String connect = props.getProperty("connect");

        if (connect == null) {
            rule.connect = rule.matchBlocks != null ? CtmRule.Connect.BLOCK : CtmRule.Connect.TILE;
        } else {
            switch (connect.trim().toLowerCase()) {
                case "block": rule.connect = CtmRule.Connect.BLOCK; break;
                case "tile": rule.connect = CtmRule.Connect.TILE; break;
                case "state": rule.connect = CtmRule.Connect.STATE; break;
                default: rule.connect = CtmRule.Connect.BLOCK;
            }
        }

        rule.metadataMask = parseRangeMask(props.getProperty("metadata"), 16, 0xFFFF);
        rule.facesMask = parseFaces(props.getProperty("faces"));

        // tiles
        String tiles = props.getProperty("tiles");

        if (tiles == null) {
            tiles = rule.method == CtmRule.Method.CTM ? "0-46"
                    : (rule.method == CtmRule.Method.HORIZONTAL || rule.method == CtmRule.Method.VERTICAL) ? "0-3" : "0";
        }

        List<String> names = new ArrayList<>();

        for (String token : split(tiles)) {
            for (String single : expandTile(token)) {
                names.add(registerTile(pack, dir, single));
            }
        }

        rule.tileNames = names.toArray(new String[0]);

        if (rule.method == CtmRule.Method.CTM && rule.tileNames.length < 47) {
            LOGGER.warn("CTM rule {} has {} tiles, expected 47; skipping", entry, rule.tileNames.length);
            return null;
        }

        if ((rule.method == CtmRule.Method.HORIZONTAL || rule.method == CtmRule.Method.VERTICAL) && rule.tileNames.length < 4) {
            LOGGER.warn("CTM rule {} needs 4 tiles; skipping", entry);
            return null;
        }

        rule.width = Math.max(1, parseInt(props.getProperty("width"), 1));
        rule.height = Math.max(1, parseInt(props.getProperty("height"), 1));

        if (rule.method == CtmRule.Method.REPEAT && rule.tileNames.length < rule.width * rule.height) {
            LOGGER.warn("CTM rule {} needs width*height tiles; skipping", entry);
            return null;
        }

        String weights = props.getProperty("weights");

        if (rule.method == CtmRule.Method.RANDOM) {
            rule.weights = new int[rule.tileNames.length];

            for (int i = 0; i < rule.weights.length; i++) {
                rule.weights[i] = 1;
            }

            if (weights != null) {
                String[] w = split(weights).toArray(new String[0]);

                for (int i = 0; i < w.length && i < rule.weights.length; i++) {
                    rule.weights[i] = Math.max(1, parseInt(w[i], 1));
                }
            }

            for (int w : rule.weights) {
                rule.totalWeight += w;
            }
        }

        return rule;
    }

    /** Turns a tile token into atlas-ready names, copying the PNG bytes into the served resources. */
    private static String registerTile(CtmPackReader.Source pack, String propsDir, String token) {
        String path = token.trim();

        if (path.startsWith("~/")) {
            path = "assets/minecraft/" + path.substring(2);
        } else if (path.startsWith("assets/")) {
            // already absolute
        } else if (path.startsWith("./")) {
            path = propsDir + "/" + path.substring(2);
        } else {
            path = propsDir + "/" + path;
        }

        if (!path.endsWith(".png")) {
            path = path + ".png";
        }

        try {
            byte[] png = pack.read(path);

            if (png != null) {
                String key = TILE_PREFIX + "p/" + Integer.toHexString(path.hashCode()) + "_" + path.length();
                SERVED.put("textures/" + key + ".png", png);

                return "minecraft:" + key;
            }
        } catch (Exception e) {
            LOGGER.warn("Could not read CTM tile {}: {}", path, e.toString());
        }

        // Not in the pack: maybe it refers to an existing block texture ("blocks/stone").
        String vanilla = token.trim();

        if (vanilla.endsWith(".png")) {
            vanilla = vanilla.substring(0, vanilla.length() - 4);
        }

        return normalizeTileName(vanilla);
    }

    private static List<String> expandTile(String token) {
        List<String> out = new ArrayList<>();
        int dash = token.indexOf('-');

        if (dash > 0 && isNumber(token.substring(0, dash)) && isNumber(token.substring(dash + 1))) {
            int from = Integer.parseInt(token.substring(0, dash));
            int to = Integer.parseInt(token.substring(dash + 1));

            for (int i = from; i <= to; i++) {
                out.add(String.valueOf(i));
            }
        } else {
            out.add(token);
        }

        return out;
    }

    private static String normalizeTileName(String token) {
        String t = token.trim();

        if (t.endsWith(".png")) {
            t = t.substring(0, t.length() - 4);
        }

        if (t.startsWith("minecraft:")) {
            t = t.substring("minecraft:".length());
        }

        if (t.startsWith("textures/")) {
            t = t.substring("textures/".length());
        }

        if (!t.contains("/")) {
            t = "blocks/" + t;
        }

        return "minecraft:" + t;
    }

    // built-in glass

    private static void planGlass(Plan plan) {
        int[] masks = GlassTileGenerator.allMasks();

        for (String base : GLASS_BASES) {
            byte[] png;

            try (InputStream in = CtmCompat.openResource(new Identifier("minecraft", "textures/blocks/" + base + ".png"))) {
                png = readAll(in);
            } catch (Exception e) {
                continue; // this pack has no such texture
            }

            try {
                BufferedImage image = GlassTileGenerator.decode(png);
                Map<Integer, String> tiles = new HashMap<>();

                for (int mask : masks) {
                    String key = TILE_PREFIX + "g/" + base + "/" + mask;
                    SERVED.put("textures/" + key + ".png", GlassTileGenerator.generate(image, mask));
                    tiles.put(mask, "minecraft:" + key);
                }

                plan.glassTiles.put(base, tiles);
            } catch (Exception e) {
                LOGGER.warn("Could not generate connected glass tiles for {}: {}", base, e.toString());
            }
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;

        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }

        return out.toByteArray();
    }

    // registration / resolve

    private static void registerAll(SpriteAtlasTexture atlas, Plan plan) {
        Set<String> registered = new HashSet<>();

        for (CtmRule rule : plan.rules) {
            for (String name : rule.tileNames) {
                if (name.startsWith("minecraft:" + TILE_PREFIX) && registered.add(name)) {
                    CtmCompat.registerSprite(atlas, idOf(name));
                }
            }
        }

        for (Map<Integer, String> tiles : plan.glassTiles.values()) {
            for (String name : tiles.values()) {
                if (registered.add(name)) {
                    CtmCompat.registerSprite(atlas, idOf(name));
                }
            }
        }
    }

    private static Identifier idOf(String name) {
        int colon = name.indexOf(':');

        return new Identifier(name.substring(0, colon), name.substring(colon + 1));
    }

    private static void resolveSprites(SpriteAtlasTexture atlas, Plan plan, Snapshot snap) {
        // Built-in glass rules are lowest priority, so any pack rule for the same tile wins.
        for (Map.Entry<String, Map<Integer, String>> e : plan.glassTiles.entrySet()) {
            CtmRule rule = new CtmRule();
            rule.name = "builtin:" + e.getKey();
            rule.method = CtmRule.Method.GENERATED_GLASS;
            // TILE: compare the neighbors' base sprite instead of the exact state. Glass
            // blocks all share their state, but panes carry connection properties that
            // differ between adjacent panes -- and OptiFine-style connectivity also joins
            // a pane to a full glass block of the same texture while keeping stained
            // colors apart (different base sprites).
            rule.connect = CtmRule.Connect.TILE;
            rule.priority = Integer.MIN_VALUE;
            rule.matchTiles = new HashSet<>();
            rule.matchTiles.add("minecraft:blocks/" + e.getKey());
            rule.generated = new Sprite[256];

            for (Map.Entry<Integer, String> tile : e.getValue().entrySet()) {
                rule.generated[tile.getKey()] = CtmCompat.getSprite(atlas, tile.getValue());
            }

            plan.rules.add(rule);
        }

        for (CtmRule rule : plan.rules) {
            rule.tiles = new Sprite[rule.tileNames.length];

            for (int i = 0; i < rule.tileNames.length; i++) {
                rule.tiles[i] = CtmCompat.getSprite(atlas, rule.tileNames[i]);
            }
        }
    }

    private static void indexRules(SpriteAtlasTexture atlas, Plan plan, Snapshot snap) {
        List<CtmRule> sorted = new ArrayList<>(plan.rules);

        Collections.sort(sorted, new Comparator<CtmRule>() {
            @Override
            public int compare(CtmRule a, CtmRule b) {
                return Integer.compare(b.priority, a.priority);
            }
        });

        for (CtmRule rule : sorted) {
            if (rule.matchTiles != null) {
                for (String tile : rule.matchTiles) {
                    Sprite sprite = CtmCompat.getSprite(atlas, tile);

                    if (sprite != null) {
                        List<CtmRule> list = snap.byTile.get(sprite);

                        if (list == null) {
                            list = new ArrayList<>();
                            snap.byTile.put(sprite, list);
                        }

                        list.add(rule);
                    }
                }
            }

            if (rule.matchBlocks != null) {
                for (Block block : rule.matchBlocks) {
                    List<CtmRule> list = snap.byBlock.get(block);

                    if (list == null) {
                        list = new ArrayList<>();
                        snap.byBlock.put(block, list);
                    }

                    list.add(rule);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void indexAtlas(SpriteAtlasTexture atlas, Snapshot snap) throws IllegalAccessException {
        Map<String, Sprite> best = null;

        for (Class<?> c = atlas.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !Map.class.isAssignableFrom(f.getType())) {
                    continue;
                }

                f.setAccessible(true);
                Map<?, ?> map = (Map<?, ?>) f.get(atlas);

                if (map == null || map.isEmpty()) {
                    continue;
                }

                Object any = map.values().iterator().next();

                if (any instanceof Sprite && (best == null || map.size() > best.size())) {
                    best = (Map<String, Sprite>) map;
                }
            }
        }

        if (best == null) {
            throw new IllegalStateException("Could not find the atlas sprite map");
        }

        List<Sprite> list = new ArrayList<>(new HashSet<>(best.values()));
        snap.sprites = list.toArray(new Sprite[0]);
        snap.bounds = new float[list.size() * 4];

        for (int i = 0; i < snap.sprites.length; i++) {
            Sprite s = snap.sprites[i];
            snap.bounds[i * 4] = CtmCompat.minU(s);
            snap.bounds[i * 4 + 1] = CtmCompat.maxU(s);
            snap.bounds[i * 4 + 2] = CtmCompat.minV(s);
            snap.bounds[i * 4 + 3] = CtmCompat.maxV(s);
        }
    }

    // quad -> sprite

    /** Finds the sprite whose atlas rectangle contains the quad's UVs (1.8.9 quads lose their sprite). */
    public static Sprite findSprite(BakedQuadView quad) {
        Snapshot s = snapshot;

        if (s == null) {
            return null;
        }

        float u = 0, v = 0;

        for (int i = 0; i < 4; i++) {
            u += quad.getTexU(i);
            v += quad.getTexV(i);
        }

        u /= 4;
        v /= 4;

        float[] b = s.bounds;

        for (int i = 0, n = s.sprites.length; i < n; i++) {
            int o = i * 4;

            if (u >= b[o] && u <= b[o + 1] && v >= b[o + 2] && v <= b[o + 3]) {
                return s.sprites[i];
            }
        }

        return null;
    }

    // resolution

    private static final ThreadLocal<Scratch> SCRATCH = new ThreadLocal<Scratch>() {
        @Override
        protected Scratch initialValue() {
            return new Scratch();
        }
    };

    private static final class Scratch {
        final Direction[] axes = new Direction[2];
        final BlockPos.Mutable pos = new BlockPos.Mutable();
    }

    /**
     * @param fullCube whether the block fills its whole 1x1x1 extent (kept for compatibility;
     *                 the built-in glass rule also serves glass panes, whose body quads carry
     *                 the same base sprite as the glass blocks)
     * @return the replacement sprite for this quad, or null to keep the original
     */
    public static Sprite resolve(BlockState state, BlockPos pos, BlockView view, BakedQuadView quad, Sprite base,
                                 Direction face, boolean fullCube) {
        Snapshot snap = snapshot;

        if (snap == null || base == null || face == null) {
            return null;
        }

        List<CtmRule> byTile = snap.byTile.get(base);
        List<CtmRule> byBlock = snap.byBlock.get(state.getBlock());

        if (byTile == null && byBlock == null) {
            return null;
        }

        Scratch scratch = SCRATCH.get();

        if (!CtmGeometry.axes(quad, scratch.axes)) {
            return null;
        }

        int meta = -1;

        for (int pass = 0; pass < 2; pass++) {
            List<CtmRule> list = pass == 0 ? byBlock : byTile;

            if (list == null) {
                continue;
            }

            for (int i = 0, n = list.size(); i < n; i++) {
                CtmRule rule = list.get(i);

                if (!rule.appliesToFace(face)) {
                    continue;
                }

                // GENERATED_GLASS only matches by base sprite (the glass textures), so it
                // naturally covers pane body quads too; pane-edge quads carry a different
                // sprite ("glass_pane_top") and fall through.

                if (rule.metadataMask != 0xFFFF) {
                    if (meta < 0) {
                        meta = CtmCompat.metaOf(state);
                    }

                    if (!rule.appliesToMeta(meta)) {
                        continue;
                    }
                }

                Sprite result = apply(rule, state, pos, view, base, face, scratch);

                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private static Sprite apply(CtmRule rule, BlockState state, BlockPos pos, BlockView view, Sprite base,
                                Direction face, Scratch s) {
        Direction uDir = s.axes[0];
        Direction vDir = s.axes[1];

        switch (rule.method) {
            case FIXED:
                return tile(rule, 0);

            case TOP:
                return connected(rule, state, pos, view, base, face, s, 0, 1, 0) ? tile(rule, 0) : null;

            case HORIZONTAL: {
                boolean left = connected(rule, state, pos, view, base, face, s, -uDir.getOffsetX(), -uDir.getOffsetY(), -uDir.getOffsetZ());
                boolean right = connected(rule, state, pos, view, base, face, s, uDir.getOffsetX(), uDir.getOffsetY(), uDir.getOffsetZ());

                return tile(rule, CtmTables.LINEAR[(left ? 1 : 0) | (right ? 2 : 0)]);
            }

            case VERTICAL: {
                boolean up = connected(rule, state, pos, view, base, face, s, -vDir.getOffsetX(), -vDir.getOffsetY(), -vDir.getOffsetZ());
                boolean down = connected(rule, state, pos, view, base, face, s, vDir.getOffsetX(), vDir.getOffsetY(), vDir.getOffsetZ());

                return tile(rule, CtmTables.LINEAR[(up ? 1 : 0) | (down ? 2 : 0)]);
            }

            case RANDOM: {
                int h = pos.getX() * 3129871 ^ pos.getZ() * 116129781 ^ pos.getY() * 42317861 ^ face.ordinal() * 7919;
                h = h * h * 42317861 + h * 11;
                int r = ((h >> 16) & 0x7FFF) % rule.totalWeight;

                for (int i = 0; i < rule.weights.length; i++) {
                    r -= rule.weights[i];

                    if (r < 0) {
                        return tile(rule, i);
                    }
                }

                return tile(rule, 0);
            }

            case REPEAT: {
                int cu = pos.getX() * uDir.getOffsetX() + pos.getY() * uDir.getOffsetY() + pos.getZ() * uDir.getOffsetZ();
                int cv = pos.getX() * vDir.getOffsetX() + pos.getY() * vDir.getOffsetY() + pos.getZ() * vDir.getOffsetZ();
                int x = Math.floorMod(cu, rule.width);
                int y = Math.floorMod(cv, rule.height);

                return tile(rule, y * rule.width + x);
            }

            case CTM:
            case GENERATED_GLASS: {
                int ux = uDir.getOffsetX(), uy = uDir.getOffsetY(), uz = uDir.getOffsetZ();
                int vx = vDir.getOffsetX(), vy = vDir.getOffsetY(), vz = vDir.getOffsetZ();

                // up = -V, down = +V, left = -U, right = +U
                boolean top = connected(rule, state, pos, view, base, face, s, -vx, -vy, -vz);
                boolean bottom = connected(rule, state, pos, view, base, face, s, vx, vy, vz);
                boolean left = connected(rule, state, pos, view, base, face, s, -ux, -uy, -uz);
                boolean right = connected(rule, state, pos, view, base, face, s, ux, uy, uz);

                boolean tr = top && right && connected(rule, state, pos, view, base, face, s, ux - vx, uy - vy, uz - vz);
                boolean br = bottom && right && connected(rule, state, pos, view, base, face, s, ux + vx, uy + vy, uz + vz);
                boolean bl = bottom && left && connected(rule, state, pos, view, base, face, s, -ux + vx, -uy + vy, -uz + vz);
                boolean tl = top && left && connected(rule, state, pos, view, base, face, s, -ux - vx, -uy - vy, -uz - vz);

                if (rule.method == CtmRule.Method.GENERATED_GLASS) {
                    int mask = GlassTileGenerator.canonicalMask(top, right, bottom, left, tr, br, bl, tl);

                    return rule.generated[mask];
                }

                return tile(rule, CtmTables.ctmTile(top, right, bottom, left, tr, br, bl, tl));
            }

            default:
                return null;
        }
    }

    private static Sprite tile(CtmRule rule, int index) {
        return index >= 0 && index < rule.tiles.length ? rule.tiles[index] : null;
    }

    private static boolean connected(CtmRule rule, BlockState self, BlockPos pos, BlockView view, Sprite base,
                                     Direction face, Scratch s, int dx, int dy, int dz) {
        BlockPos.Mutable n = s.pos;
        n.setPosition(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);

        BlockState other = view.getBlockState(n);

        switch (rule.connect) {
            case STATE:
                return other == self;

            case TILE:
                return sameTile(other, n, view, face, base) || glassBaseConnects(rule, other, base);

            case BLOCK:
            default:
                return other.getBlock() == self.getBlock();
        }
    }

    /**
     * Deterministic connectivity for the built-in glass rules, compared by block type /
     * stained color instead of the neighbor's model quads (pane models are composition
     * models whose per-direction quad lists are not reliable for this lookup).
     *
     * <p>Connects pane<i>⇄</i>pane, pane<i>⇄</i>glass and glass<i>⇄</i>glass, but only
     * when both share the same base texture (plain glass together; each stained color
     * stays with its own color).</p>
     */
    private static boolean glassBaseConnects(CtmRule rule, BlockState other, Sprite base) {
        // Builtin rules are named "builtin:<glass base>" ("glass", "glass_lime", ...).
        // A pack rule must never be lifted by this fallback.
        if (!rule.name.startsWith("builtin:glass") || base == null) {
            return false;
        }

        Block b = other.getBlock();

        if (!(b instanceof GlassBlock) && !(b instanceof PaneBlock)) {
            return false;
        }

        DyeColor color = null;

        if (b instanceof StainedGlassBlock) {
            color = other.get(StainedGlassBlock.COLOR);
        } else if (b instanceof StainedGlassPaneBlock) {
            color = other.get(StainedGlassPaneBlock.COLOR);
        }

        String otherBase = color == null ? "glass" : "glass_" + color.asString();

        return rule.name.regionMatches("builtin:".length(), otherBase, 0, otherBase.length());
    }

    private static boolean sameTile(BlockState other, BlockPos pos, BlockView view, Direction face, Sprite base) {
        try {
            BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(other, view, pos);

            if (model == null) {
                return false;
            }

            List<BakedQuad> quads = model.getByDirection(face);

            if (quads.isEmpty()) {
                return false;
            }

            Sprite sprite = ((BakedQuadView) quads.get(0)).getCtmSprite();

            return sprite == base;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // parsing helpers

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();

        for (String part : s.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }

        return out;
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty()) {
            return false;
        }

        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseRangeMask(String s, int size, int fallback) {
        if (s == null) {
            return fallback;
        }

        int mask = 0;

        for (String token : split(s.replace(',', ' '))) {
            int dash = token.indexOf('-');

            if (dash > 0) {
                int from = parseInt(token.substring(0, dash), 0);
                int to = parseInt(token.substring(dash + 1), size - 1);

                for (int i = from; i <= to && i < size; i++) {
                    mask |= 1 << i;
                }
            } else {
                int v = parseInt(token, -1);

                if (v >= 0 && v < size) {
                    mask |= 1 << v;
                }
            }
        }

        return mask == 0 ? fallback : mask;
    }

    private static int parseFaces(String s) {
        if (s == null) {
            return CtmRule.ALL_FACES;
        }

        int mask = 0;

        for (String token : split(s.replace(',', ' '))) {
            switch (token.toLowerCase()) {
                case "all": mask |= CtmRule.ALL_FACES; break;
                case "sides":
                    mask |= bit(Direction.NORTH) | bit(Direction.SOUTH) | bit(Direction.WEST) | bit(Direction.EAST);
                    break;
                case "top": case "up": mask |= bit(Direction.UP); break;
                case "bottom": case "down": mask |= bit(Direction.DOWN); break;
                case "north": mask |= bit(Direction.NORTH); break;
                case "south": mask |= bit(Direction.SOUTH); break;
                case "east": mask |= bit(Direction.EAST); break;
                case "west": mask |= bit(Direction.WEST); break;
                default: break;
            }
        }

        return mask == 0 ? CtmRule.ALL_FACES : mask;
    }

    private static int bit(Direction d) {
        return 1 << d.ordinal();
    }
}
