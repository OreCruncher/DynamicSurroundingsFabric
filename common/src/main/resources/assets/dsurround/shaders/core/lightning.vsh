#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Dynamic Surroundings lightning: a channel's ribbon, or a patch of the cloud's glow around a strike. UV0 is where in
// it the vertex is, from its middle: across the ribbon (y, -1 to 1), or across the patch (x and y), with the patch's
// noise cell added to x (see lightning.fsh). The vertex colour is the light's, and its alpha half its brightness.
// Positions are from the camera.

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
}
