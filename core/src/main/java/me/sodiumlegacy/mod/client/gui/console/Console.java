package me.sodiumlegacy.mod.client.gui.console;

import me.sodiumlegacy.mod.client.gui.console.message.Message;
import me.sodiumlegacy.mod.client.gui.console.message.MessageLevel;
import org.apache.commons.lang3.Validate;

import java.util.ArrayDeque;
import java.util.Deque;

public class Console implements ConsoleSink {
    static final Console INSTANCE = new Console();

    private final ArrayDeque<Message> messages = new ArrayDeque<>();

    @Override
    public void logMessage(MessageLevel level, String text, double duration) {
        Validate.notNull(level);
        Validate.notNull(text);

        this.messages.addLast(new Message(level, text, duration));
    }

    public Deque<Message> getMessageDrain() {
        return this.messages;
    }

    public static ConsoleSink instance() {
        return INSTANCE;
    }
}
