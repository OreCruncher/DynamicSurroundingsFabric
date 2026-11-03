#version 330
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Dynamic Surroundings realistic aurora, laid over the sky (see aurora_composite.vsh): its light, read smoothly between
// the pixels it was drawn at, added to the sky's.

uniform sampler2D AuroraSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(AuroraSampler, texCoord).rgb, 0.0);
}
