package me.sodiumlegacy.mod.mixin.features.gui.hooks.debug;

import com.google.common.collect.Lists;
import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.render.SodiumWorldRenderer;
import me.sodiumlegacy.mod.client.util.MathUtil;
import me.sodiumlegacy.mod.client.util.NativeBuffer;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;

@Mixin(DebugHud.class)
public abstract class DebugHudMixin {
    @Redirect(method = "getRightText", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayList([Ljava/lang/Object;)Ljava/util/ArrayList;", remap = false))
    private ArrayList<String> redirectRightTextEarly(Object[] elements) {
        ArrayList<String> strings = Lists.newArrayList((String[]) elements);
        strings.add("");
        strings.add(String.format("%sSodium Legacy (%s)", getVersionColor(), SodiumClientMod.getVersion()));

        SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();

        if (renderer != null) {
            strings.addAll(renderer.getDebugStrings());
        }

        for (int i = 0; i < strings.size(); i++) {
            String str = strings.get(i);

            if (str.startsWith("Allocated:")) {
                strings.add(i + 1, getNativeMemoryString());

                break;
            }
        }

        return strings;
    }

    @Unique
    private static Formatting getVersionColor() {
        String version = SodiumClientMod.getVersion();
        Formatting color;

        if (version.contains("-local")) {
            color = Formatting.RED;
        } else if (version.contains("-snapshot")) {
            color = Formatting.LIGHT_PURPLE;
        } else {
            color = Formatting.GREEN;
        }

        return color;
    }

    @Unique
    private static String getNativeMemoryString() {
        return "Off-Heap: +" + MathUtil.toMib(getNativeMemoryUsage()) + "MB";
    }

    @Unique
    private static long getNativeMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() + NativeBuffer.getTotalAllocated();
    }
}
