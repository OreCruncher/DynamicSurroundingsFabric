#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Dynamic Surroundings halo and sun dogs (HaloRenderer), drawn over the sky with their light added to it.
//
// The halo is a ring 22 degrees from the sun: tumbling ice crystals bend no light less than that, and most just past
// it, so the ring has a sharp inner edge, faintly red (red light is bent least), and is white beyond it: the crystals
// tumble every way, so the colors overlap all but at the edge, and a halo looks white with a warm rim, not a rainbow
// (the rainbow-colored rings close round the sun are a corona, from water droplets). The sun dogs are
// spots level with the sun either side, where flat crystals drifting level send the light: 22 degrees out with the
// sun on the horizon, further as it climbs (HaloConditions.sunDogAzimuth, for each color). Each has the same sharp red
// edge toward the sun and a white tail away from it.
//
// Sky style Blocky (Style.x) lays the sky in blocks fixed around the sun, as the rainbow's, each block one flat
// color. The ring: a warm block at its edge, then white. The sun dogs, made by crystals that don't tumble, keep their
// colors: red, orange, then pale stepping down.

#include <dsurround:halo.glsl>

layout(location = 0) in vec2 screenPos;

layout(location = 0) out vec4 fragColor;

// A direction, or the one given where there is none: with the sun straight overhead it has no direction across the
// ground and the blocks no up (at noon with the daylight cycle held, 0/0 made the whole halo NaN: black within 60
// degrees of the sun), and a pixel straight up has none across the ground
vec2 direction(vec2 v, vec2 otherwise) {
    return dot(v, v) > 1.0e-12 ? normalize(v) : otherwise;
}

vec3 direction(vec3 v, vec3 otherwise) {
    return dot(v, v) > 1.0e-12 ? normalize(v) : otherwise;
}

// The cosine of the furthest from the sun anything is drawn: 60 degrees, past the sun dogs' tails with the sun as high
// as they show
const float OUTERMOST = 0.5;
// The halo's inner edge for red, green and blue light, in degrees, and how far out it fades
const vec3 HALO_EDGE = vec3(21.6, 22.0, 22.5);
const float HALO_FADE = 2.0;
// How much of its colors the ring keeps: the edges' colors overlap in the eye as much as in the crystals, so a real
// halo's rim is only faintly warm
const float HALO_SATURATION = 0.3;
// How bright the halo is next to the sun dogs, at their brightest
const float HALO_BRIGHTNESS = 0.45;
// How bright the sun dogs are at their brightest: a little more than the ring, as they gather the light of two
// directions into one spot, but not glaring
const float DOG_BRIGHTNESS = 0.6;
// How far a sun dog's tail reaches, in degrees, and how tall it is
const float DOG_TAIL = 3.5;
const float DOG_HEIGHT = 1.4;
// How much of the sky behind the light covers, for each of its brightest channel, as the rainbow's
const float COVER = 0.75;
const float BLOCKY_COVER = 1.0;
// Blocky: the size of the sky's blocks, in degrees, as the rainbow's
const float BLOCK = 0.5;
const vec3 RED = vec3(1.0, 0.25, 0.12);
const vec3 ORANGE = vec3(1.0, 0.6, 0.2);
const vec3 PALE = vec3(1.0, 0.92, 0.65);
// Blocky: the ring's warm edge, and the white beyond it
const vec3 HALO_RIM = vec3(1.0, 0.72, 0.55);
const vec3 HALO_WHITE = vec3(1.0, 0.97, 0.93);

void main() {
    // The pixel's direction: from one point along its line of sight to another further along it, so view bobbing
    // doesn't swing it (see the realistic aurora's)
    vec4 near = InvViewProj * vec4(screenPos, 1.0, 1.0);
    vec4 far = InvViewProj * vec4(screenPos, 0.5, 1.0);
    vec3 view = normalize(far.xyz / far.w - near.xyz / near.w);
    float toward = dot(view, Sun.xyz);
    if (toward < OUTERMOST || view.y < -0.05) {
        discard;
    }

    bool blocky = Style.x > 0.5;
    float angle = degrees(acos(min(toward, 1.0)));
    if (blocky) {
        // Blocks fixed in the sky: a square grid laid flat around the sun, true to the angle out from it and around
        // it, each block taken as its middle
        vec3 up = direction(vec3(0.0, 1.0, 0.0) - Sun.xyz * Sun.y, vec3(1.0, 0.0, 0.0));
        vec3 side = cross(Sun.xyz, up);
        float around = atan(dot(view, side), dot(view, up));
        vec2 grid = angle * vec2(sin(around), cos(around));
        vec2 middle = (floor(grid / BLOCK) + 0.5) * BLOCK;
        angle = length(middle);
        around = atan(middle.x, middle.y);
        float a = radians(angle);
        view = cos(a) * Sun.xyz + sin(a) * (cos(around) * up + sin(around) * side);
    }

    // Where the pixel is beside the sun: its height against the sun's, and how far round from it
    float height = degrees(asin(clamp(view.y, -1.0, 1.0))) - Dogs.w;
    vec2 flatView = direction(view.xz, vec2(1.0, 0.0));
    vec2 flatSun = direction(Sun.xz, vec2(1.0, 0.0));
    float aside = abs(degrees(atan(flatView.x * flatSun.y - flatView.y * flatSun.x, dot(flatView, flatSun))));

    vec3 light;
    if (blocky) {
        // The ring: a warm block at its edge, then white fading, a block each
        float ring = floor((angle - HALO_EDGE.r) / BLOCK);
        vec3 halo = vec3(0.0);
        if (ring >= 0.0 && ring < 3.0)
            halo = (ring < 1.0 ? HALO_RIM * 0.8 : ring < 2.0 ? HALO_WHITE : HALO_WHITE * 0.5) * HALO_BRIGHTNESS;
        // The sun dogs: the same out along the level, four blocks tall
        float along = floor((aside - Dogs.g) / BLOCK + 0.5);
        vec3 dog = vec3(0.0);
        if (along >= 0.0 && along < 6.0 && abs(height) < BLOCK * 2.0)
            dog = along < 1.0 ? RED : along < 2.0 ? ORANGE : PALE * (1.0 - (along - 2.0) * 0.2);
        light = max(halo, dog * (DOG_BRIGHTNESS * Sunlight.a));
    } else {
        // Each color's sharp inner edge, then all of them fading out together from the last, so that past the rim
        // the ring is white
        vec3 past = angle - HALO_EDGE;
        vec3 halo = smoothstep(-0.4, 0.15, past) * exp(-max(angle - HALO_EDGE.b, 0.0) / HALO_FADE) * HALO_BRIGHTNESS;
        halo = mix(vec3(dot(halo, vec3(1.0 / 3.0))), halo, HALO_SATURATION);
        vec3 beyond = aside - Dogs.xyz;
        vec3 dog = smoothstep(-0.35, 0.15, beyond) * exp(-max(beyond, 0.0) / DOG_TAIL)
                * exp(-height * height / (2.0 * DOG_HEIGHT * DOG_HEIGHT));
        light = halo + dog * (DOG_BRIGHTNESS * Sunlight.a);
    }

    float foot = smoothstep(-0.05, 0.03, view.y);
    vec3 color = light * Sunlight.rgb * (Sun.w * foot);
    float cover = blocky ? BLOCKY_COVER : COVER;
    fragColor = vec4(color, min(max(color.r, max(color.g, color.b)) * cover, 1.0));
}
