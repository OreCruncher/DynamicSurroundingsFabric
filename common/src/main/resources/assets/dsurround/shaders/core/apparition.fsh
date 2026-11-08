#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Dynamic Surroundings apparition (see ApparitionRenderer). A villager's textures layered as the game draws them, each
// a cutout over the last: the villager (Sampler0), its biome's clothes (Sampler1, left out where the vertex color's
// green says: its hat, under a profession's), its profession's (Sampler2, where the blue says there is one). Only the
// result's brightness is kept, turned pale, white in the light parts going to blue in the dark ones, and seen through.
// The shade is in the red. With DEPTH_ONLY defined it writes only the figure's depth, where enough of it shows, so the
// color pass after sees only its nearest faces. Fog fades it as it does anything else.

#include <minecraft:fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in float sphericalVertexDistance;
layout(location = 3) in float cylindricalVertexDistance;

layout(location = 0) out vec4 fragColor;

// Below this much showing, a part writes no depth: the faded legs don't hide what is behind them
const float DEPTH_CUTOFF = 0.12;
// The pale colors it goes between, dark to light
const vec3 SHADOW = vec3(0.42, 0.55, 0.85);
const vec3 LIGHT = vec3(0.92, 0.96, 1.0);
// What the game's cutout drawing leaves out of a layer
const float CUTOUT = 0.1;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (vertexColor.g < 0.5) {
        vec4 type = texture(Sampler1, texCoord0);
        if (type.a >= CUTOUT) {
            texel = type;
        }
    }
    if (vertexColor.b > 0.5) {
        vec4 profession = texture(Sampler2, texCoord0);
        if (profession.a >= CUTOUT) {
            texel = profession;
        }
    }
    float alpha = texel.a * vertexColor.a;
    if (texel.a < CUTOUT || alpha <= 0.004) {
        discard;
    }
#ifdef DEPTH_ONLY
    if (alpha < DEPTH_CUTOFF) {
        discard;
    }
    fragColor = vec4(0.0);
#else
    float brightness = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
    vec3 color = mix(SHADOW, LIGHT, smoothstep(0.1, 0.75, brightness)) * vertexColor.r;
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd);
    color = mix(color, FogColor.rgb, fog * FogColor.a);
    // Fog thins it too: far off, it is hardly there
    alpha *= 1.0 - fog;
    fragColor = vec4(color * alpha, alpha);
#endif
}
