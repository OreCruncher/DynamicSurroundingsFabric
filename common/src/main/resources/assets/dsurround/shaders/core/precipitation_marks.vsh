#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// What the drops leave where they land, shaped on the GPU from records packed once a tick (PackedMarks), as the CPU's
// builds in PrecipitationRenderer do step for step: snow flecks lying on the face they landed on, melting; drops on
// windows, a bead and the wet trail above it (two quads a record); and, with SPOTS set by the pipeline, damp spots
// darkening the ground. No vertex buffer: four vertices a quad, the mark from the vertex's index. Three texels a record,
// in batches of buffers of their own (GpuMarks), the vertex index starting again at 0 in each. Hands the fragment shader
// what precipitation.vsh does (damp_spot.vsh, for the spots).
//
// The pipeline sets the constants (GpuMarks), so they stay those of the Java classes.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#ifndef SPOTS
#include <minecraft:sample_lightmap.glsl>
#endif

layout(std140) uniform MarkInfo {
    // Where the packed marks' anchor is from the camera; the partial tick
    vec4 Offset;
    // The rain's color (the drops on windows); which marks are drawn (MARK_FLECKS or MARK_STREAKS)
    vec4 Rain;
    // How big snow is drawn; how many snowflakes there are; the largest's size
    vec4 Looks;
    // The damp spots' sprite's texture rectangle (u0, v0, u1, v1)
    vec4 SpotRect;
    // Each snowflake's texture rectangle (u0, v0, u1, v1), and their sizes in pixels, four to a vec4
    vec4 LookRects[MAX_FLAKES];
    vec4 FlakeSizes[MAX_FLAKES / 4];
};

uniform samplerBuffer Marks;
#ifndef SPOTS
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec2 texCoord0;
layout(location = 3) out vec4 vertexColor;

// PrecipitationRenderer.fleckCorner: offset from the center by (u, v) in the plane of the face it lies on
vec3 on_face(float face, vec3 p, float u, float v) {
    if (face == FACE_WEST || face == FACE_EAST) {
        return p + vec3(0.0, v, u);
    }
    if (face == FACE_NORTH || face == FACE_SOUTH) {
        return p + vec3(u, v, 0.0);
    }
    return p + vec3(u, 0.0, v);
}

