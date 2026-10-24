#version 330

// Particle rain, snow and dust. A drop fades into the fog rather than taking on its colour: the ground behind it is
// already fogged, so a fogged drop would look the same, and fading also suits drops already faint at their tops.
// Snowflakes and dust are textured; rain streaks are plain colour, marked by a texture coordinate below zero.
//
// With SOFT_DISTANCE set by the pipeline (the dust), drawn "soft", as the soft particle shader draws mist: a puff fades
// out as it nears whatever is behind it, so where it passes into the ground it blends away instead of ending in a hard
// line. SOFT_DISTANCE is how far in front of what is behind it, in blocks, a puff is at full strength.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

uniform sampler2D Sampler0;

#ifdef SOFT_DISTANCE
// A copy of the scene's depth, water included, made just before the dust is drawn
uniform sampler2D DepthSampler;

layout(std140) uniform SoftParticleInfo {
    // 1 if clip space depth runs 0 to 1, 0 if it runs -1 to 1
    float DepthZeroToOne;
};

// A depth buffer value as a distance from the camera in blocks, along the view direction. Inverts the perspective
// projection: for a view space z, ndc = (m22 * z + m32) / -z. Holds whichever way round the depth range runs.
float view_distance(float depth) {
    float ndc = DepthZeroToOne > 0.5 ? depth : depth * 2.0 - 1.0;
    return ProjMat[3][2] / (ndc + ProjMat[2][2]);
}
#endif

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// How much of a drop shows through the fog: all of it before the fog starts, none past where it ends
float fog_fade() {
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    return 1.0 - smoothstep(0.0, 1.0, fog);
}

void main() {
    vec4 color = vertexColor * ColorModulator;
    // Rain streaks have no texture: the whole quad is given a coordinate below zero
    if (texCoord0.x >= 0.0) {
        color *= texture(Sampler0, texCoord0);
    }
#ifdef SOFT_DISTANCE
    float sceneDistance = view_distance(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r);
    float dropDistance = view_distance(gl_FragCoord.z);
    color.a *= clamp((sceneDistance - dropDistance) / SOFT_DISTANCE, 0.0, 1.0);
#endif
    color.a *= fog_fade();
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
