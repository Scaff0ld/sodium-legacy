package me.sodiumlegacy.mod.client.gui.options.storage;

public interface OptionStorage<T> {
    T getData();

    void save();
}
