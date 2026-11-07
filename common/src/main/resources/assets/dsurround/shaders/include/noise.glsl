// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Smooth value noise from a noise texture (NoiseTexture), for the aurora, the realistic aurora, ground fog and
// lightning. The shader including it declares the texture, NoiseSampler, and NOISE_SIZE, its size in texels, first.

// Smooth value noise, 0 to 1, a cell to a texel of the noise texture, repeating every NOISE_SIZE cells. The texture is
// read between texels in a straight line; moving the point within its cell along an S-curve first rounds that off, as
// working out the four corners would, in one read
float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return texture(NoiseSampler, (cell + f + 0.5) / NOISE_SIZE).r;
}
