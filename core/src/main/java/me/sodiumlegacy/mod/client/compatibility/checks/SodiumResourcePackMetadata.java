package me.sodiumlegacy.mod.client.compatibility.checks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads additional metadata for Sodium from a resource pack's `pack.mcmeta` file. This allows the
 * resource pack author to specify which shaders from their pack are not usable with Sodium, but that
 * the author is aware of and is fine with being ignored.
 *
 * <p>Minecraft 1.8.9 has no registry for pack metadata section serializers (unlike 1.20.1), so the
 * `sodium` section is parsed directly from the `pack.mcmeta` JSON with Gson instead.</p>
 */
public final class SodiumResourcePackMetadata {
    private final List<String> ignoredShaders;

    public SodiumResourcePackMetadata(List<String> ignoredShaders) {
        this.ignoredShaders = ignoredShaders;
    }

    public List<String> ignoredShaders() {
        return this.ignoredShaders;
    }

    /**
     * Parses the Sodium metadata section out of the JSON document contained in a resource pack's
     * `pack.mcmeta` file. Returns {@code null} when the metadata is absent or malformed.
     *
     * <p>The expected layout is {@code {"sodiumlegacy": {"ignored_shaders": ["file.vsh", ...]}}} inside
     * the root of the `pack.mcmeta` JSON object.</p>
     */
    static @Nullable SodiumResourcePackMetadata fromJson(JsonObject packMetadata) {
        if (!packMetadata.has("sodiumlegacy")) {
            return null;
        }

        JsonElement sodiumElement = packMetadata.get("sodiumlegacy");

        if (!sodiumElement.isJsonObject()) {
            return null;
        }

        JsonObject sodium = sodiumElement.getAsJsonObject();

        List<String> ignoredShaders = new ArrayList<String>();

        if (sodium.has("ignored_shaders") && sodium.get("ignored_shaders").isJsonArray()) {
            JsonArray array = sodium.getAsJsonArray("ignored_shaders");

            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    ignoredShaders.add(element.getAsString());
                } else {
                    return null;
                }
            }
        }

        return new SodiumResourcePackMetadata(ignoredShaders);
    }
}
