package me.sodiumlegacy.mod.api.vertex.attributes.common;

import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;

public class NormalAttribute {
    public static void set(long ptr, int normal) {
        MemoryUtil.memPutInt(ptr + 0, normal);
    }

    public static int get(long ptr) {
        return MemoryUtil.memGetInt(ptr);
    }
}

