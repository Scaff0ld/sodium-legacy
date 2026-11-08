package me.sodiumlegacy.mod.mixin.core;

import java.util.ArrayDeque;
import java.util.Deque;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.compatibility.checks.ResourcePackScanner;
import me.sodiumlegacy.mod.client.particle.ParticleFilter;
import me.sodiumlegacy.mod.client.zoom.ZoomFeature;
import me.sodiumlegacy.lwjgl3shim.opengl.GL32C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.profiler.Profiler;
import org.lwjgl.opengl.GLSync;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the per-frame loop of 1.8.9 ({@code runGameLoop()}; there is no modern
 * {@code render(boolean)}) to wait on our GPU fences, and after resource reloads to check for
 * resource packs which replace core shaders (1.8.9 has no core shader packs, but the check is
 * harmless and keeps the scanner logic in one place).
 *
 * <p>1.8.9 uses fixed-function pipelines; the GL32 objects used here come from sodium's
 * {@code me.sodiumlegacy.lwjgl3shim.opengl.GL32C} compatibility shim over LWJGL 2.</p>
 */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Shadow
    @Final
    private IReloadableResourceManager mcResourceManager;

    @Unique
    private final Deque<GLSync> fences = new ArrayDeque<>();

    /**
     * We run this at the beginning of the frame (except for the first frame) to give the previous frame plenty of time
     * to render on the GPU. This allows us to stall on ClientWaitSync for less time.
     */
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void preRender(CallbackInfo ci) {
        // Cheap flag check after the first successful call; see ParticleFilter for why.
        ParticleFilter.normalizeVanillaSetting();

        // Advances the zoom animation (activation/deactivation easing).
        ZoomFeature.update();

        Profiler profiler = ((Minecraft) (Object) this).mcProfiler;
        profiler.startSection("wait_for_gpu");

        while (this.fences.size() > SodiumClientMod.options().advanced.cpuRenderAheadLimit) {
            GLSync fence = this.fences.poll();
            // We do a ClientWaitSync here instead of a WaitSync to not allow the CPU to get too far ahead of the GPU.
            // This is also needed to make sure that our persistently-mapped staging buffers function correctly, rather
            // than being overwritten by data meant for future frames before the current one has finished rendering on
            // the GPU.
            //
            // Because we use GL_SYNC_FLUSH_COMMANDS_BIT, a flush will be inserted at some point in the command stream
            // (the stream of commands the GPU and/or driver (aka. the "server") is processing).
            GL32C.glClientWaitSync(fence, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, Long.MAX_VALUE);
            GL32C.glDeleteSync(fence);
        }

        profiler.endSection();
    }

    @Inject(method = "runGameLoop", at = @At("RETURN"))
    private void postRender(CallbackInfo ci) {
        GLSync fence = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

        if (fence == null) {
            throw new RuntimeException("Failed to create fence object");
        }

        this.fences.add(fence);
    }

    /**
     * Check for problematic resource packs after every resource reload.
     */
    @Inject(method = "refreshResources", at = @At("RETURN"))
    private void postResourceReload(CallbackInfo ci) {
        ResourcePackScanner.checkIfCoreShaderLoaded(this.mcResourceManager);
    }
}

