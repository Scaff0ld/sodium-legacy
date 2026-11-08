package me.sodiumlegacy.mod.client.gui.options;

import me.sodiumlegacy.mod.client.util.SodiumLang;
import net.minecraft.util.Formatting;

public enum OptionImpact implements TextProvider {
    LOW(Formatting.GREEN, "sodiumlegacy.option_impact.low"),
    MEDIUM(Formatting.YELLOW, "sodiumlegacy.option_impact.medium"),
    HIGH(Formatting.RED, "sodiumlegacy.option_impact.high"),
    VARIES(Formatting.GRAY, "sodiumlegacy.option_impact.varies");

    private final String text;

    OptionImpact(Formatting color, String textKey) {
        // The impact string is rendered (pre-colored) by its own enumeration so callers don't
        // have to repeat the color: low=green, medium=yellow, high=red, varies=gray.
        this.text = color.toString() + SodiumLang.translate(textKey);
    }

    @Override
    public String getLocalizedName() {
        return this.text;
    }
}
