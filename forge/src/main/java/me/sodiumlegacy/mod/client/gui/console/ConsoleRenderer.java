package me.sodiumlegacy.mod.client.gui.console;

import me.sodiumlegacy.mod.client.gui.console.message.Message;
import me.sodiumlegacy.mod.client.gui.console.message.MessageLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Draws the on-GuiScreen console messages over the HUD.
 *
 * 1.20's {@code DrawContext}/{@code TextHandler} pipeline does not exist on 1.8.9;
 * drawing happens directly through {@link Gui#fill} and the legacy
 * {@link FontRenderer}, wrapped in GL matrix transforms to lift the drawing above
 * the HUD layer.
 */
public class ConsoleRenderer {
    static final ConsoleRenderer INSTANCE = new ConsoleRenderer();

    private final LinkedList<ActiveMessage> activeMessages = new LinkedList<>();

    public void update(Console console, double currentTime) {
        this.purgeMessages(currentTime);
        this.pollMessages(console, currentTime);
    }

    private void purgeMessages(double currentTime) {
        this.activeMessages.removeIf(message ->
                currentTime > message.timestamp() + message.duration());
    }

    private void pollMessages(Console console, double currentTime) {
        Deque<Message> log = console.getMessageDrain();

        while (!log.isEmpty()) {
            this.activeMessages.add(ActiveMessage.create(log.poll(), currentTime));
        }
    }

    public void draw(double currentTime) {
        Minecraft client = Minecraft.getMinecraft();

        int paddingWidth = 3;
        int paddingHeight = 1;

        ArrayList<MessageRender> renders = new ArrayList<>();

        {
            int x = 4;
            int y = 4;

            for (ActiveMessage message : this.activeMessages) {
                double opacity = getMessageOpacity(message, currentTime);

                if (opacity < 0.025D) {
                    continue;
                }

                List<String> lines = new ArrayList<>();

                int messageWidth = 270;

                lines.addAll(client.fontRendererObj.listFormattedStringToWidth(message.text(), messageWidth - 20));

                int messageHeight = (client.fontRendererObj.FONT_HEIGHT * lines.size()) + (paddingHeight * 2);

                renders.add(new MessageRender(x, y, messageWidth, messageHeight, message.level(), lines, opacity));

                y += messageHeight;
            }
        }

        // Compute the mouse position in scaled GUI coordinates.
        // Compute the mouse position in scaled GUI coordinates.
        ScaledResolution scaled = new ScaledResolution(client);
        int mouseX = Mouse.getX() * scaled.getScaledWidth() / client.displayWidth;
        int mouseY = scaled.getScaledHeight() - Mouse.getY() * scaled.getScaledHeight() / client.displayHeight - 1;

        boolean hovered = false;

        for (MessageRender render : renders) {
            if (mouseX >= render.x && mouseX < render.x + render.width && mouseY >= render.y && mouseY < render.y + render.height) {
                hovered = true;
                break;
            }
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, 0.0F, 400.0F);

        for (MessageRender render : renders) {
            int x = render.x();
            int y = render.y();

            int width = render.width();
            int height = render.height();

            ColorPalette colors = COLORS.get(render.level());
            double opacity = render.opacity();

            if (hovered) {
                opacity *= 0.4D;
            }

            // message background
            Gui.drawRect(x, y, x + width, y + height,
                    ColorARGB.withAlpha(colors.background(), weightAlpha(opacity)));

            // message colored stripe
            Gui.drawRect(x, y, x + 1, y + height,
                    ColorARGB.withAlpha(colors.foreground(), weightAlpha(opacity)));

            FontRenderer FontRenderer = client.fontRendererObj;

            for (String line : render.lines()) {
                // message text
                FontRenderer.drawString(line, x + paddingWidth + 3, y + paddingHeight,
                        ColorARGB.withAlpha(colors.text(), weightAlpha(opacity)));

                y += FontRenderer.FONT_HEIGHT;
            }
        }

        GL11.glPopMatrix();
    }

    private static double getMessageOpacity(ActiveMessage message, double time) {
        double midpoint = message.timestamp() + (message.duration() / 2.0D);

        if (time > midpoint) {
            return getFadeOutOpacity(message, time);
        } else if (time < midpoint) {
            return getFadeInOpacity(message, time);
        } else {
            return 1.0D;
        }
    }

    private static double getFadeInOpacity(ActiveMessage message, double time) {
        double animationDuration = 0.25D;

        double animationStart = message.timestamp();
        double animationEnd = message.timestamp() + animationDuration;

        return getAnimationProgress(time, animationStart, animationEnd);
    }

    private static double getFadeOutOpacity(ActiveMessage message, double time) {
        // animation duration is 1/5th the message's duration, or 0.5 seconds, whichever is smaller
        double animationDuration = Math.min(0.5D, message.duration() * 0.20D);

        double animationStart = message.timestamp() + message.duration() - animationDuration;
        double animationEnd = message.timestamp() + message.duration();

        return 1.0D - getAnimationProgress(time, animationStart, animationEnd);
    }

    private static double getAnimationProgress(double currentTime, double startTime, double endTime) {
        // Replacement for 1.20's MathHelper.getLerpProgress
        if (currentTime < startTime) {
            return 0.0D;
        } else if (currentTime > endTime) {
            return 1.0D;
        }

        double delta = endTime - startTime;

        if (delta == 0.0D) {
            return 1.0D;
        }

        return MathHelper.clamp_double((currentTime - startTime) / delta, 0.0D, 1.0D);
    }

    private static int weightAlpha(double scale) {
        return ColorU8.normalizedFloatToByte((float) scale);
    }

    /**
     * Minimal local replacements for the 1.20 {@code net.caffeinemc...api.util} color
     * helpers, which do not exist in this codebase.
     */
    private static final class ColorARGB {
        private static int withAlpha(int color, int alpha) {
            return (color & 0x00FFFFFF) | (clampAlpha(alpha) << 24);
        }

        private static int clampAlpha(int alpha) {
            return alpha & 0xFF;
        }
    }

    private static final class ColorU8 {
        private static int normalizedFloatToByte(float value) {
            return (int) (MathHelper.clamp_float(value, 0.0F, 1.0F) * 255.0F);
        }
    }

    private static final class ActiveMessage {
        private final MessageLevel level;
        private final String text;
        private final double duration;
        private final double timestamp;

        private ActiveMessage(MessageLevel level, String text, double duration, double timestamp) {
            this.level = level;
            this.text = text;
            this.duration = duration;
            this.timestamp = timestamp;
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

        public double timestamp() {
            return this.timestamp;
        }

        public static ActiveMessage create(Message message, double timestamp) {
            return new ActiveMessage(message.level(), message.text(), message.duration(), timestamp);
        }
    }

    private static final EnumMap<MessageLevel, ColorPalette> COLORS = new EnumMap<MessageLevel, ColorPalette>(MessageLevel.class);

    static {
        COLORS.put(MessageLevel.INFO, new ColorPalette(
                ColorPalette.pack(255, 255, 255),
                ColorPalette.pack( 15,  15,  15),
                ColorPalette.pack( 15,  15,  15)
        ));

        COLORS.put(MessageLevel.WARN, new ColorPalette(
                ColorPalette.pack(224, 187,   0),
                ColorPalette.pack( 25,  21,   0),
                ColorPalette.pack(180, 150,   0)
        ));

        COLORS.put(MessageLevel.SEVERE, new ColorPalette(
                ColorPalette.pack(220,   0,   0),
                ColorPalette.pack( 25,   0,   0),
                ColorPalette.pack(160,   0,   0)
        ));
    }

    private static final class ColorPalette {
        private final int text;
        private final int background;
        private final int foreground;

        private ColorPalette(int text, int background, int foreground) {
            this.text = 0xFF000000 | text;
            this.background = 0xFF000000 | background;
            this.foreground = 0xFF000000 | foreground;
        }

        public static int pack(int red, int green, int blue) {
            return (red & 0xFF) << 16 | (green & 0xFF) << 8 | (blue & 0xFF);
        }

        public int text() {
            return this.text;
        }

        public int background() {
            return this.background;
        }

        public int foreground() {
            return this.foreground;
        }
    }

    private static final class MessageRender {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final MessageLevel level;
        private final List<String> lines;
        private final double opacity;

        private MessageRender(int x, int y, int width, int height, MessageLevel level, List<String> lines, double opacity) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.level = level;
            this.lines = lines;
            this.opacity = opacity;
        }

        public int x() {
            return this.x;
        }

        public int y() {
            return this.y;
        }

        public int width() {
            return this.width;
        }

        public int height() {
            return this.height;
        }

        public MessageLevel level() {
            return this.level;
        }

        public List<String> lines() {
            return this.lines;
        }

        public double opacity() {
            return this.opacity;
        }
    }
}


