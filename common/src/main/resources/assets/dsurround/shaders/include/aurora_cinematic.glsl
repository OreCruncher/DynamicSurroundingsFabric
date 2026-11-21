// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// What the cinematic aurora's shader is given (CinematicAuroraRenderer, CinematicAuroraFrame). Distances are in
// kilometers; the ground's directions are the world's x and z.

// The most ribbons (CinematicAurora.MAX_RIBBONS)
#define MAX_RIBBONS 4

layout(std140) uniform CinematicAuroraInfo {
    // From the screen back to directions from the camera: the inverse of the camera's rotation and the projection
    // together, as the world was drawn (view bobbing and all)
    mat4 InvViewProj;
    // x: seconds, for the animation; y: how strongly it shows, 0 to 1; z: radians across a pixel, up and down the
    // screen, at its middle; w: how many ribbons there are
    vec4 Frame;
    // x: how distinct the rays are, 0 (smoky) to 1 (RealisticAurora.Moment.rayContrast); y: how strong the violet toward
    // the ribbons' tops is; z and w unused
    vec4 Look;
    // x: how high the ribbons' lower edge is (CinematicAurora.lowerEdgeAt); y, z and w unused
    vec4 Edge;
    // Each ribbon's line (CinematicAurora.Ribbon): xy the way it runs, a unit vector in the world's x and z; z how far
    // across from the viewer it is now, to its left (CinematicAurora.distanceAt); w how far its ends bow
    vec4 RibbonAxis[MAX_RIBBONS];
    // Each ribbon's folds: x how far its large folds reach across; y how far along it from one to the next; z where along
    // their cycle they are now, in radians (CinematicAuroraMotion); w how far its traveling wave reaches across
    vec4 RibbonFold[MAX_RIBBONS];
    // Each ribbon's wave and sheet: x how far along it from one crest of the wave to the next; y how far the wave has
    // traveled; z how far its rays have (CinematicAuroraMotion); w how thick its sheet is
    vec4 RibbonWave[MAX_RIBBONS];
    // Each ribbon's light: x how bright it is now; y how far it rises above its lower edge; z how distinct its rays are,
    // 0 to 1; w where along the noise its patches start
    vec4 RibbonLight[MAX_RIBBONS];
    // Each ribbon's curl (CinematicAurora.Curl): xy its middle on the ground, in the world's x and z; z how far out its
    // twist reaches; w how far it turns the layer at its middle now, in radians (0: no curl)
    vec4 RibbonCurl[MAX_RIBBONS];
};
