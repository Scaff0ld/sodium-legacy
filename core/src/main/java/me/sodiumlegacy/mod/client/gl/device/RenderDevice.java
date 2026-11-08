package me.sodiumlegacy.mod.client.gl.device;

import me.sodiumlegacy.mod.client.gl.functions.DeviceFunctions;
import me.sodiumlegacy.lwjgl3shim.opengl.GLCapabilities;

public interface RenderDevice {
    RenderDevice INSTANCE = new GLRenderDevice();

    CommandList createCommandList();

    static void enterManagedCode() {
        RenderDevice.INSTANCE.makeActive();
    }

    static void exitManagedCode() {
        RenderDevice.INSTANCE.makeInactive();
    }

    void makeActive();
    void makeInactive();

    GLCapabilities getCapabilities();

    DeviceFunctions getDeviceFunctions();
}

