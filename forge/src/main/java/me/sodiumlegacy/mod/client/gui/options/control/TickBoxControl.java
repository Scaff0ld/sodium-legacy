package me.sodiumlegacy.mod.client.gui.options.control;

import me.sodiumlegacy.mod.client.gui.options.Option;
import me.sodiumlegacy.mod.client.util.Dim2i;
import org.lwjgl.input.Keyboard;

public class TickBoxControl implements Control<Boolean> {
    private final Option<Boolean> option;

    public TickBoxControl(Option<Boolean> option) {
        this.option = option;
    }

    @Override
    public ControlElement<Boolean> createElement(Dim2i dim) {
        return new TickBoxControlElement(this.option, dim);
    }

    @Override
    public int getMaxWidth() {
        return 30;
    }

    @Override
    public Option<Boolean> getOption() {
        return this.option;
    }

    private static class TickBoxControlElement extends ControlElement<Boolean> {
        private final Dim2i button;

        public TickBoxControlElement(Option<Boolean> option, Dim2i dim) {
            super(option, dim);

            this.button = new Dim2i(dim.getLimitX() - 16, dim.getCenterY() - 5, 10, 10);
        }

        @Override
        public void render(int mouseX, int mouseY) {
            super.render(mouseX, mouseY);

            final int x = this.button.x();
            final int y = this.button.y();
            final int w = x + this.button.width();
            final int h = y + this.button.height();

            final boolean enabled = this.option.isAvailable();
            final boolean ticked = enabled && this.option.getValue();

            final int color;

            if (enabled) {
                color = ticked ? 0xFF94E4D3 : 0xFFFFFFFF;
            } else {
                color = 0xFFAAAAAA;
            }

            if (ticked) {
                this.drawRect(x + 2, y + 2, w - 2, h - 2, color);
            }

            this.drawBorder(x, y, w, h, color);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (this.option.isAvailable() && button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
                toggleControl();
                this.playClickSound();

                return true;
            }

            return false;
        }

        @Override
        public boolean keyPressed(int keyCode) {
            if (!isFocused()) return false;

            if (keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                toggleControl();
                this.playClickSound();

                return true;
            }

            return false;
        }

        public void toggleControl() {
            this.option.setValue(!this.option.getValue());
        }
    }
}
