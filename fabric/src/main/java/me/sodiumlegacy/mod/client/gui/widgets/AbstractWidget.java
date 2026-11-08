package me.sodiumlegacy.mod.client.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.Identifier;

/**
 * Base class for all of Sodium's custom widgets. This class is deliberately independent
 * of the vanilla widget kit (our widgets draw themselves into rectangles rather than
 * using sprite-sheets), so it only provides shared state and drawing helpers that map
 * onto the 1.8.9 GUI API ({@link DrawableHelper#fill} and {@link TextRenderer}).
 */
public abstract class AbstractWidget {
    protected final TextRenderer font;
    protected boolean focused;
    protected boolean hovered;

    protected AbstractWidget() {
        this.font = MinecraftClient.getInstance().textRenderer;
    }

    /**
     * Renders the widget at the given mouse position (in screen coordinates).
     */
    public void render(int mouseX, int mouseY) {
    }

    public boolean isHovered() {
        return this.hovered;
    }

    public boolean isFocused() {
        return this.focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    /**
     * Called when the screen receives a mouse click. Returns true if this widget
     * handled the event.
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    /**
     * Called while a mouse button is held down. Returns true if this widget
     * handled the event.
     */
    public boolean mouseDragged(int mouseX, int mouseY, int button) {
        return false;
    }

    /**
     * Called when a key is pressed. Returns true if this widget handled the event.
     */
    public boolean keyPressed(int keyCode) {
        return false;
    }

    protected void drawString(String str, int x, int y, int color) {
        this.font.drawWithShadow(str, x, y, color);
    }

    protected void drawRect(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(x1, y1, x2, y2, color);
    }

    protected void drawBorder(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(x1, y1, x2, y1 + 1, color);
        DrawableHelper.fill(x1, y2 - 1, x2, y2, color);
        DrawableHelper.fill(x1, y1, x1 + 1, y2, color);
        DrawableHelper.fill(x2 - 1, y1, x2, y2, color);
    }

    protected int getStringWidth(String text) {
        return this.font.getStringWidth(text);
    }

    protected void playClickSound() {
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(new Identifier("gui.button.press"), 1.0F));
    }
}
