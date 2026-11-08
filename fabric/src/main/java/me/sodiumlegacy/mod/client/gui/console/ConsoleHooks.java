package me.sodiumlegacy.mod.client.gui.console;

public class ConsoleHooks {
    /**
     * Called after each HUD/render frame to update and draw the on-screen console.
     * The 1.20 signature took a {@code DrawContext} and used {@code GLFW.glfwGetTime()};
     * 1.8.9 has no DrawContext, and the caller (a mixin) must supply a monotonic
     * timestamp in seconds.
     */
    public static void render(double currentTime) {
        ConsoleRenderer.INSTANCE.update(Console.INSTANCE, currentTime);
        ConsoleRenderer.INSTANCE.draw(currentTime);
    }
}
