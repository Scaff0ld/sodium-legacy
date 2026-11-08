package me.sodiumlegacy.mod.client.gl.shader.uniform;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import me.sodiumlegacy.lwjgl3shim.opengl.GL30C;
import me.sodiumlegacy.lwjgl3shim.system.MemoryStack;

import java.nio.FloatBuffer;

public class GlUniformMatrix4f extends GlUniform<Matrix4fc>  {
    public GlUniformMatrix4f(int index) {
        super(index);
    }

    @Override
    public void set(Matrix4fc value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.callocFloat(16);
            value.get(buf);

            GL30C.glUniformMatrix4fv(this.index, false, buf);
        }
    }
}

