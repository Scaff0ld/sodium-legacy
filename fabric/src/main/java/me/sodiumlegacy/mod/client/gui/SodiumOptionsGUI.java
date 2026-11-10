package me.sodiumlegacy.mod.client.gui;

import me.sodiumlegacy.mod.client.util.SodiumLang;
import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.gui.console.Console;
import me.sodiumlegacy.mod.client.gui.console.message.MessageLevel;
import me.sodiumlegacy.mod.client.gui.options.*;
import me.sodiumlegacy.mod.client.gui.options.control.Control;
import me.sodiumlegacy.mod.client.gui.options.control.ControlElement;
import me.sodiumlegacy.mod.client.gui.options.storage.OptionStorage;
import me.sodiumlegacy.mod.client.gui.prompt.ScreenPrompt;
import me.sodiumlegacy.mod.client.gui.prompt.ScreenPromptable;
import me.sodiumlegacy.mod.client.gui.screen.ConfigCorruptedScreen;
import me.sodiumlegacy.mod.client.gui.widgets.AbstractWidget;
import me.sodiumlegacy.mod.client.gui.widgets.FlatButtonWidget;
import me.sodiumlegacy.mod.client.util.Dim2i;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.VideoOptionsScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

public class SodiumOptionsGUI extends Screen implements ScreenPromptable {
    private final List<OptionPage> pages = new ArrayList<>();

    private final List<ControlElement<?>> controls = new ArrayList<>();

    // Sodium's widgets are custom-drawn and are not vanilla buttons, so they are
    // tracked here and dispatched by hand instead of through the vanilla screen.
    private final List<AbstractWidget> children = new ArrayList<>();

    private final Screen prevScreen;

    private OptionPage currentPage;

    private FlatButtonWidget applyButton, closeButton, undoButton;

    private boolean hasPendingChanges;
    private ControlElement<?> hoveredElement;

    private @Nullable ScreenPrompt prompt;

    // Pages with many options (Advanced has the per-particle switches) don't fit on screen, so
    // the option list scrolls one option at a time with the mouse wheel. Rows that don't fit
    // completely are simply not created, which avoids needing a GL scissor.
    private int scrollIndex;
    private boolean hasHiddenOptionsBelow;
    private int shownOptionCount;

    private SodiumOptionsGUI(Screen prevScreen) {
        this.prevScreen = prevScreen;

        this.pages.add(SodiumGameOptionPages.general());
        this.pages.add(SodiumGameOptionPages.quality());
        this.pages.add(SodiumGameOptionPages.performance());
        this.pages.add(SodiumGameOptionPages.miscellaneous());
        this.pages.add(SodiumGameOptionPages.advanced());
    }

    public static Screen createScreen(Screen currentScreen) {
        if (SodiumClientMod.options().isReadOnly()) {
            return new ConfigCorruptedScreen(currentScreen, new ScreenCreator());
        } else {
            return new SodiumOptionsGUI(currentScreen);
        }
    }

    private static class ScreenCreator implements Function<Screen, Screen> {
        @Override
        public Screen apply(Screen screen) {
            return new SodiumOptionsGUI(screen);
        }
    }

    public void setPage(OptionPage page) {
        this.currentPage = page;
        this.scrollIndex = 0;

        this.rebuildGUI();
    }

    @Override
    public void init() {
        super.init();

        this.rebuildGUI();

        if (this.prompt != null) {
            this.prompt.init();
        }
    }

    private void rebuildGUI() {
        this.controls.clear();

        this.clearChildren();

        if (this.currentPage == null) {
            if (this.pages.isEmpty()) {
                throw new IllegalStateException("No pages are available?!");
            }

            // Just use the first page for now
            this.currentPage = this.pages.get(0);
        }

        this.rebuildGUIPages();
        this.rebuildGUIOptions();

        this.undoButton = new FlatButtonWidget(new Dim2i(this.width - 211, this.height - 30, 65, 20), SodiumLang.translate("sodiumlegacy.options.buttons.undo"), this::undoChanges);
        this.applyButton = new FlatButtonWidget(new Dim2i(this.width - 142, this.height - 30, 65, 20), SodiumLang.translate("sodiumlegacy.options.buttons.apply"), this::applyChanges);
        this.closeButton = new FlatButtonWidget(new Dim2i(this.width - 73, this.height - 30, 65, 20), SodiumLang.translate("gui.done"), this::close);

        this.addDrawableChild(this.undoButton);
        this.addDrawableChild(this.applyButton);
        this.addDrawableChild(this.closeButton);
    }

    private void rebuildGUIPages() {
        int x = 6;
        int y = 6;

        for (OptionPage page : this.pages) {
            int width = 12 + this.textRenderer.getStringWidth(page.getName());

            FlatButtonWidget button = new FlatButtonWidget(new Dim2i(x, y, width, 18), page.getName(), () -> this.setPage(page));
            button.setSelected(this.currentPage == page);

            x += width + 6;

            this.addDrawableChild(button);
        }
    }

