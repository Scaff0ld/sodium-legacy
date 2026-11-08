package me.sodiumlegacy.mod.client.gl.sync;

import me.sodiumlegacy.lwjgl3shim.opengl.GL32C;
import me.sodiumlegacy.lwjgl3shim.system.MemoryStack;
import org.lwjgl.opengl.GLSync;

import java.nio.IntBuffer;

public class GlFence {
    private final GLSync sync;
    private boolean disposed;

    public GlFence(GLSync sync) {
        this.sync = sync;
    }

    public boolean isCompleted() {
        this.checkDisposed();

        int result;

        // Query with the single-value convenience: the shim returns the GL_SYNC_STATUS value
        // and also mirrors it into the scratch buffer. (The "length" buffer check of the
        // two-buffer glGetSynciv shape does not apply to this form: the status constant itself
        // lives in the buffer, so comparing it against 1 throws even on success.)
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer value = stack.callocInt(1);
            result = GL32C.glGetSynci(this.sync, GL32C.GL_SYNC_STATUS, value);
        }

        return result == GL32C.GL_SIGNALED;
    }

    public void sync() {
        this.checkDisposed();
        this.sync(Long.MAX_VALUE);
    }

    public void sync(long timeout) {
        this.checkDisposed();
        GL32C.glWaitSync(this.sync, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, timeout);
    }

    public void delete() {
        GL32C.glDeleteSync(this.sync);
        this.disposed = true;
    }

    private void checkDisposed() {
        if (this.disposed) {
            throw new IllegalStateException("Fence object has been disposed");
        }
    }
}

