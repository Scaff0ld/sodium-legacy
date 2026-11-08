package me.sodiumlegacy.mod.client.gui.options.control;

import me.sodiumlegacy.mod.client.gui.options.Option;
import me.sodiumlegacy.mod.client.gui.options.TextProvider;
import me.sodiumlegacy.mod.client.util.Dim2i;
import net.minecraft.client.gui.screen.Screen;
import org.apache.commons.lang3.Validate;
import org.lwjgl.input.Keyboard;

public class CyclingControl<T extends Enum<T>> implements Control<T> {
    private final Option<T> option;
    private final T[] allowedValues;
    private final String[] names;
    private final boolean useEnumOrdinal;

    public CyclingControl(Option<T> option, Class<T> enumType) {
        this(option, enumType, enumType.getEnumConstants());
    }

    public CyclingControl(Option<T> option, Class<T> enumType, String[] names) {
        T[] universe = enumType.getEnumConstants();

        Validate.isTrue(universe.length == names.length, "Mismatch between universe length and names array length");
        Validate.notEmpty(universe, "The enum universe must contain at least one item");

        this.option = option;
        this.allowedValues = universe;
        this.names = names;
        this.useEnumOrdinal = true;
    }

    public CyclingControl(Option<T> option, Class<T> enumType, T[] allowedValues) {
        this.option = option;
        this.allowedValues = allowedValues;

        // NOTE: labels must be built from the allowedValues array itself, NOT from the enum
        // universe: enumerating the universe desynchronizes labels from values when the allowed
        // array is a subset (it made "Fast" display as "Default" for the graphics control).
        this.names = new String[allowedValues.length];

        for (int i = 0; i < this.names.length; i++) {
            T value = allowedValues[i];
            String name;

            if (value instanceof TextProvider) {
                name = ((TextProvider) value).getLocalizedName();
            } else {
                name = value.name();
            }

            this.names[i] = name;
        }

        this.useEnumOrdinal = false;
    }

    @Override
    public Option<T> getOption() {
        return this.option;
    }

    @Override
    public ControlElement<T> createElement(Dim2i dim) {
        return new CyclingControlElement<T>(this.option, dim, this.allowedValues, this.names, this.useEnumOrdinal);
    }

    @Override
    public int getMaxWidth() {
        return 70;
    }

    private static class CyclingControlElement<T extends Enum<T>> extends ControlElement<T> {
        private final T[] allowedValues;
        private final String[] names;
        private final boolean useEnumOrdinal;
        private int currentIndex;

        public CyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, String[] names, boolean useEnumOrdinal) {
            super(option, dim);

            this.allowedValues = allowedValues;
            this.names = names;
            this.useEnumOrdinal = useEnumOrdinal;
            this.currentIndex = this.indexForValue(option.getValue());

            if (this.currentIndex == -1) {
                this.currentIndex = 0;
            }
        }

        private int indexForValue(T value) {
            if (this.useEnumOrdinal && value != null && value.ordinal() < this.names.length) {
                return value.ordinal();
            }

            for (int i = 0; i < this.allowedValues.length; i++) {
                if (this.allowedValues[i] == value) {
                    return i;
                }
            }

            return -1;
        }

        @Override
        public void render(int mouseX, int mouseY) {
            super.render(mouseX, mouseY);

            String name = this.names[this.currentIndex];

            int strWidth = this.getStringWidth(name);
            this.drawString(name, this.dim.getLimitX() - strWidth - 6, this.dim.getCenterY() - 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (this.option.isAvailable() && button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
                cycleControl(Screen.hasShiftDown());
                this.playClickSound();

                return true;
            }

            return false;
        }

        @Override
        public boolean keyPressed(int keyCode) {
            if (!isFocused()) return false;

            if (keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                cycleControl(Screen.hasShiftDown());
                return true;
            }

            return false;
        }

        public void cycleControl(boolean reverse) {
            if (reverse) {
                this.currentIndex = (this.currentIndex + this.allowedValues.length - 1) % this.allowedValues.length;
            } else {
                this.currentIndex = (this.currentIndex + 1) % this.allowedValues.length;
            }
            this.option.setValue(this.allowedValues[this.currentIndex]);
        }
    }
}
