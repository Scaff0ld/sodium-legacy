// Copyright 2020 Grondag
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package me.sodiumlegacy.mod.client.gl.shader;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import me.sodiumlegacy.lwjgl3shim.opengl.GL20C;

class ShaderWorkarounds {
	/**
	 * Uploads shader source safely on the 1.8.9/LWJGL 2 runtime.
	 *
	 * <p>This mirrors vanilla 1.8.9's {@code GLX.gl20ShaderSource(shader, ByteBuffer)},
	 * which is the only proven-good upload path under LWJGL 2: the public
	 * {@code GL20.glShaderSource(int, ByteBuffer)} overload encodes the exact UTF-8 byte
	 * blob and forwards (blob address, byte length) to its native glue, which builds the
	 * driver's {@code GLchar*[]} array internally.</p>
	 *
	 * <p>IMPORTANT (1.8.9 port): the LWJGL 3-style raw {@code glShaderSource(count, GLchar*#
	 * lengths*)} ABI does NOT translate. {@code nglShaderSource3(blobAddr, lengthsPtr, fn)}
	 * interprets {@code string} as an array-of-POINTERS (that is what the {@code CharSequence[]}
	 * public overload feeds it), so handing it the raw GLSL text blob makes the driver
	 * dereference the first bytes of "#version 330 core..." as a pointer — the shader
	 * source the driver sees is garbage (or unmapped memory) and compilation fails with
	 * "Shader compilation failed". Never use the raw overload for the string blob.</p>
	 */
	static void safeShaderSource(int glId, CharSequence source) {
		final byte[] bytes = source.toString().getBytes(StandardCharsets.UTF_8);

		final ByteBuffer sourceBuffer = org.lwjgl.BufferUtils.createByteBuffer(bytes.length);
		sourceBuffer.put(bytes);
		sourceBuffer.flip();

		GL20C.glShaderSource(glId, sourceBuffer);
	}
}

