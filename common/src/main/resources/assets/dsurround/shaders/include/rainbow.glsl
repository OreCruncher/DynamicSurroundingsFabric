// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// What the rainbow's shaders share (RainbowRenderer, RainbowFrame): its values.

// The angle from the point opposite the sun at the end of the spectrum texture's rows, in degrees
// (RainbowSpectrum.MAX_DEGREES)
#define MAX_DEGREES 60.0

layout(std140) uniform RainbowInfo {
    // From the screen back to directions from the camera: the inverse of the camera's rotation and the projection
    // together, as the world was drawn (view bobbing and all)
    mat4 InvViewProj;
    // xyz: the direction of the point opposite the sun (the moon, for a moonbow); w: how strongly the rainbow shows, 0 to 1
    vec4 Antisolar;
    // xyz: a direction square to Antisolar, toward the top of the bow, which the patches are placed around from;
    // w: seconds, for their drifting
    vec4 Across;
    // rgb: the sunlight's color, as bright as the brightest of the bow is added to the sky; a: how strongly the
    // secondary bow shows, 0 to 1
    vec4 Sunlight;
    // x: where the patches are, the same for everyone seeing this rainbow; y: 1 for flat stripes in blocks (Rainbow
    // style Blocky), 0 for its real colors; z: how far its colors are paled toward gray, a moonbow's; w unused
    vec4 Patches;
};
