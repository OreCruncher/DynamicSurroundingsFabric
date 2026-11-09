#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Dynamic Surroundings point lights (fireflies', flame jets'): two triangles, made from the vertex's index, over the
// part of the screen the frame's lights can light between them (Area), where point_light.fsh lights the scene.

#include <dsurround:point_lights.glsl>

// The corners of the two triangles: 0 is (x0, y0), 1 (x1, y0), 2 (x0, y1), 3 (x1, y1)
const int CORNERS[6] = int[6](0, 1, 2, 2, 1, 3);

void main() {
    int corner = CORNERS[gl_VertexIndex];
    vec2 at = vec2(corner & 1, corner >> 1);
    gl_Position = vec4(mix(Area.xy, Area.zw, at), 0.0, 1.0);
}
