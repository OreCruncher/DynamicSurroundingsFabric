#version 150

// Particle rain, snow and dust. A drop fades into the fog rather than taking on its colour: the ground behind it is
// already fogged, so a fogged drop would look the same, and fading also suits drops already faint at their tops.
// Dust is drawn "soft", as the soft particle shader draws mist: a puff fades out as it nears whatever is behind it, so
// where it passes into the ground it blends away instead of ending in a hard line. linear_fog_fade from fog.glsl is
// copied in rather than imported.

uniform sampler2D Sampler0;
// A copy of the scene's depth, made just before the dust is drawn; only read when SoftDistance is set
uniform sampler2D DepthSampler;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform mat4 ProjMat;
// How far in front of what is behind it, in blocks, a puff is at full strength; 0 to draw hard, as rain and snow are
uniform float SoftDistance;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float linear_fog_fade(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 1.0;
    } else if (vertexDistance >= fogEnd) {
        return 0.0;
    }

    return smoothstep(fogEnd, fogStart, vertexDistance);
}

// A depth buffer value as a distance from the camera in blocks, along the view direction. Inverts the perspective
// projection: for a view space z, depth = (m22 * z + m32) / -z in normalized device coordinates.
float linear_depth(float depth) {
    float ndc = depth * 2.0 - 1.0;
    return ProjMat[3][2] / (ndc + ProjMat[2][2]);
}

void main() {
    vec4 color = vertexColor * ColorModulator;
    // Rain streaks have no texture: the whole quad is given a coordinate below zero
    if (texCoord0.x >= 0.0) {
        color *= texture(Sampler0, texCoord0);
    }
    if (SoftDistance > 0.0) {
        float sceneDistance = linear_depth(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r);
        float dropDistance = linear_depth(gl_FragCoord.z);
        color.a *= clamp((sceneDistance - dropDistance) / SoftDistance, 0.0, 1.0);
    }
    color.a *= linear_fog_fade(vertexDistance, FogStart, FogEnd);
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
