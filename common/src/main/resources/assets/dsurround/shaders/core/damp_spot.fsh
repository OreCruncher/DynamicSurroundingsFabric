#version 150

// Damp spots on the ground after rain. Usually drawn with a blend that darkens what is behind by the alpha, where the
// colour plays no part, so the fog fades the alpha: a spot deep in the fog darkens the fogged ground less, until it
// is lost in it. linear_fog_fade from fog.glsl is copied in rather than imported.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

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

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    color.a *= linear_fog_fade(vertexDistance, FogStart, FogEnd);
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
