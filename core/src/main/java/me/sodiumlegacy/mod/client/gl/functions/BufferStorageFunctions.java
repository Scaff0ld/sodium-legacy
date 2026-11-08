package me.sodiumlegacy.mod.client.gl.functions;

import me.sodiumlegacy.mod.client.gl.buffer.GlBufferStorageFlags;
import me.sodiumlegacy.mod.client.gl.buffer.GlBufferTarget;
import me.sodiumlegacy.mod.client.gl.device.RenderDevice;
import me.sodiumlegacy.mod.client.gl.util.EnumBitField;
import me.sodiumlegacy.lwjgl3shim.opengl.ARBBufferStorage;
import me.sodiumlegacy.lwjgl3shim.opengl.GL44C;
import me.sodiumlegacy.lwjgl3shim.opengl.GLCapabilities;

public enum BufferStorageFunctions {
    NONE {
        @Override
        public void createBufferStorage(GlBufferTarget target, long length, EnumBitField<GlBufferStorageFlags> flags) {
            throw new UnsupportedOperationException();
        }
    },
    CORE {
        @Override
        public void createBufferStorage(GlBufferTarget target, long length, EnumBitField<GlBufferStorageFlags> flags) {
            GL44C.glBufferStorage(target.getTargetParameter(), length, flags.getBitField());
        }
    },
    ARB {
        @Override
        public void createBufferStorage(GlBufferTarget target, long length, EnumBitField<GlBufferStorageFlags> flags) {
            ARBBufferStorage.glBufferStorage(target.getTargetParameter(), length, flags.getBitField());
        }
    };

    public static BufferStorageFunctions pickBest(RenderDevice device) {
        GLCapabilities capabilities = device.getCapabilities();

        if (capabilities.OpenGL44) {
            return CORE;
        } else if (capabilities.GL_ARB_buffer_storage) {
            return ARB;
        } else {
            return NONE;
        }
    }


    public abstract void createBufferStorage(GlBufferTarget target, long length, EnumBitField<GlBufferStorageFlags> flags);
}

