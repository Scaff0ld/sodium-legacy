package me.sodiumlegacy.mod.client.gui.prompt;

import me.sodiumlegacy.mod.client.util.Dim2i;

public interface ScreenPromptable {
    void setPrompt(ScreenPrompt prompt);

    ScreenPrompt getPrompt();

    Dim2i getDimensions();
}
