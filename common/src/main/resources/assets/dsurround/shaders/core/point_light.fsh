#version 330
#extension GL_ARB_separate_shader_objects : require
// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

// Dynamic Surroundings point lights (fireflies', flame jets'): light the scene already drawn, near each light. Where in
// the world a pixel is comes from a copy of the scene's depth, so they light the grass, leaves and ground around them
// rather than flat discs. All of the frame's lights in one pass: the depth is read, and the position and the way the
// surface faces worked out, once for the pixel, then each light whose part of the screen it is in adds its light.
// Added to what is there (see PointLights).

#include <dsurround:point_lights.glsl>

uniform sampler2D DepthSampler;

layout(location = 0) out vec4 fragColor;

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    // Nothing there but sky: the depth buffer is cleared to 0, the far end of its range
    if (depth <= 0.0) {
        discard;
    }

    // Where the scene is at this pixel, in view space
    vec2 ndc = gl_FragCoord.xy / vec2(textureSize(DepthSampler, 0)) * 2.0 - 1.0;
    float ndcZ = Params.x > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = InvProjMat * vec4(ndc, ndcZ, 1.0);
    vec3 position = view.xyz / view.w;

    // Which way the surface faces, from how its position changes between neighboring pixels; turned toward the
    // camera. Surfaces facing a light are lit most, those facing away barely at all, so a wall between the light and
    // the camera mostly stays dark.
    vec3 normal = normalize(cross(dFdx(position), dFdy(position)));
    if (dot(normal, position) > 0.0) {
        normal = -normal;
    }

    vec3 light = vec3(0.0);
    float lit = 0.0;
    int count = int(Params.y + 0.5);
    for (int i = 0; i < count; i++) {
        vec4 bounds = Lights[i * 3 + 2];
        if (ndc.x < bounds.x || ndc.y < bounds.y || ndc.x > bounds.z || ndc.y > bounds.w) {
            continue;
        }
        vec4 center = Lights[i * 3];
        vec3 toLight = center.xyz - position;
        float dist2 = dot(toLight, toLight);
        if (dist2 >= center.w * center.w) {
            continue;
        }
        float dist = sqrt(dist2);
        // Fades smoothly to nothing at the radius
        float falloff = 1.0 - dist / center.w;
        falloff *= falloff;
        float facing = clamp((dot(normal, toLight / max(dist, 0.0001)) + 0.25) / 1.25, 0.0, 1.0);
        // The light's color scaled by its strength
        vec4 color = Lights[i * 3 + 1];
        light += color.rgb * (color.a * falloff * facing);
        lit += 1.0;
    }
    if (lit == 0.0) {
        discard;
    }
    // Next to no alpha for each light, as each added when they were drawn one at a time
    fragColor = vec4(light, lit / 255.0);
}
