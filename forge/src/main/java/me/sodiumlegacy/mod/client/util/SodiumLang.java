package me.sodiumlegacy.mod.client.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side translation lookup for sodium's own lang keys.
 *
 * <p>The 1.8.9 I18n pipeline loads its {@code .lang} files (en_US and the language packs)
 * directly from the game jar / resource packs, so it never sees the modern {@code lang/*.json}
 * assets of Fabric mods: every {@code sodium.*} key would be rendered as the raw key.
 * This class therefore loads sodium's own bundles from
 * {@code /assets/sodiumlegacy/lang/<code>.json} itself and delegates every other key
 * (vanilla {@code options.*}, {@code gui.*}, ...) to the vanilla I18n.</p>
 *
 * <p>The bundle is selected from the game's current language: any Spanish variant
 * ({@code es_ES}, {@code es_MX}, {@code es_AR}, {@code es_UY}, {@code es_VE}, {@code es_CL}, ...)
 * resolves to the Spanish bundle; everything else falls back to the English one.</p>
 */
public final class SodiumLang {
    private static final Gson GSON = new Gson();
    private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static final Map<String, Map<String, String>> BUNDLES = new ConcurrentHashMap<>();

    private static final Map<String, String> EMPTY = Collections.emptyMap();

    private SodiumLang() {
    }

    /**
     * Translates a lang key, formatting it with the given arguments when one is provided.
     * Keys missing from sodium's own bundles (i.e. vanilla keys) fall back to I18n.
     */
    public static String translate(String key, Object... args) {
        String text = lookup(key);

        if (text == null) {
            return I18n.format(key, args);
        }

        return args.length == 0 ? text : String.format(text, args);
    }

    /**
     * Same as {@link #translate} but returns the given English fallback (instead of I18n,
     * which would render the raw key) when the lookup fails; used by the compatibility
     * scanners which run before the language is guaranteed to be available.
     */
    public static String translateOr(String key, String fallback) {
        String text = lookup(key);

        return text != null ? text : fallback;
    }

    /**
     * Raw bundle lookup, used by the I18n hook: returns null when the key is not one of
     * sodium's own translations, so the caller (vanilla I18n) can fall back normally.
     */
    public static String lookupOrNull(String key) {
        return lookup(key);
    }

    private static String lookup(String key) {
        Map<String, String> bundle = getBundle(bundleName());

        String text = bundle.get(key);

        // Fall back to the English bundle when the selected language has no entry.
        if (text == null) {
            text = getBundle("en_us").get(key);
        }

        return text;
    }

    private static Map<String, String> getBundle(String name) {
        return BUNDLES.computeIfAbsent(name, SodiumLang::loadBundle);
    }

    private static String bundleName() {
        String language = currentLanguageCode().toLowerCase(Locale.ROOT);

        return language.startsWith("es") ? "es_es" : "en_us";
    }

    private static String currentLanguageCode() {
        try {
            Minecraft client = Minecraft.getMinecraft();

            if (client != null && client.gameSettings != null) {
                return client.gameSettings.language;
            }
        } catch (Throwable ignored) {
        }

        return "en_US";
    }

    private static Map<String, String> loadBundle(String name) {
        String path = "/assets/sodiumlegacy/lang/" + name + ".json";

        try (InputStream stream = SodiumLang.class.getResourceAsStream(path)) {
            if (stream == null) {
                return EMPTY;
            }

            Map<String, String> map = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), STRING_MAP_TYPE);

            return map != null ? map : EMPTY;
        } catch (Throwable t) {
            // Never fail the caller: a broken bundle just means untranslated text.
            return EMPTY;
        }
    }
}

