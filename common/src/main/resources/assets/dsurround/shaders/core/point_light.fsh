#version 150

// Dynamic Surroundings point light (a firefly's, a flame jet's): lights the scene already drawn, near the light. For
// each pixel, where in the world it is comes from a copy of the scene's depth, so this lights the grass, leaves and
// ground around the light rather than a flat disc. Drawn with additive blending.

uniform sampler2D DepthSampler;
uniform mat4 InverseProjMat;
uniform vec2 ScreenSize;
// With Fabulous graphics, 1: the light's colour comes scaled by its strength, with next to no alpha, for adding as it
// is (see PointLights)
uniform float AddOnly;

flat in vec3 lightCenter;
flat in vec4 lightColor;
flat in float lightRadius;

out vec4 fragColor;

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    // Nothing there but sky
    if (depth >= 1.0) {
        discard;
    }

    // Where the scene is at this pixel, in view space
    vec3 ndc = vec3(gl_FragCoord.xy / ScreenSize, depth) * 2.0 - 1.0;
    vec4 view = InverseProjMat * vec4(ndc, 1.0);
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

    float strength = lightColor.a * falloff * facing;
    fragColor = AddOnly > 0.5 ? vec4(lightColor.rgb * strength, 1.0 / 255.0) : vec4(lightColor.rgb, strength);
}
