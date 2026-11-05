#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Dynamic Surroundings realistic aurora: a triangle over the whole screen, from the vertex's index alone, no vertex
// buffer. What turns a pixel back into a direction is in RealisticAuroraInfo, worked out in Java: working it out here
// from the world's matrices and handing it on crashed AMD's Vulkan driver (26.10.1) as the pipeline was compiled.

layout(location = 0) out vec2 screenPos;

void main() {
    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    screenPos = uv * 2.0 - 1.0;
    gl_Position = vec4(screenPos, 0.0, 1.0);
}
