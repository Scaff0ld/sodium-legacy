package me.sodiumlegacy.mod.client.gui.options.control;
import me.sodiumlegacy.mod.client.util.SodiumLang;


public interface ControlValueFormatter {
    static ControlValueFormatter guiScale() {
        return new ControlValueFormatter() {
            @Override
            public String format(int value) {
                return (value == 0) ? SodiumLang.translate("options.guiScale.auto") : value + "x";
            }
        };
    }

    static ControlValueFormatter fpsLimit() {
        return new ControlValueFormatter() {
            @Override
            public String format(int value) {
                return (value == 260) ? SodiumLang.translate("options.framerateLimit.max") : value + " fps";
            }
        };
    }

    static ControlValueFormatter brightness() {
        return new ControlValueFormatter() {
            @Override
            public String format(int value) {
                if (value == 0) {
                    return SodiumLang.translate("options.gamma.min");
                } else if (value == 100) {
                    return SodiumLang.translate("options.gamma.max");
                } else {
                    return value + "%";
                }
            }
        };
    }

    static ControlValueFormatter biomeBlend() {
        return new ControlValueFormatter() {
            @Override
            public String format(int value) {
                return (value == 0) ? SodiumLang.translate("gui.none") : SodiumLang.translate("sodiumlegacy.options.biome_blend.value", value);
            }
        };
    }

    String format(int value);

    static ControlValueFormatter translateVariable(String key) {
        return new ControlValueFormatter() {
            @Override
            public String format(int value) {
                return SodiumLang.translate(key, value);
            }
        };
    }

    static ControlValueFormatter percentage() {
        return new ControlValueFormatter() {
            @Override
            public String format(int value) {
                return value + "%";
            }
        };
    }

    static ControlValueFormatter multiplier() {
        return new ControlValueFormatter() {
            @Override
            public String format(int value) {
                return value + "x";
            }
        };
    }

    static ControlValueFormatter quantityOrDisabled(String name, String disableText) {
        return new ControlValueFormatter() {
            @Override
            public String format(int value) {
                return value == 0 ? disableText : value + " " + name;
            }
        };
    }

    static ControlValueFormatter number() {
        return new ControlValueFormatter() {
            @Override
            public String format(int value) {
                return String.valueOf(value);
            }
        };
    }
}
