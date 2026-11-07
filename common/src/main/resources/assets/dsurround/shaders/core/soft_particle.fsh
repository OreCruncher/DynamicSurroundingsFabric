#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Vanilla's particle fragment shader (shaders/core/particle.fsh), made "soft": the particle fades out as it nears
// whatever is behind it (terrain, the surface of water), so where it passes into them it blends away instead of
// being cut off in a hard line.
//
// SOFT_DISTANCE, set by the pipeline, is how far in front of what is behind it, in blocks, the particle is at full
// strength.
//
// The same shader draws in Improved Transparency's stages (OIT, set by the pipeline), as vanilla's does. A flame's
// pipeline also sets OIT_ADDITIVE: it adds its light and hides nothing behind it.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;
// A copy of the scene's depth, made just before the particles are drawn
uniform sampler2D DepthSampler;

layout(std140) uniform SoftParticleInfo {
    // 1 if clip space depth runs 0 to 1, 0 if it runs -1 to 1
    float DepthZeroToOne;
};

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

#include <dsurround:view_distance.glsl>

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    // Vanilla discards below 0.1 alpha after the particle's own alpha is applied; this tests the texture alone, and
    // only drops what is all but invisible. A faint particle fading out (mist) would otherwise vanish all at once
    // when it crossed 0.1, and the faint outer wisps of the mist sprites would be cut off. Tested before the soft
    // fade too, so that takes the edge smoothly down to nothing.
    if (texel.a < 0.01) {
        discard;
    }
    vec4 color = texel * vertexColor * ColorModulator;

    float sceneDistance = view_distance(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r);
    float particleDistance = view_distance(gl_FragCoord.z);
    color.a *= clamp((sceneDistance - particleDistance) / SOFT_DISTANCE, 0.0, 1.0);

    #ifdef OIT
    // Every stage has to drop what the depth bounds stage does (executeAlphaOnlyPhase): where only that one does, the
    // composite writes the far plane to the depth, a hole in the terrain that clouds show through
    if (color.a < 0.01) {
        discard;
    }
    #endif

    #ifdef OIT_ALPHA_ONLY
    #ifdef OIT_ADDITIVE
    // At full alpha the depth bounds would take it for something opaque, and what is behind it would be dropped
    color.a = min(color.a, 0.98);
    #endif
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif
}