    private void rebuildGUIOptions() {
        int x = 6;
        int y = 28;
        int limitY = this.getOptionAreaLimitY();

        int index = 0;
        int shown = 0;
        boolean overflow = false;

        // Never leave the list scrolled past its end (e.g. after the window got resized).
        this.scrollIndex = Math.max(0, Math.min(this.scrollIndex, this.currentPage.getOptions().size() - 1));

        outer:
        for (OptionGroup group : this.currentPage.getGroups()) {
            boolean placedInGroup = false;

            // Add each option's control element
            for (Option<?> option : group.getOptions()) {
                if (index++ < this.scrollIndex) {
                    continue;
                }

                if (y + 18 > limitY) {
                    overflow = true;
                    break outer;
                }

                Control<?> control = option.getControl();
                ControlElement<?> element = control.createElement(new Dim2i(x, y, 200, 18));

                this.addDrawableChild(element);

                this.controls.add(element);

                // Move down to the next option
                y += 18;
                shown++;
                placedInGroup = true;
            }

            // Add padding beneath each option group
            if (placedInGroup) {
                y += 4;
            }
        }

        this.hasHiddenOptionsBelow = overflow;
        this.shownOptionCount = shown;
    }

    private int getOptionAreaLimitY() {
        // Keep the bottom strip free for the Undo / Apply / Done buttons.
        return this.height - 36;
    }

    private void scrollOptions(int direction) {
        if (direction < 0 && this.scrollIndex > 0) {
            this.scrollIndex--;
        } else if (direction > 0 && this.hasHiddenOptionsBelow) {
            this.scrollIndex++;
        } else {
            return;
        }

        this.rebuildGUI();
    }

