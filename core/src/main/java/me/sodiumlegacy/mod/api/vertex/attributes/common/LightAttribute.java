package me.sodiumlegacy.mod.api.vertex.attributes.common;

import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;

public class LightAttribute {
    public static void set(long ptr, int light) {
        MemoryUtil.memPutInt(ptr + 0, light);
    }

    public static int get(long ptr) {
        return MemoryUtil.memGetInt(ptr);
    }
}

