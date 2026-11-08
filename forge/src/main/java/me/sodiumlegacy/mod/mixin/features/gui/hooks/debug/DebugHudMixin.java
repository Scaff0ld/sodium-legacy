package me.sodiumlegacy.mod.mixin.features.gui.hooks.debug;

import com.google.common.collect.Lists;
import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.render.SodiumWorldRenderer;
import me.sodiumlegacy.mod.client.util.MathUtil;
import me.sodiumlegacy.mod.client.util.NativeBuffer;
import net.minecraft.client.gui.GuiOverlayDebug;
import net.minecraft.util.EnumChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;

@Mixin(GuiOverlayDebug.class)
public abstract class DebugHudMixin {
    @Inject(method = "getDebugInfoRight", at = @At("RETURN"))
    private void redirectRightTextEarly(CallbackInfoReturnable<ArrayList<String>> cir) {
        ArrayList<String> strings = cir.getReturnValue();
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
    }

    @Unique
    private static EnumChatFormatting getVersionColor() {
        String version = SodiumClientMod.getVersion();
        EnumChatFormatting color;

        if (version.contains("-local")) {
            color = EnumChatFormatting.RED;
        } else if (version.contains("-snapshot")) {
            color = EnumChatFormatting.LIGHT_PURPLE;
        } else {
            color = EnumChatFormatting.GREEN;
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
