#version 150
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Ground fog as a volume: a quad over the whole screen, given in normalized device coordinates

in vec3 Position;

out vec2 screenUV;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    screenUV = Position.xy * 0.5 + 0.5;
}
