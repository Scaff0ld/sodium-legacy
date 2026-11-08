package me.sodiumlegacy.mod.client.gui.options;

import me.sodiumlegacy.mod.client.util.SodiumLang;
import net.minecraft.util.EnumChatFormatting;

public enum OptionImpact implements TextProvider {
    LOW(EnumChatFormatting.GREEN, "sodiumlegacy.option_impact.low"),
    MEDIUM(EnumChatFormatting.YELLOW, "sodiumlegacy.option_impact.medium"),
    HIGH(EnumChatFormatting.RED, "sodiumlegacy.option_impact.high"),
    VARIES(EnumChatFormatting.GRAY, "sodiumlegacy.option_impact.varies");

    private final String text;

    OptionImpact(EnumChatFormatting color, String textKey) {
        // The impact string is rendered (pre-colored) by its own enumeration so callers don't
        // have to repeat the color: low=green, medium=yellow, high=red, varies=gray.
        this.text = color.toString() + SodiumLang.translate(textKey);
    }

    @Override
    public String getLocalizedName() {
        return this.text;
    }
}
