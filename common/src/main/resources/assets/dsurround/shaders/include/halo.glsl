// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// What the halo's shaders share (HaloRenderer, HaloFrame): its values.

layout(std140) uniform HaloInfo {
    // From the screen back to directions from the camera: the inverse of the camera's rotation and the projection
    // together, as the world was drawn (view bobbing and all)
    mat4 InvViewProj;
    // xyz: the direction of the sun; w: how strongly the halo shows, 0 to 1
    vec4 Sun;
    // rgb: the sunlight's color, as bright as the brightest of the halo is added to the sky; a: how strongly the sun
    // dogs show, 0 to 1
    vec4 Sunlight;
    // xyz: how far round from the sun the sun dogs are, level with it, for red, green and blue light, in degrees;
    // w: how high the sun is, in degrees
    vec4 Dogs;
    // x: 1 for blocks (Sky style Blocky), 0 for its real look; y, z and w unused
    vec4 Style;
};
