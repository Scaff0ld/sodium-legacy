package me.sodiumlegacy.mod.client.gui.prompt;

import me.sodiumlegacy.mod.client.gui.widgets.AbstractWidget;
import me.sodiumlegacy.mod.client.gui.widgets.FlatButtonWidget;
import me.sodiumlegacy.mod.client.util.Dim2i;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class ScreenPrompt {
    private final ScreenPromptable parent;
    private final List<String> text;

    private final Action action;

    private FlatButtonWidget closeButton, actionButton;

    private final int width, height;

    public ScreenPrompt(ScreenPromptable parent, List<String> text, int width, int height, Action action) {
        this.parent = parent;
        this.text = text;

        this.width = width;
        this.height = height;

        this.action = action;
    }

    public void init() {
        Dim2i parentDimensions = this.parent.getDimensions();

        int boxX = (parentDimensions.width() / 2) - (width / 2);
        int boxY = (parentDimensions.height() / 2) - (height / 2);

        this.closeButton = new FlatButtonWidget(new Dim2i((boxX + width) - 84, (boxY + height) - 24, 80, 20), "Close", this::close);
        this.closeButton.setStyle(createButtonStyle());

        this.actionButton = new FlatButtonWidget(new Dim2i((boxX + width) - 198, (boxY + height) - 24, 110, 20), this.action.label, this::runAction);
        this.actionButton.setStyle(createButtonStyle());
    }

    public void render(int mouseX, int mouseY) {
        GL11.glPushMatrix();
        // The overlay GUI matrix is translated by -2000 in Z with a clip range of
        // [-3000, -1000], so raise the whole prompt by 300 to draw over the screen.
        GL11.glTranslatef(0.0F, 0.0F, 300.0F);

        Dim2i parentDimensions = this.parent.getDimensions();

        DrawableHelper.fill(0, 0, parentDimensions.width(), parentDimensions.height(), 0x70090909);

        GL11.glTranslatef(0.0F, 0.0F, 50.0F);

        int boxX = (parentDimensions.width() / 2) - (width / 2);
        int boxY = (parentDimensions.height() / 2) - (height / 2);

        DrawableHelper.fill(boxX, boxY, boxX + width, boxY + height, 0xFF171717);
        drawBorderHelper(boxX, boxY, boxX + width, boxY + height, 0xFF121212);

        GL11.glTranslatef(0.0F, 0.0F, 50.0F);

        int padding = 5;

        int textX = boxX + padding;
        int textY = boxY + padding;

        int textMaxWidth = width - (padding * 2);

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        for (String paragraph : this.text) {
            List<String> formatted = textRenderer.wrapLines(paragraph, textMaxWidth);

            for (String line : formatted) {
                textRenderer.drawWithShadow(line, textX, textY, 0xFFFFFFFF);
                textY += textRenderer.fontHeight + 2;
            }

            textY += 8;
        }

        for (AbstractWidget button : getWidgets()) {
            button.render(mouseX, mouseY);
        }

        GL11.glPopMatrix();
    }

    private static void drawBorderHelper(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(x1, y1, x2, y1 + 1, color);
        DrawableHelper.fill(x1, y2 - 1, x2, y2, color);
        DrawableHelper.fill(x1, y1, x1 + 1, y2, color);
        DrawableHelper.fill(x2 - 1, y1, x2, y2, color);
    }

    private static FlatButtonWidget.Style createButtonStyle() {
        FlatButtonWidget.Style style = new FlatButtonWidget.Style();
        style.bgDefault = 0xff2b2b2b;
        style.bgHovered = 0xff393939;
        style.bgDisabled = style.bgDefault;

        style.textDefault = 0xFFFFFFFF;
        style.textDisabled = style.textDefault;

        return style;
    }

    public List<AbstractWidget> getWidgets() {
        return java.util.Arrays.asList(this.actionButton, this.closeButton);
    }

    public void setFocused(boolean focused) {
        if (focused) {
            this.parent.setPrompt(this);
        } else {
            this.parent.setPrompt(null);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        for (AbstractWidget widget : this.getWidgets()) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.close();
            return true;
        }

        return false;
    }

    public boolean isFocused() {
        return this.parent.getPrompt() == this;
    }

    private void close() {
        this.parent.setPrompt(null);
    }

    private void runAction() {
        this.action.runnable.run();
        this.close();
    }

    public static final class Action {
        private final String label;
        private final Runnable runnable;

        public Action(String label, Runnable runnable) {
            this.label = label;
            this.runnable = runnable;
        }

        public String label() {
            return this.label;
        }

        public Runnable runnable() {
            return this.runnable;
        }
    }
}


