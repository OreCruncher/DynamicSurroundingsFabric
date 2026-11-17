// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Depth buffer values as distances, for the soft particles and the weather. The shader including it declares
// DepthZeroToOne (1 if clip space depth runs 0 to 1, 0 if it runs -1 to 1) and includes minecraft:projection.glsl first.

// A depth buffer value as a distance from the camera in blocks, along the view direction. Inverts the perspective
// projection: for a view space z, ndc = (m22 * z + m32) / -z. Holds whichever way round the depth range runs.
float view_distance(float depth) {
    float ndc = DepthZeroToOne > 0.5 ? depth : depth * 2.0 - 1.0;
    return ProjMat[3][2] / (ndc + ProjMat[2][2]);
}