void main() {
    int corner = gl_VertexIndex & 3;
    int quad = gl_VertexIndex >> 2;
#ifdef SPOTS
    int mark = quad;
    int part = 0;
#else
    bool streaks = Rain.w == MARK_STREAKS;
    int mark = streaks ? quad >> 1 : quad;
    int part = streaks ? quad & 1 : 0;
#endif
    int at = mark * 3;
    vec4 t0 = texelFetch(Marks, at);
    vec4 t1 = texelFetch(Marks, at + 1);
    vec4 t2 = texelFetch(Marks, at + 2);
    float partialTick = Offset.w;
    float age = t1.x + partialTick;
    float lifetime = t1.y;

    vec3 pos;
    vec2 uv = vec2(NO_TEXTURE);
    vec4 color;
    // How much of it shows: nothing is drawn at 0
    float shown;
    int light = 0;
#ifdef SPOTS
    // x y z peak | age lifetime cornerX cornerZ (PrecipitationRenderer.build, DampSpots.darkness)
    vec3 p = t0.xyz + Offset.xyz;
    float remaining = 1.0 - age / lifetime;
    float darkness = age >= lifetime ? 0.0 : t0.w * PEAK_DARKNESS * min(1.0, age / DARKEN_TICKS) * remaining * remaining;
    float cx = t1.z;
    float cz = t1.w;
    if (corner == 0) {
        pos = p + vec3(cx, 0.0, cz);
        uv = SpotRect.xy;
    } else if (corner == 1) {
        pos = p + vec3(-cz, 0.0, cx);
        uv = SpotRect.xw;
    } else if (corner == 2) {
        pos = p + vec3(-cx, 0.0, -cz);
        uv = SpotRect.zw;
    } else {
        pos = p + vec3(cz, 0.0, -cx);
        uv = SpotRect.zy;
    }
    color = vec4(1.0, 1.0, 1.0, darkness);
    shown = darkness;
#else
    float face = t0.w;
    light = floatBitsToInt(t2.z);
    if (!streaks) {
        // x y z face | age lifetime size variant | turnCos turnSin light - (SnowFlecks.remaining, sizeFor)
        vec3 p = t0.xyz + Offset.xyz;
        float remaining = age >= lifetime ? 0.0 : 1.0 - clamp((age / lifetime - WHOLE_SHARE) / (1.0 - WHOLE_SHARE), 0.0, 1.0);
        float halfSize = (SNOW_MIN_HALF_SIZE + SNOW_HALF_SIZE_RANGE * t1.z) * Looks.x * mix(MELTED_SIZE, 1.0, remaining);
        int looks = int(Looks.y);
        vec4 rect = vec4(NO_TEXTURE);
        if (looks > 0) {
            int variant = clamp(int(t1.w * float(looks)), 0, looks - 1);
            rect = LookRects[variant];
            halfSize *= FlakeSizes[variant >> 2][variant & 3] / Looks.z;
        }
        // The corners, each a quarter turn from the last, as far out as a falling flake's
        float reach = halfSize * SQRT_OF_TWO;
        float cu = t2.x * reach;
        float cv = t2.y * reach;
        if (corner == 0) {
            pos = on_face(face, p, cu, cv);
            uv = rect.xy;
        } else if (corner == 1) {
            pos = on_face(face, p, -cv, cu);
            uv = rect.xw;
        } else if (corner == 2) {
            pos = on_face(face, p, -cu, -cv);
            uv = rect.zw;
        } else {
            pos = on_face(face, p, cv, -cu);
            uv = rect.zy;
        }
        color = vec4(1.0, 1.0, 1.0, SNOW_ALPHA * remaining);
        shown = remaining;
    } else {
        // x prevY z face | age lifetime size y | prevTrailTop trailTop light - (WindowStreaks.fade)
        vec3 p = vec3(t0.x, mix(t0.y, t1.w, partialTick), t0.z) + Offset.xyz;
        float trailTop = mix(t2.x, t2.y, partialTick) + Offset.y;
        float fade = clamp(min(age / STREAK_FADE_IN, (lifetime - age) / STREAK_FADE_OUT), 0.0, 1.0);
        float halfSize = STREAK_BEAD_MIN_HALF_SIZE + STREAK_BEAD_HALF_SIZE_RANGE * t1.z;
        float alpha = STREAK_BEAD_ALPHA * fade;
        float u;
        float v;
        if (part == 0) {
            // The bead
            u = corner == 0 || corner == 3 ? -halfSize : halfSize;
            v = corner < 2 ? -halfSize : halfSize;
            color = vec4(Rain.rgb, alpha);
            shown = fade;
        } else {
            // The trail above it, narrower and fainter, fading out toward its top
            float trail = trailTop - p.y - halfSize;
            float width = halfSize * STREAK_TRAIL_WIDTH;
            u = corner == 0 || corner == 3 ? -width : width;
            v = corner < 2 ? halfSize : halfSize + trail;
            color = vec4(Rain.rgb, corner < 2 ? alpha * STREAK_TRAIL_ALPHA : 0.0);
            shown = trail > 0.0 ? fade : 0.0;
        }
        pos = on_face(face, p, u, v);
    }
#endif

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    texCoord0 = uv;
#ifdef SPOTS
    vertexColor = color;
#else
    vertexColor = color * sample_lightmap(Sampler2, ivec2(light & 0xFFFF, (light >> 16) & 0xFFFF));
#endif
    // Gone: the whole quad is clipped
    if (shown <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    }
}