    @Override
    public void handleMouse() {
        try {
            super.handleMouse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (this.prompt == null) {
            int wheel = Mouse.getEventDWheel();

            if (wheel != 0) {
                this.scrollOptions(wheel > 0 ? -1 : 1);
            }
        }
    }

    private void renderScrollBar() {
        if (this.scrollIndex == 0 && !this.hasHiddenOptionsBelow) {
            return;
        }

        int total = this.currentPage.getOptions().size();

        int trackTop = 28;
        int trackBottom = this.getOptionAreaLimitY();
        int trackHeight = trackBottom - trackTop;

        int thumbHeight = Math.max(12, trackHeight * this.shownOptionCount / Math.max(1, total));
        int thumbTop = trackTop + (trackHeight - thumbHeight) * this.scrollIndex / Math.max(1, total - this.shownOptionCount);

        int barX = 6 + 200 + 2;

        this.fillGradient(barX, trackTop, barX + 3, trackBottom, 0x60000000, 0x60000000);
        this.fillGradient(barX, thumbTop, barX + 3, thumbTop + thumbHeight, 0xFFC0C0C0, 0xFFC0C0C0);
    }

    private void addDrawableChild(AbstractWidget widget) {
        this.children.add(widget);
    }

    private void clearChildren() {
        this.children.clear();
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.updateControls();

        if (this.prompt == null) {
            this.renderBackground();
        }

        int mx = this.prompt != null ? -1 : mouseX;
        int my = this.prompt != null ? -1 : mouseY;

        for (AbstractWidget widget : this.children) {
            widget.render(mx, my);
        }

        this.renderScrollBar();

        if (this.hoveredElement != null && this.prompt == null) {
            this.renderOptionTooltip(this.hoveredElement);
        }

        if (this.prompt != null) {
            this.prompt.render(mouseX, mouseY);
        }
    }

    private void updateControls() {
        ControlElement<?> hovered = null;

        for (ControlElement<?> control : this.controls) {
            if (control.isHovered()) {
                hovered = control;
                break;
            }
        }

        if (hovered == null) {
            // If there is no hovered element, use the focused element.
            for (ControlElement<?> control : this.controls) {
                if (control.isFocused()) {
                    hovered = control;
                    break;
                }
            }
        }

        boolean hasChanges = false;

        for (OptionPage page : this.pages) {
            for (Option<?> option : page.getOptions()) {
                if (option.hasChanged()) {
                    hasChanges = true;
                    break;
                }
            }

            if (hasChanges) {
                break;
            }
        }

        this.applyButton.setEnabled(hasChanges);
        this.undoButton.setVisible(hasChanges);
        this.closeButton.setEnabled(!hasChanges);

        this.hasPendingChanges = hasChanges;
        this.hoveredElement = hovered;
    }

    private void renderOptionTooltip(ControlElement<?> element) {
        Dim2i dim = element.getDimensions();

        int textPadding = 3;
        int boxPadding = 3;

        int boxWidth = 200;

        int boxY = dim.y();
        int boxX = dim.getLimitX() + boxPadding;

        if (boxX + boxWidth > this.width) {
            boxX = Math.max(0, dim.x() - boxPadding - boxWidth);
        }

        Option<?> option = element.getOption();
        List<String> tooltip = new ArrayList<>(this.textRenderer.wrapLines(option.getTooltip(), boxWidth - (textPadding * 2)));

        OptionImpact impact = option.getImpact();

        if (impact != null) {
            // The impact value is pre-colored by its own enum (green/yellow/red/gray); the
            // line label stays gray so the level color stands out.
            tooltip.add(Formatting.GRAY + SodiumLang.translate("sodiumlegacy.options.performance_impact_string",
                    impact.getLocalizedName()));
        }

        int boxHeight = (tooltip.size() * 12) + boxPadding;
        int boxYLimit = boxY + boxHeight;
        int boxYCutoff = this.height - 40;

        // If the box is going to be cutoff on the Y-axis, move it back up the difference
        if (boxYLimit > boxYCutoff) {
            boxY -= boxYLimit - boxYCutoff;
        }

        this.fillGradient(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000, 0xE0000000);

        for (int i = 0; i < tooltip.size(); i++) {
            this.textRenderer.drawWithShadow(tooltip.get(i), boxX + textPadding, boxY + textPadding + (i * 12), 0xFFFFFFFF);
        }
    }

    private void applyChanges() {
        HashSet<OptionStorage<?>> dirtyStorages = new HashSet<>();
        EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);

        for (Option<?> option : this.getAllOptions()) {
            if (!option.hasChanged()) {
                continue;
            }

            option.applyChanges();

            flags.addAll(option.getFlags());
            dirtyStorages.add(option.getStorage());
        }

        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world != null) {
            if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
                client.worldRenderer.reload();
            } else if (flags.contains(OptionFlag.REQUIRES_RENDERER_UPDATE)) {
                client.worldRenderer.scheduleTerrainUpdate();
            }
        }

        // NOTE: mipmap asset reloads are handled directly by the option's binding,
        // since 1.8.9 has no SimpleOption/mipmap accessor to go through.

        if (flags.contains(OptionFlag.REQUIRES_GAME_RESTART)) {
            Console.instance().logMessage(MessageLevel.WARN,
                    SodiumLang.translate("sodiumlegacy.console.game_restart"), 10.0);
        }

        for (OptionStorage<?> storage : dirtyStorages) {
            storage.save();
        }
    }

    private void undoChanges() {
        for (Option<?> option : this.getAllOptions()) {
            option.reset();
        }
    }

    private Iterable<Option<?>> getAllOptions() {
        List<Option<?>> options = new ArrayList<>();

        for (OptionPage page : this.pages) {
            options.addAll(page.getOptions());
        }

        return options;
    }

    @Override
    public void keyPressed(char typedChar, int keyCode) {
        if (this.prompt != null && this.prompt.keyPressed(keyCode)) {
            return;
        }

        if (this.prompt == null && keyCode == Keyboard.KEY_P && Screen.hasShiftDown()) {
            GameOptions options = MinecraftClient.getInstance().options;
            MinecraftClient.getInstance().setScreen(new VideoOptionsScreen(this.prevScreen, options));

            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            // Block closing while there are unapplied changes.
            if (!this.hasPendingChanges) {
                this.close();
            }

            return;
        }

        for (AbstractWidget widget : this.children) {
            if (widget.keyPressed(keyCode)) {
                return;
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (this.prompt != null) {
            this.prompt.mouseClicked(mouseX, mouseY, button);

            return;
        }

        // End any drag that is still in progress (e.g. a slider thumb held from a
        // previous click) before dispatching the new click, so at most one widget
        // can be dragged at a time.
        for (AbstractWidget widget : this.children) {
            widget.resetDrag();
        }

        for (AbstractWidget widget : this.children) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
        }
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY, int button, long lastClickTime) {
        if (this.prompt != null) {
            return;
        }

        for (AbstractWidget widget : this.children) {
            if (widget.mouseDragged(mouseX, mouseY, button)) {
                // Only the widget that accepted the drag gets further drag events.
                return;
            }
        }
    }

    public boolean hasPendingChanges() {
        return this.hasPendingChanges;
    }

    // NOTE: vanilla 1.8.9 Screen has no close() hook; this is sodium's own method called by
    // our widgets (vanilla only calls client.closeScreen()).
    public void close() {
        this.client.setScreen(this.prevScreen);
    }

    @Override
    public void setPrompt(@Nullable ScreenPrompt prompt) {
        this.prompt = prompt;
    }

    @Nullable
    @Override
    public ScreenPrompt getPrompt() {
        return this.prompt;
    }

    @Override
    public Dim2i getDimensions() {
        return new Dim2i(0, 0, this.width, this.height);
    }
}