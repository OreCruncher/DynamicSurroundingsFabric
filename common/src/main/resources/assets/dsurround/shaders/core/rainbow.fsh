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
    float u = angle / MAX_DEGREES;
    vec3 primary = texture(SpectrumSampler, vec2(u, 0.25)).rgb;
    vec3 secondary = texture(SpectrumSampler, vec2(u, 0.75)).rgb;
    vec3 light = primary * primary + secondary * secondary * Sunlight.a;

    // Where around the bow the pixel is, 0 at its top, and how much rain there is there
    vec3 side = cross(Antisolar.xyz, Across.xyz);
    float around = atan(dot(view, side), dot(view, Across.xyz));
    float drift = Across.w * 0.01;
    // Gently and broadly: stronger or finer patches show as streaks across the bright sky inside the bow
    float clump = noise(around * 2.0 + Patches.x + drift) * 0.75 + noise(around * 4.0 - Patches.x * 1.7 - drift * 1.7) * 0.25;
    float showing = mix(0.55, 1.0, smoothstep(0.2, 0.75, clump));
    float foot = smoothstep(-0.05, 0.03, view.y);

    vec3 color = light * Sunlight.rgb * (Antisolar.w * showing * foot);
    fragColor = vec4(color, min(max(color.r, max(color.g, color.b)) * COVER, 1.0));
}
