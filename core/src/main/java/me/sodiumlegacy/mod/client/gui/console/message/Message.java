package me.sodiumlegacy.mod.client.gui.console.message;

/**
 * A console message. The text is a plain {@link String}; 1.8.9 has no {@code Text}
 * formatting pipeline on top of the font renderer, and the legacy font renderer
 * handles the legacy §-formatting codes natively.
 */
public final class Message {
    private final MessageLevel level;
    private final String text;
    private final double duration;

    public Message(MessageLevel level, String text, double duration) {
        this.level = level;
        this.text = text;
        this.duration = duration;
    }

    public MessageLevel level() {
        return this.level;
    }

    public String text() {
        return this.text;
    }

    public double duration() {
        return this.duration;
    }
}
