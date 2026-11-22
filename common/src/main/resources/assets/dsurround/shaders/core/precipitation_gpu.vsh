#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Particle rain, snow and dust built on the GPU: each drop's quad made here from its packed record, rather than written by
// the CPU every frame (PrecipitationRenderer.build, whose math this follows step for step). No vertex buffer: four
// vertices a drop, drawn as quads, the drop from the vertex's index and the corner from its last two bits, as vanilla's
// clouds read their faces. The records are PackedDrops', uploaded as they are into a texel buffer, five texels a drop.
// Drawn in batches (GpuDrops.BATCH_DROPS), each from a buffer of its own, so no texel buffer is larger than every GPU
// must allow; the vertex index starts again at 0 in each. The rain and snow are one draw, the dust another (soft, with
// the particle atlas): each has its own records and uniform block. The looks (snowflakes, or dust sprites) are in the
// uniform block. Hands the fragment shader what precipitation.vsh does.
//
// The pipeline sets the constants (GpuDrops), so they stay those of PrecipitationRenderer and PrecipitationField.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(std140) uniform DropInfo {
    // Where the packed drops' anchor is from the camera; the partial tick
    vec4 Offset;
    // The camera's left; the field's radius
    vec4 Left;
    // The camera's up; how big snow is drawn
    vec4 Up;
    // The rain's color; how big rain is drawn
    vec4 Rain;
    // How far every flake or puff has turned since its last tick, as a cos and sin; how many looks there are; the
    // largest snowflake's size
    vec4 Turn;
    // Each look's texture rectangle (u0, v0, u1, v1): the snowflakes, or the dust's sprites. The snowflakes' sizes in
    // pixels, four to a vec4.
    vec4 LookRects[MAX_FLAKES];
    vec4 FlakeSizes[MAX_FLAKES / 4];
};

uniform samplerBuffer Drops;
uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec2 texCoord0;
layout(location = 3) out vec4 vertexColor;

// PrecipitationField.fade
float drop_fade(float kind, float ticksOld, float lifetime, float covered, float partialTick) {
    float age = ticksOld + partialTick;
    float fade = kind == KIND_SNOW ? min(1.0, age / SNOW_FADE_IN)
            : kind == KIND_DUST ? clamp(min(age, lifetime - age) / DUST_FADE, 0.0, 1.0)
            : min(1.0, age / RAIN_FADE_IN);
    if (covered > 0.0) {
        float lasts = kind == KIND_SNOW ? SNOW_COVER_TICKS : kind == KIND_DUST ? DUST_COVER_TICKS : RAIN_COVER_TICKS;
        fade *= clamp(1.0 - (covered - 1.0 + partialTick) / lasts, 0.0, 1.0);
    }
    return fade;
}

// PrecipitationRenderer.thinned
float thinned(float thinning, float outward, float radius) {
    float from = radius * THIN_FROM_SHARE;
    float across = clamp((outward - from) / (radius - from), 0.0, 1.0);
    float share = mix(1.0, THIN_KEEP_AT_EDGE, across);
    return clamp((share - thinning) / THIN_FADE + 1.0, 0.0, 1.0);
}

void hide() {
    // Outside the view: the whole quad is clipped
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    sphericalVertexDistance = 0.0;
    cylindricalVertexDistance = 0.0;
    texCoord0 = vec2(NO_TEXTURE);
    vertexColor = vec4(0.0);
}

