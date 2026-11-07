#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Particle rain, snow and dust. A drop fades into the fog rather than taking on its colour: the ground behind it is
// already fogged, so a fogged drop would look the same, and fading also suits drops already faint at their tops.
// Snowflakes and dust are textured; rain streaks are plain colour, marked by a texture coordinate below zero.
//
// With SOFT_DISTANCE set by the pipeline (the dust), drawn "soft", as the soft particle shader draws mist: a puff fades
// out as it nears whatever is behind it, so where it passes into the ground it blends away instead of ending in a hard
// line. SOFT_DISTANCE is how far in front of what is behind it, in blocks, a puff is at full strength.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

uniform sampler2D Sampler0;

#ifdef SOFT_DISTANCE
// A copy of the scene's depth, water included, made just before the dust is drawn
uniform sampler2D DepthSampler;

layout(std140) uniform SoftParticleInfo {
    // 1 if clip space depth runs 0 to 1, 0 if it runs -1 to 1
    float DepthZeroToOne;
};

#include <dsurround:view_distance.glsl>
#endif

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

// How much of a drop shows through the fog: all of it before the fog starts, none past where it ends
float fog_fade() {
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    return 1.0 - smoothstep(0.0, 1.0, fog);
}

void main() {
    vec4 color = vertexColor * ColorModulator;
    // Rain streaks have no texture: the whole quad is given a coordinate below zero
    if (texCoord0.x >= 0.0) {
        color *= texture(Sampler0, texCoord0);
    }
#ifdef SOFT_DISTANCE
    float sceneDistance = view_distance(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r);
    float dropDistance = view_distance(gl_FragCoord.z);
    color.a *= clamp((sceneDistance - dropDistance) / SOFT_DISTANCE, 0.0, 1.0);
#endif
    color.a *= fog_fade();
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
