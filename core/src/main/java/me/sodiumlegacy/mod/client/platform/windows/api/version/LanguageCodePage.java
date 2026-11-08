package me.sodiumlegacy.mod.client.platform.windows.api.version;

import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;

public final class LanguageCodePage {
    static final int STRIDE = Integer.BYTES;

    private final int languageId;
    private final int codePage;

    public LanguageCodePage(int languageId, int codePage) {
        this.languageId = languageId;
        this.codePage = codePage;
    }

    public int languageId() {
        return this.languageId;
    }

    public int codePage() {
        return this.codePage;
    }

    static LanguageCodePage decode(long address) {
        int value = MemoryUtil.memGetInt(address);

        int languageId = value & 0xFFFF;
        int codePage = (value & 0xFFFF0000) >> 16;

        return new LanguageCodePage(languageId, codePage);
    }
}

