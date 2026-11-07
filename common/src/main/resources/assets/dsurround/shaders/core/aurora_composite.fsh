#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Dynamic Surroundings realistic aurora, laid over the sky (see aurora_composite.vsh): its light, read smoothly between
// the pixels it was drawn at, added to the sky's. Four reads half a pixel apart, each between pixels, even out the ordered
// pattern its samples start in, which one read would only enlarge into grain between the rays.

uniform sampler2D AuroraSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 half_pixel = 0.5 / vec2(textureSize(AuroraSampler, 0));
    vec3 light = texture(AuroraSampler, texCoord + vec2(-half_pixel.x, -half_pixel.y)).rgb
               + texture(AuroraSampler, texCoord + vec2(half_pixel.x, -half_pixel.y)).rgb
               + texture(AuroraSampler, texCoord + vec2(-half_pixel.x, half_pixel.y)).rgb
               + texture(AuroraSampler, texCoord + vec2(half_pixel.x, half_pixel.y)).rgb;
    fragColor = vec4(light * 0.25, 0.0);
}
