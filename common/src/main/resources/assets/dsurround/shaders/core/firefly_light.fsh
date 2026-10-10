#version 330

// Dynamic Surroundings firefly light: lights the scene already drawn, near the light. For each pixel, where in the
// world it is comes from a copy of the scene's depth, so this lights the grass, leaves and ground around a firefly
// rather than a flat disc. Drawn with additive blending.

#moj_import <minecraft:projection.glsl>

uniform sampler2D DepthSampler;

layout(std140) uniform FireflyLightInfo {
    // 1 if clip space depth runs 0 to 1, 0 if it runs -1 to 1
    float DepthZeroToOne;
    float LightRadius;
};

flat in vec3 lightCenter;
flat in vec4 lightColor;

out vec4 fragColor;

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    // Nothing there but sky: the depth buffer is cleared to 0, the far end of its range
    if (depth <= 0.0) {
        discard;
    }

    // Where the scene is at this pixel, in view space
    vec2 screen = gl_FragCoord.xy / vec2(textureSize(DepthSampler, 0));
    float ndcZ = DepthZeroToOne > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = inverse(ProjMat) * vec4(screen * 2.0 - 1.0, ndcZ, 1.0);
    vec3 position = view.xyz / view.w;

    vec3 toLight = lightCenter - position;
    float dist = length(toLight);
    if (dist >= LightRadius) {
        discard;
    }

    // Fades smoothly to nothing at the radius
    float falloff = 1.0 - dist / LightRadius;
    falloff *= falloff;

    // Which way the surface faces, from how its position changes between neighbouring pixels; turned toward the
    // camera. Surfaces facing the light are lit most, those facing away barely at all, so a wall between the light
    // and the camera mostly stays dark.
    vec3 normal = normalize(cross(dFdx(position), dFdy(position)));
    if (dot(normal, position) > 0.0) {
        normal = -normal;
    }
    float facing = clamp((dot(normal, toLight / max(dist, 0.0001)) + 0.25) / 1.25, 0.0, 1.0);

    fragColor = vec4(lightColor.rgb, lightColor.a * falloff * facing);
}
