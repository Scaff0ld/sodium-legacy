#version 330 core

#import <sodiumlegacy:include/fog.glsl>

in vec4 v_Color; // The interpolated vertex color
in vec2 v_TexCoord; // The interpolated block texture coordinates
// v_FragDistance is written by the VSH only when USE_FOG is defined; guard the
// declaration to match -- an unmatched out/in pair is undefined behaviour in GLSL.
#ifdef USE_FOG
in float v_FragDistance; // The fragment's distance from the camera
#endif

in float v_MaterialMipBias;
// v_MaterialAlphaCutoff is written by the VSH only when USE_FRAGMENT_DISCARD is set.
#ifdef USE_FRAGMENT_DISCARD
in float v_MaterialAlphaCutoff;
#endif

uniform sampler2D u_BlockTex; // The block texture

uniform vec4 u_FogColor; // The color of the shader fog
uniform int u_FogMode; // The fog mode (see fog.glsl: LINEAR/EXP/EXP2)
uniform float u_FogStart; // The starting position of the shader fog
uniform float u_FogEnd; // The ending position of the shader fog
uniform float u_FogDensity; // The density of the shader fog (EXP/EXP2 modes)

out vec4 fragColor; // The output fragment for the color framebuffer

void main() {
    // A negative mip *bias* is not enough to disable mipmaps: once the implicit LOD
    // grows past the bias, the mip chain is sampled again and neighbouring atlas tiles
    // bleed into distant geometry as thin lines. Non-mipped materials must be pinned
    // to the top mip level exactly like vanilla's non-mipmapped cutout pass.
    vec4 diffuseColor;
    if (v_MaterialMipBias < -1.0) {
        diffuseColor = textureLod(u_BlockTex, v_TexCoord, 0.0);
    } else {
        diffuseColor = texture(u_BlockTex, v_TexCoord, 0.0);
    }

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }
#endif

    // Apply per-vertex color
    diffuseColor.rgb *= v_Color.rgb;

    // Apply ambient occlusion "shade"
    diffuseColor.rgb *= v_Color.a;

#ifdef USE_FOG
    fragColor = _applyFog(diffuseColor, v_FragDistance, u_FogColor, u_FogMode, u_FogStart, u_FogEnd, u_FogDensity);
#else
    fragColor = _applyFog(diffuseColor, 0.0, u_FogColor, u_FogMode, u_FogStart, u_FogEnd, u_FogDensity);
#endif
}