void main() {
    int corner = gl_VertexIndex & 3;
    int looks = int(Turn.z);
    int at = (gl_VertexIndex >> 2) * 5;
    // prevX prevY prevZ moveX | moveY moveZ kind light | age covered lifetime thinning | size pick A B | C
    vec4 t0 = texelFetch(Drops, at);
    vec4 t1 = texelFetch(Drops, at + 1);
    vec4 t2 = texelFetch(Drops, at + 2);
    vec4 t3 = texelFetch(Drops, at + 3);
    float c = texelFetch(Drops, at + 4).x;

    float partialTick = Offset.w;
    float radius = Left.w;
    vec3 p = t0.xyz + partialTick * vec3(t0.w, t1.x, t1.y) + Offset.xyz;
    float kind = t1.z;
    float outward = length(p.xz);
    float fade = drop_fade(kind, t2.x, t2.z, t2.y, partialTick) * clamp((radius - outward) / EDGE_FADE, 0.0, 1.0);
    // Far rain and snow are thinned out; puffs of dust close by are not drawn (PrecipitationRenderer.dustNearFade)
    fade *= kind == KIND_DUST ? clamp((length(p) - DUST_NEAR_HIDDEN) / DUST_NEAR_FADE, 0.0, 1.0) : thinned(t2.w, outward, radius);
    // Light and color are whole numbers in a float (PackedDrops.whole)
    int light = int(t1.w);
    float size = t3.x;
    vec3 left = Left.xyz;
    vec3 up = Up.xyz;

    vec3 pos;
    vec2 uv;
    vec4 color;
    if (kind != KIND_RAIN) {
        // Snowflakes and puffs of dust: squares facing the camera, turning
        float cosTurn = t3.z * Turn.x - t3.w * Turn.y;
        float sinTurn = t3.w * Turn.x + t3.z * Turn.y;
        vec4 rect = vec4(NO_TEXTURE);
        int variant = clamp(int(t3.y * float(looks)), 0, max(looks - 1, 0));
        if (looks > 0) {
            rect = LookRects[variant];
        }
        float halfSize;
        if (kind == KIND_DUST) {
            halfSize = DUST_MIN_HALF_SIZE + DUST_HALF_SIZE_RANGE * size;
            // The color of the ground it was blown from, paler
            int ground = int(c);
            vec3 tint = vec3(float((ground >> 16) & 0xFF), float((ground >> 8) & 0xFF), float(ground & 0xFF)) / 255.0;
            color = vec4(mix(tint, vec3(1.0), DUST_PALENESS), DUST_ALPHA * fade);
        } else {
            halfSize = (SNOW_MIN_HALF_SIZE + SNOW_HALF_SIZE_RANGE * size) * Up.w;
            if (looks > 0) {
                halfSize *= FlakeSizes[variant >> 2][variant & 3] / Turn.w;
            }
            color = vec4(1.0, 1.0, 1.0, SNOW_ALPHA * fade);
        }
        // PrecipitationRenderer.billboard
        vec3 a = (left * cosTurn + up * sinTurn) * halfSize;
        vec3 b = (up * cosTurn - left * sinTurn) * halfSize;
        if (corner == 0) {
            pos = p - a - b;
            uv = rect.zw;
        } else if (corner == 1) {
            pos = p - a + b;
            uv = rect.zy;
        } else if (corner == 2) {
            pos = p + a + b;
            uv = rect.xy;
        } else {
            pos = p + a - b;
            uv = rect.xw;
        }
    } else {
        float streakLength = (RAIN_MIN_LENGTH + RAIN_LENGTH_RANGE * size) * Rain.w;
        float halfWidth = (RAIN_MIN_HALF_WIDTH + RAIN_HALF_WIDTH_RANGE * size) * sqrt(max(Rain.w, 1.0));
        // Along the way the drop is moving, its tail trailing, turned about its length to face the camera
        vec3 v = vec3(t3.z, t3.w, c);
        vec3 s = cross(v, p);
        float across = dot(s, s);
        s = across > 1.0e-6 ? s * (halfWidth * inversesqrt(across)) : left * halfWidth;
        vec3 tail = p - v * streakLength;
        float alpha = (RAIN_MIN_ALPHA + RAIN_ALPHA_RANGE * size) * fade;
        if (corner == 0) {
            pos = p - s;
        } else if (corner == 1) {
            pos = tail - s;
        } else if (corner == 2) {
            pos = tail + s;
        } else {
            pos = p + s;
        }
        uv = vec2(NO_TEXTURE);
        color = vec4(Rain.rgb, corner == 1 || corner == 2 ? 0.0 : alpha);
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    texCoord0 = uv;
    vertexColor = color * sample_lightmap(Sampler2, ivec2(light & 0xFFFF, (light >> 16) & 0xFFFF));
    // Faded out entirely: nothing of it is drawn
    if (fade <= 0.0) {
        hide();
    }
}
