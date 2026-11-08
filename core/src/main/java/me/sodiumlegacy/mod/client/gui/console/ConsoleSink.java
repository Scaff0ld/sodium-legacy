package me.sodiumlegacy.mod.client.gui.console;

import me.sodiumlegacy.mod.client.gui.console.message.MessageLevel;

public interface ConsoleSink {
    void logMessage(MessageLevel level, String text, double duration);
}
