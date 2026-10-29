#version 330

// Damp spots on the ground after rain. Usually drawn with a blend that darkens what is behind by the alpha, where the
// colour plays no part, so the fog fades the alpha: a spot deep in the fog darkens the fogged ground less, until it
// is lost in it.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    color.a *= 1.0 - smoothstep(0.0, 1.0, fog);
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
