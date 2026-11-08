package me.sodiumlegacy.mod.client.gui.screen;
import me.sodiumlegacy.mod.client.util.SodiumLang;

import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.gui.console.Console;
import me.sodiumlegacy.mod.client.gui.console.message.MessageLevel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class ConfigCorruptedScreen extends Screen {
    private static final String TEXT_BODY_RAW = "A problem occurred while trying to load the configuration file. This\n"
            + "can happen when the file has been corrupted on disk, or when trying\n"
            + "to manually edit the file by hand.\n"
            + "\n"
            + "If you continue, the configuration file will be reset back to known-good\n"
            + "defaults, and you will lose any changes that have since been made to your\n"
            + "Video Settings.\n"
            + "\n"
            + "More information about the error can be found in the log file.\n";

    private static final List<String> TEXT_BODY = Arrays.asList(TEXT_BODY_RAW.split("\n"));

    private static final int BUTTON_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 20;

    private static final int SCREEN_PADDING = 32;

    private static final int BUTTON_ID_CONTINUE = 0;
    private static final int BUTTON_ID_GO_BACK = 1;

    private final @Nullable Screen prevScreen;
    private final Function<Screen, Screen> nextScreen;

    public ConfigCorruptedScreen(@Nullable Screen prevScreen, @Nullable Function<Screen, Screen> nextScreen) {
        this.prevScreen = prevScreen;
        this.nextScreen = nextScreen;
    }

    @Override
    public void init() {
        super.init();

        int buttonY = this.height - SCREEN_PADDING - BUTTON_HEIGHT;

        this.buttons.add(new ButtonWidget(BUTTON_ID_CONTINUE,
                this.width - SCREEN_PADDING - BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                "Continue"));

        this.buttons.add(new ButtonWidget(BUTTON_ID_GO_BACK,
                SCREEN_PADDING, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                "Go back"));
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();

        this.drawWithShadow(this.textRenderer, "Sodium Legacy", 32, 32, 0xffffff);
        this.drawWithShadow(this.textRenderer, "Could not load the configuration file", 32, 48, 0xff0000);

        for (int i = 0; i < TEXT_BODY.size(); i++) {
            String line = TEXT_BODY.get(i);

            if (line.isEmpty()) {
                continue;
            }

            this.drawWithShadow(this.textRenderer, line, 32, 68 + (i * 12), 0xffffff);
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        switch (button.id) {
            case BUTTON_ID_CONTINUE:
                Console.instance().logMessage(MessageLevel.INFO,
                        SodiumLang.translate("sodiumlegacy.console.config_file_was_reset"), 3.0);

                SodiumClientMod.restoreDefaultOptions();
                MinecraftClient.getInstance().setScreen(this.nextScreen.apply(this.prevScreen));
                break;
            case BUTTON_ID_GO_BACK:
                MinecraftClient.getInstance().setScreen(this.prevScreen);
                break;
        }
    }
}
