#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Dynamic Surroundings aurora: one curtain. UV0.x runs along the curtain, UV0.y up it (0 at its lower edge, 1 at
// its top), and the vertex alpha fades it toward its ends. The camera's rotation is in ModelViewMat; the curtains
// are drawn around the camera, as the sky is.

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out float vertexAlpha;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexAlpha = Color.a;
}
