// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// The frame's point lights, all drawn in one pass (PointLights packs PointLightFrame into it)

// The most lights (PointLightFrame.MAX_DRAWN)
const int MAX_LIGHTS = 64;

layout(std140) uniform PointLightInfo {
    // The projection's inverse: from clip space back to view space
    mat4 InvProjMat;
    // x: 1 if clip space depth runs 0 to 1, 0 if it runs -1 to 1; y: how many lights
    vec4 Params;
    // The part of the screen the lights can light between them: x0, y0, x1, y1, in normalized device coordinates
    vec4 Area;
    // Three for each light: its center in view space and radius; its color and strength; the part of the screen it
    // can light, as Area
    vec4 Lights[MAX_LIGHTS * 3];
};
