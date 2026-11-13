const int FOG_SHAPE_SPHERICAL = 0;
const int FOG_SHAPE_CYLINDRICAL = 1;

// Fog modes, translated from GL's fixed-function GL_FOG_MODE by ChunkShaderFogComponent.
const int FOG_MODE_LINEAR = 0;
const int FOG_MODE_EXP = 1;
const int FOG_MODE_EXP2 = 2;

// Reproduces the fixed-function fog blend: LINEAR interpolates between fogStart/fogEnd,
// EXP/EXP2 use the exponential density falloff (underwater, lava, blindness). The alpha
// channel of fogColor is used as the blend weight.
vec4 _applyFog(vec4 fragColor, float fragDistance, vec4 fogColor, int fogMode, float fogStart, float fogEnd, float fogDensity) {
#ifdef USE_FOG
    float factor = 0.0;

    if (fogMode == FOG_MODE_LINEAR) {
        if (fragDistance > fogStart) {
            factor = fragDistance < fogEnd ? smoothstep(fogStart, fogEnd, fragDistance) : 1.0;
        }
    } else if (fogMode == FOG_MODE_EXP) {
        // fixed-function GL_EXP fog: factor = 1 - exp(-density * distance)
        factor = 1.0 - exp(-fogDensity * fragDistance);
    } else if (fogMode == FOG_MODE_EXP2) {
        // fixed-function GL_EXP2 fog: factor = 1 - exp(-(density * distance)^2)
        float scaled = fogDensity * fragDistance;
        factor = 1.0 - exp(-scaled * scaled);
    }

    vec3 blended = mix(fragColor.rgb, fogColor.rgb, factor * fogColor.a);

    return vec4(blended, fragColor.a); // alpha value of fragment cannot be modified
#else
    return fragColor;
#endif
}

float getFragDistance(int fogShape, vec3 position) {
    // Use the maximum of the horizontal and vertical distance to get cylindrical fog if fog shape is cylindrical
    switch (fogShape) {
        case FOG_SHAPE_SPHERICAL: return length(position);
        case FOG_SHAPE_CYLINDRICAL: return max(length(position.xz), abs(position.y));
        default: return length(position); // This shouldn't be possible to get, but return a sane value just in case
    }
}