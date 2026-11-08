package me.sodiumlegacy.mod.client.gui.options.control;

import me.sodiumlegacy.mod.client.gui.options.Option;
import me.sodiumlegacy.mod.client.gui.widgets.AbstractWidget;
import me.sodiumlegacy.mod.client.util.Dim2i;
import net.minecraft.util.EnumChatFormatting;

public class ControlElement<T> extends AbstractWidget {
    protected final Option<T> option;

    protected final Dim2i dim;

    public ControlElement(Option<T> option, Dim2i dim) {
        this.option = option;
        this.dim = dim;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        String name = this.option.getName();
        String label;

        if ((this.hovered || this.isFocused()) && this.getStringWidth(name) > (this.dim.width() - this.option.getControl().getMaxWidth())) {
            name = name.substring(0, Math.min(name.length(), 10)) + "...";
        }

        if (this.option.isAvailable()) {
            if (this.option.hasChanged()) {
                label = EnumChatFormatting.ITALIC + name + " *";
            } else {
                label = EnumChatFormatting.WHITE + name;
            }
        } else {
            label = String.valueOf(EnumChatFormatting.GRAY) + EnumChatFormatting.STRIKETHROUGH + name;
        }

        this.hovered = this.dim.containsCursor(mouseX, mouseY);

        this.drawRect(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), this.hovered ? 0xE0000000 : 0x90000000);
        this.drawString(label, this.dim.x() + 6, this.dim.getCenterY() - 4, 0xFFFFFFFF);

        if (this.isFocused()) {
            this.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), -1);
        }
    }

    public Option<T> getOption() {
        return this.option;
    }

    public Dim2i getDimensions() {
        return this.dim;
    }
}
