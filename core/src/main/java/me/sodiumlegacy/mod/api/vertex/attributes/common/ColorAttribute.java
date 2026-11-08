package me.sodiumlegacy.mod.api.vertex.attributes.common;

import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;

public class ColorAttribute {
    public static void set(long ptr, int color) {
        MemoryUtil.memPutInt(ptr + 0, color);
    }

    public static int get(long ptr) {
        return MemoryUtil.memGetInt(ptr);
    }
}

