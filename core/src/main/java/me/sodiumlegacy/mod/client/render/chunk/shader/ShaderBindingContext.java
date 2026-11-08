package me.sodiumlegacy.mod.client.render.chunk.shader;

import me.sodiumlegacy.mod.client.gl.shader.uniform.GlUniform;
import me.sodiumlegacy.mod.client.gl.shader.uniform.GlUniformBlock;

import java.util.function.IntFunction;

public interface ShaderBindingContext {
    <U extends GlUniform<?>> U bindUniform(String name, IntFunction<U> factory);

    GlUniformBlock bindUniformBlock(String name, int bindingPoint);
}
