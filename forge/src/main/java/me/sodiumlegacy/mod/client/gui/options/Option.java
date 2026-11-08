package me.sodiumlegacy.mod.client.gui.options;

import me.sodiumlegacy.mod.client.gui.options.control.Control;
import me.sodiumlegacy.mod.client.gui.options.storage.OptionStorage;

import java.util.Collection;

public interface Option<T> {
    String getName();

    String getTooltip();

    OptionImpact getImpact();

    Control<T> getControl();

    T getValue();

    void setValue(T value);

    void reset();

    OptionStorage<?> getStorage();

    boolean isAvailable();

    boolean hasChanged();

    void applyChanges();

    Collection<OptionFlag> getFlags();
}
