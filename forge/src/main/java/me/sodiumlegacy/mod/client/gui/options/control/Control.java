package me.sodiumlegacy.mod.client.gui.options.control;

import me.sodiumlegacy.mod.client.gui.options.Option;
import me.sodiumlegacy.mod.client.util.Dim2i;

public interface Control<T> {
    Option<T> getOption();

    ControlElement<T> createElement(Dim2i dim);

    int getMaxWidth();
}
