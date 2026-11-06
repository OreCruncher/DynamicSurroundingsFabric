#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// The ground fog's passes: a triangle over the whole of what is drawn to, from the vertex's index alone (as vanilla's
// screenquad), no vertex buffer. The matrices they need are in GroundFogInfo, worked out in Java: working them out here
// from the world's matrices and handing them on crashed AMD's Vulkan driver (26.10.1) as the pipeline was compiled.

void main() {
    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
}
