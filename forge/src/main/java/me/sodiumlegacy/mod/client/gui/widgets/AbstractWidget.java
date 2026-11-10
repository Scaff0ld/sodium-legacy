package me.sodiumlegacy.mod.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

/**
 * Base class for all of Sodium's custom widgets. This class is deliberately independent
 * of the vanilla widget kit (our widgets draw themselves into rectangles rather than
 * using sprite-sheets), so it only provides shared state and drawing helpers that map
 * onto the 1.8.9 GUI API ({@link Gui#fill} and {@link FontRenderer}).
 */
public abstract class AbstractWidget {
    protected final FontRenderer font;
    protected boolean focused;
    protected boolean hovered;

    protected AbstractWidget() {
        this.font = Minecraft.getMinecraft().fontRendererObj;
    }

    /**
     * Renders the widget at the given mouse position (in GuiScreen coordinates).
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
     * Called when the GuiScreen receives a mouse click. Returns true if this widget
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
        this.font.drawStringWithShadow(str, x, y, color);
    }

    protected void drawRect(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
    }

    protected void drawBorder(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y1 + 1, color);
        Gui.drawRect(x1, y2 - 1, x2, y2, color);
        Gui.drawRect(x1, y1, x1 + 1, y2, color);
        Gui.drawRect(x2 - 1, y1, x2, y2, color);
    }

    protected int getStringWidth(String text) {
        return this.font.getStringWidth(text);
    }

    /**
     * Called before dispatching a new click, so widgets can end any ongoing drag
     * (e.g. a slider thumb still held from a previous click elsewhere).
     */
    public void resetDrag() {
    }

    protected void playClickSound() {
        Minecraft.getMinecraft().getSoundHandler()
                .playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }
}
