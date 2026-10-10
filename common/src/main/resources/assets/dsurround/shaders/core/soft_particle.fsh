#version 150

// Vanilla's particle fragment shader (shaders/core/particle.fsh), made "soft": the particle fades out as it nears
// whatever is behind it (terrain, the surface of water), so where it passes into them it blends away instead of
// being cut off in a hard line. linear_fog from fog.glsl is copied in rather than imported.

uniform sampler2D Sampler0;
// A copy of the scene's depth, made just before the particles are drawn
uniform sampler2D DepthSampler;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform mat4 ProjMat;
// How far in front of what is behind it, in blocks, the particle is at full strength
uniform float SoftDistance;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
    if (vertexDistance <= fogStart) {
        return inColor;
    }

    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

// A depth buffer value as a distance from the camera in blocks, along the view direction. Inverts the perspective
// projection: for a view space z, depth = (m22 * z + m32) / -z in normalized device coordinates.
float linear_depth(float depth) {
    float ndc = depth * 2.0 - 1.0;
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

    float sceneDistance = linear_depth(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r);
    float particleDistance = linear_depth(gl_FragCoord.z);
    color.a *= clamp((sceneDistance - particleDistance) / SoftDistance, 0.0, 1.0);

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
