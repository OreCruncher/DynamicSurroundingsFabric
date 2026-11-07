#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Dynamic Surroundings clouds (see CloudRenderer): blocks of cloud, each vertex's color its shade, gray, and its
// alpha how much it hides. Positions are from the corner of the cloud grid; Offset moves them to be from the camera,
// where fog is measured from: across the ground only (see clouds.fsh). A part of a cloud that is changing fades in or
// out over FadeTicks: UV0 is when its change began (x, in ticks, from when the blocks were built) and which way (y: 1
// in, -1 out, 0 for a settled part, which doesn't fade).

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// Kept the same as in clouds.fsh
layout(std140) uniform DSCloudInfo {
    // xyz from the grid's corner to the camera, in blocks; w ticks since the blocks were built, with the part of a tick
    vec4 OffsetNow;
    // rgb the light on the clouds' tops; w how many ticks a change fades over
    vec4 CloudColorFade;
    // x how far from the camera, across the ground, the clouds begin to fade out, y where they are gone
    vec4 EdgeFade;
    vec4 LightPowerA;
    vec4 LightPowerB;
    vec4 Lights[8];
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

// How far from the camera across the ground, for the fog and for fading out before the grid's edge
layout(location = 0) out float flatDistance;
// Where it is, from the camera, for the light of lightning inside the clouds
layout(location = 1) out vec3 cloudPos;
layout(location = 2) out vec4 vertexColor;

void main() {
    vec3 pos = Position + OffsetNow.xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    flatDistance = length(pos.xz);
    cloudPos = pos;

    float fade = 1.0;
    if (UV0.y != 0.0) {
        float along = clamp((OffsetNow.w - UV0.x) / CloudColorFade.w, 0.0, 1.0);
        along = along * along * (3.0 - 2.0 * along);
        fade = UV0.y > 0.0 ? along : 1.0 - along;
    }
    vertexColor = vec4(Color.rgb, Color.a * fade);
}
