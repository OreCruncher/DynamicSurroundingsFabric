#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Dynamic Surroundings rainbow (RainbowRenderer), drawn over the sky with its light added to it.
//
// Each pixel's direction is set against the point opposite the sun: the angle between them is all a rainbow depends
// on. The light the rain sends back at that angle, for the primary bow and for the secondary, comes from the spectrum
// texture (RainbowSpectrum, the square root of each channel). The rain isn't an even curtain, so the bow is brighter in
// some stretches than others, drifting slowly; and it fades a little at its foot, where it meets the horizon.
//
// Its light doesn't only add to the sky: it covers part of what is behind it (COVER), as much as it is bright. Added
// alone onto a bright blue sky, whose blue is already near full, every color of the bow came out nearly white.
//
// Sky style Blocky (Patches.y) draws it to suit the game instead: the sky in blocks fixed around the point opposite
// the sun, each block one flat color by the ring of the bow it falls in, six stripes a block wide, red to violet.
//
// A moonbow is the same opposite the moon, in moonlight (Sunlight, dimmed), its colors paled (Patches.z).

#include <dsurround:rainbow.glsl>

// Two rows over 0 to MAX_DEGREES from the point opposite the sun: the primary bow's light, then the secondary's
uniform sampler2D SpectrumSampler;

layout(location = 0) in vec2 screenPos;

layout(location = 0) out vec4 fragColor;

// The cosine of the furthest from the point opposite the sun any light is drawn: 56 degrees, past the secondary bow's
// softened outer edge. The light outside the secondary, brighter than Alexander's band, fades on out to here
const float OUTERMOST = 0.5592;
// How much of the sky behind the bow its light covers, for each of its brightest channel
const float COVER = 0.75;

// Sky style Blocky: the sky laid out in blocks this size, in degrees, about a texel of the sun's, and each block
// one flat color by the ring of the bow its middle falls in, a block wide each
const float BLOCK = 0.5;
// The primary's outer edge, red, as the real bow's; the secondary's inner edge, red too, its colors reversed
const float PRIMARY_EDGE = 42.4;
const float SECONDARY_EDGE = 50.4;
// How bright the secondary is, next to the primary, and the sky inside the primary
const float BLOCKY_SECONDARY = 0.3;
const float BLOCKY_INSIDE = 0.1;
// How much of the sky behind the stripes they cover, for each of their brightest channel: more than the real bow's, so
// they show as solid colors and not pale ones
const float BLOCKY_COVER = 1.0;
const vec3 STRIPES[6] = vec3[6](
    vec3(1.0, 0.1, 0.06),    // red
    vec3(1.0, 0.45, 0.04),   // orange
    vec3(1.0, 0.9, 0.08),    // yellow
    vec3(0.15, 0.8, 0.15),   // green
    vec3(0.1, 0.35, 1.0),    // blue
    vec3(0.5, 0.15, 0.9)     // violet
);

float hash(float n) {
    return fract(sin(n) * 43758.5453);
}

float noise(float x) {
    float i = floor(x);
    float f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash(i), hash(i + 1.0), f);
}

void main() {
    // The pixel's direction: from one point along its line of sight to another further along it, so view bobbing
    // doesn't swing it (see the realistic aurora's)
    vec4 near = InvViewProj * vec4(screenPos, 1.0, 1.0);
    vec4 far = InvViewProj * vec4(screenPos, 0.5, 1.0);
    vec3 view = normalize(far.xyz / far.w - near.xyz / near.w);
    float toward = dot(view, Antisolar.xyz);
    if (toward < OUTERMOST || view.y < -0.05) {
        discard;
    }

    float angle = degrees(acos(min(toward, 1.0)));
    // Where around the bow the pixel is, 0 at its top
    vec3 side = cross(Antisolar.xyz, Across.xyz);
    float around = atan(dot(view, side), dot(view, Across.xyz));
    bool blocky = Patches.y > 0.5;

    vec3 light;
    if (blocky) {
        // Blocks fixed in the sky: a square grid laid flat around the point opposite the sun, true to the angle out
        // from it and around it, each block taken as its middle, so the bow steps around in blocks
        vec2 grid = angle * vec2(sin(around), cos(around));
        vec2 middle = (floor(grid / BLOCK) + 0.5) * BLOCK;
        angle = length(middle);
        around = atan(middle.x, middle.y);
        float a = radians(angle);
        view = cos(a) * Antisolar.xyz + sin(a) * (cos(around) * Across.xyz + sin(around) * side);

        light = vec3(0.0);
        float inPrimary = (PRIMARY_EDGE - angle) / BLOCK;
        float inSecondary = (angle - SECONDARY_EDGE) / BLOCK;
        if (inPrimary >= 0.0 && inPrimary < 6.0)
            light = STRIPES[int(inPrimary)];
        else if (inPrimary >= 6.0)
            // The brighter sky inside the bow, stepping down toward its middle
            light = vec3(BLOCKY_INSIDE * max(0.0, 1.0 - floor((inPrimary - 6.0) / 4.0) * 0.25));
        else if (inSecondary >= 0.0 && inSecondary < 6.0)
            light = STRIPES[int(inSecondary)] * (BLOCKY_SECONDARY * Sunlight.a);
    } else {
        float u = angle / MAX_DEGREES;
        vec3 primary = texture(SpectrumSampler, vec2(u, 0.25)).rgb;
        vec3 secondary = texture(SpectrumSampler, vec2(u, 0.75)).rgb;
        light = primary * primary + secondary * secondary * Sunlight.a;
    }
    // A moonbow: at night eyes see little color, so it looks nearly white
    light = mix(light, vec3(dot(light, vec3(0.2126, 0.7152, 0.0722))), Patches.z);

    // How much rain there is around the bow
    float drift = Across.w * 0.01;
    // Gently and broadly: stronger or finer patches show as streaks across the bright sky inside the bow
    float clump = noise(around * 2.0 + Patches.x + drift) * 0.75 + noise(around * 4.0 - Patches.x * 1.7 - drift * 1.7) * 0.25;
    float showing = mix(0.55, 1.0, smoothstep(0.2, 0.75, clump));
    float foot = smoothstep(-0.05, 0.03, view.y);
    if (blocky) {
        // In steps, as the blocks are
        showing = floor(showing * 4.0 + 0.5) / 4.0;
        foot = floor(foot * 4.0 + 0.5) / 4.0;
    }

    vec3 color = light * Sunlight.rgb * (Antisolar.w * showing * foot);
    float cover = blocky ? BLOCKY_COVER : COVER;
    fragColor = vec4(color, min(max(color.r, max(color.g, color.b)) * cover, 1.0));
}
