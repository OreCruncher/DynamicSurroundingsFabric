#version 330

// Vanilla's particle fragment shader (shaders/core/particle.fsh), made "soft": the particle fades out as it nears
// whatever is behind it (terrain, the surface of water), so where it passes into them it blends away instead of
// being cut off in a hard line.
//
// SOFT_DISTANCE, set by the pipeline, is how far in front of what is behind it, in blocks, the particle is at full
// strength.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

uniform sampler2D Sampler0;
// A copy of the scene's depth, made just before the particles are drawn
uniform sampler2D DepthSampler;

layout(std140) uniform SoftParticleInfo {
    // 1 if clip space depth runs 0 to 1, 0 if it runs -1 to 1
    float DepthZeroToOne;
};

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// A depth buffer value as a distance from the camera in blocks, along the view direction. Inverts the perspective
// projection: for a view space z, ndc = (m22 * z + m32) / -z. Holds whichever way round the depth range runs.
float view_distance(float depth) {
    float ndc = DepthZeroToOne > 0.5 ? depth : depth * 2.0 - 1.0;
    return ProjMat[3][2] / (ndc + ProjMat[2][2]);
}

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    // Vanilla discards below 0.1 alpha after the particle's own alpha is applied; this tests the texture alone, and
    // only drops what is all but invisible. A faint particle fading out (mist) would otherwise vanish all at once
    // when it crossed 0.1, and the faint outer wisps of the mist sprites would be cut off. Tested before the soft
    // fade too, so that takes the edge smoothly down to nothing.
    if (texel.a < 0.01) {
        discard;
    }
    vec4 color = texel * vertexColor * ColorModulator;

    float sceneDistance = view_distance(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r);
    float particleDistance = view_distance(gl_FragCoord.z);
    color.a *= clamp((sceneDistance - particleDistance) / SOFT_DISTANCE, 0.0, 1.0);

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
