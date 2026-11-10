package me.sodiumlegacy.mod.client.gui.options.control;

import me.sodiumlegacy.mod.client.gui.options.Option;
import me.sodiumlegacy.mod.client.gui.widgets.AbstractWidget;
import me.sodiumlegacy.mod.client.util.Dim2i;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.util.Formatting;
import org.lwjgl.opengl.GL11;

public class ControlElement<T> extends AbstractWidget {
    // Delay before the name starts scrolling while hovered, and speed (px/s) of the scroll.
    private static final float SCROLL_DELAY_S = 0.4F;
    private static final float SCROLL_SPEED = 30.0F;

    protected final Option<T> option;

    protected final Dim2i dim;

    private long hoverStartMs;
    private boolean wasHovered;

    public ControlElement(Option<T> option, Dim2i dim) {
        this.option = option;
        this.dim = dim;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        this.hovered = this.dim.containsCursor(mouseX, mouseY);

        // Restart the hover scroll whenever the mouse enters this element again.
        if (this.hovered && !this.wasHovered) {
            this.hoverStartMs = System.currentTimeMillis();
        }
        this.wasHovered = this.hovered;

        String name = this.option.getName();
        String label;

        final int maxNameWidth = this.dim.width() - this.option.getControl().getMaxWidth() - 12;
        final int textX = this.dim.x() + 6;
        final int textY = this.dim.getCenterY() - 4;

        boolean scrollName = false;
        int scrollOffset = 0;

        if (this.getStringWidth(name) > maxNameWidth) {
            if (this.hovered) {
                // While hovered, slide the name leftwards (clipped to the name column)
                // so the full text can be read; the scroll resets on the next hover.
                float elapsed = (System.currentTimeMillis() - this.hoverStartMs) / 1000.0F;
                scrollOffset = (int) (Math.max(0.0F, elapsed - SCROLL_DELAY_S) * SCROLL_SPEED);
                scrollOffset = Math.min(scrollOffset, this.getStringWidth(name) - maxNameWidth);
                scrollName = true;
            } else {
                // Otherwise trim the name so it never overlaps the control widget itself.
                while (name.length() > 1 && this.getStringWidth(name + "...") > maxNameWidth) {
                    name = name.substring(0, name.length() - 1);
                }

                name = name.trim() + "...";
            }
        }

        if (this.option.isAvailable()) {
            if (this.option.hasChanged()) {
                label = Formatting.ITALIC + name + " *";
            } else {
                label = Formatting.WHITE + name;
            }
        } else {
            label = String.valueOf(Formatting.GRAY) + Formatting.STRIKETHROUGH + name;
        }

        this.drawRect(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), this.hovered ? 0xE0000000 : 0x90000000);

        if (scrollName) {
            int clipX = this.dim.x() + 5;
            int clipY = this.dim.y() + 2;
            int clipWidth = this.dim.width() - this.option.getControl().getMaxWidth() - 9;

            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            this.applyScissor(clipX, clipY, clipWidth, 14);
            this.drawString(label, textX - scrollOffset, textY, 0xFFFFFFFF);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            this.drawString(label, textX, textY, 0xFFFFFFFF);
        }

        if (this.isFocused()) {
            this.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), -1);
        }
    }

    /**
     * Sets up an OpenGL scissor box from GUI coordinates. GL scissor expects
     * framebuffer pixels with the Y axis starting from the bottom, so the given
     * rectangle is rescaled (and flipped vertically) accordingly.
     */
    private void applyScissor(int x, int y, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = new Window(client);

        float scale = (float) client.width / window.getWidth();

        GL11.glScissor(
                (int) (x * scale),
                (int) (client.height - (y + height) * scale),
                (int) (width * scale),
                (int) (height * scale));
    }

    public Option<T> getOption() {
        return this.option;
    }

    public Dim2i getDimensions() {
        return this.dim;
    }
}
