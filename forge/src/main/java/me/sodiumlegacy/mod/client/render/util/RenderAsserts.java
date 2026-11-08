package me.sodiumlegacy.mod.client.render.util;

import net.minecraft.client.Minecraft;

public class RenderAsserts {
    /**
     * Checks that the thread calling this function is the main render thread. This is useful for ensuring that OpenGL
     * APIs are not accessed from off-thread incorrectly, which is known to cause severe issues.
     *
     * <p>1.8.9 has no {@code RenderSystem.isOnRenderThread()}; the client thread is the render thread there.</p>
     *
     * @throws IllegalStateException If the current thread is not the main render thread
     * @return Always true, since an exception is thrown otherwise
     */
    public static boolean validateCurrentThread() {
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Accessing OpenGL functions from outside the main render thread is not supported when using Sodium");
        }

        return true;
    }
}

