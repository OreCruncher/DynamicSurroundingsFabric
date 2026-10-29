#version 330

// Dynamic Surroundings point light (a firefly's, a flame jet's): lights the scene already drawn, near the light. For
// each pixel, where in the world it is comes from a copy of the scene's depth, so this lights the grass, leaves and
// ground around the light rather than a flat disc. Added to what is there (see PointLights).

#moj_import <minecraft:projection.glsl>

uniform sampler2D DepthSampler;

layout(std140) uniform PointLightInfo {
    // 1 if clip space depth runs 0 to 1, 0 if it runs -1 to 1
    float DepthZeroToOne;
};

flat in vec3 lightCenter;
flat in vec4 lightColor;
flat in float lightRadius;

out vec4 fragColor;

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    // Nothing there but sky: the depth buffer is cleared to 1, the far end of its range
    if (depth >= 1.0) {
        discard;
    }

    // Where the scene is at this pixel, in view space
    vec2 screen = gl_FragCoord.xy / vec2(textureSize(DepthSampler, 0));
    float ndcZ = DepthZeroToOne > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = inverse(ProjMat) * vec4(screen * 2.0 - 1.0, ndcZ, 1.0);
    vec3 position = view.xyz / view.w;

    vec3 toLight = lightCenter - position;
    float dist = length(toLight);
    if (dist >= lightRadius) {
        discard;
    }

    // Fades smoothly to nothing at the radius
    float falloff = 1.0 - dist / lightRadius;
    falloff *= falloff;

    // Which way the surface faces, from how its position changes between neighbouring pixels; turned toward the
    // camera. Surfaces facing the light are lit most, those facing away barely at all, so a wall between the light
    // and the camera mostly stays dark.
    vec3 normal = normalize(cross(dFdx(position), dFdy(position)));
    if (dot(normal, position) > 0.0) {
        normal = -normal;
    }
    float facing = clamp((dot(normal, toLight / max(dist, 0.0001)) + 0.25) / 1.25, 0.0, 1.0);

    // The light's colour scaled by its strength, with next to no alpha: both are added as they are
    float strength = lightColor.a * falloff * facing;
    fragColor = vec4(lightColor.rgb * strength, 1.0 / 255.0);
}
