package me.sodiumlegacy.mod.client.util;

import me.sodiumlegacy.mod.platform.ModPlatform;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implements the "Flawless Frames" FREX feature using which third-party mods can instruct Sodium to sacrifice
 * performance (even beyond the point where it can no longer achieve interactive frame rates) in exchange for
 * a noticeable boost to quality.
 *
 * In Sodium's case, this means waiting for all chunks to be fully updated and ready for rendering before each frame.
 *
 * See https://github.com/grondag/frex/pull/9
 */
public class FlawlessFrames {
    private static final Set<Object> ACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void onClientInitialization() {
        Function<String, Consumer<Boolean>> provider = new ProviderFactory();

        for (Object entrypoint : ModPlatform.get().getFrexFlawlessFramesEntrypoints()) {
            try {
                Consumer<Function<String, Consumer<Boolean>>> api =
                        (Consumer<Function<String, Consumer<Boolean>>>) entrypoint;
                api.accept(provider);
            } catch (Exception e) {
                // Do not make initialization fail if the FREX entrypoints could not be loaded (this is an
                // optional feature which no hard dependency is declared for)
            }
        }
    }

    private static final class ProviderFactory implements Function<String, Consumer<Boolean>> {
        @Override
        public Consumer<Boolean> apply(String name) {
            final Object token = new Object();

            return active -> {
                if (active) {
                    ACTIVE.add(token);
                } else {
                    ACTIVE.remove(token);
                }
            };
        }
    }

    public static boolean isActive() {
        return !ACTIVE.isEmpty();
    }
}
