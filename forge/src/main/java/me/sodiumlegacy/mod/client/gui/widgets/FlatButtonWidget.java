package me.sodiumlegacy.mod.client.gui.widgets;

import me.sodiumlegacy.mod.client.util.Dim2i;

import java.util.Objects;

public class FlatButtonWidget extends AbstractWidget {
    private final Dim2i dim;
    private final Runnable action;

    private Style style = Style.defaults();

    private boolean selected;
    private boolean enabled = true;
    private boolean visible = true;

    private String label;

    public FlatButtonWidget(Dim2i dim, String label, Runnable action) {
        this.dim = dim;
        this.label = label;
        this.action = action;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }

        this.hovered = this.dim.containsCursor(mouseX, mouseY);

        int backgroundColor = this.enabled ? (this.hovered ? this.style.bgHovered : this.style.bgDefault) : this.style.bgDisabled;
        int textColor = this.enabled ? this.style.textDefault : this.style.textDisabled;

        int strWidth = this.getStringWidth(this.label);

        this.drawRect(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), backgroundColor);
        this.drawString(this.label, this.dim.getCenterX() - (strWidth / 2), this.dim.getCenterY() - 4, textColor);

        if (this.enabled && this.selected) {
            this.drawRect(this.dim.x(), this.dim.getLimitY() - 1, this.dim.getLimitX(), this.dim.getLimitY(), 0xFF94E4D3);
        }
        if (this.enabled && this.isFocused()) {
            this.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), -1);
        }
    }

    public void setStyle(Style style) {
        Objects.requireNonNull(style);

        this.style = style;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!this.enabled || !this.visible) {
            return false;
        }

        if (button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
            doAction();

            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (!this.isFocused())
            return false;

        if (isToggleKeyCode(keyCode)) {
            doAction();
            return true;
        }

        return false;
    }

    /**
     * Replacement for the 1.20+ {@code KeyCodes.isToggle} helper. On 1.8.9 keyboard
     * input uses LWJGL 2 codes: space, enter or the numpad enter key.
     */
    private static boolean isToggleKeyCode(int keyCode) {
        return keyCode == org.lwjgl.input.Keyboard.KEY_SPACE
                || keyCode == org.lwjgl.input.Keyboard.KEY_RETURN
                || keyCode == org.lwjgl.input.Keyboard.KEY_NUMPADENTER;
    }

    private void doAction() {
        this.action.run();
        this.playClickSound();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setLabel(String text) {
        this.label = text;
    }

    public String getLabel() {
        return this.label;
    }

    public static class Style {
        public int bgHovered, bgDefault, bgDisabled;
        public int textDefault, textDisabled;

        public static Style defaults() {
            Style style = new Style();
            style.bgHovered = 0xE0000000;
            style.bgDefault = 0x90000000;
            style.bgDisabled = 0x60000000;
            style.textDefault = 0xFFFFFFFF;
            style.textDisabled = 0x90FFFFFF;

            return style;
        }
    }
}
