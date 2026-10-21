#version 150

// Frost on windows. The texture's alpha says when each texel frosts over as the frost grows (0 first, 1 never); the
// vertex alpha says how far it has grown there. A texel is shown once the frost has grown past it, with a soft edge
// as wide as a texel on screen, so it neither shimmers far off nor shows steps close up. Crystals are brighter and
// more solid than the haze between them. The colour is written premultiplied, for blending "over" (ONE,
// ONE_MINUS_SRC_ALPHA), as Fabulous graphics' transparency pass also takes it. It fades out toward the edge of the
// range windows frost in (FadeDistance), and into the fog. linear_fog_fade from fog.glsl is copied in rather than
// imported.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float FadeDistance;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// How solid the haze and the brightest crystals are
const float HAZE_ALPHA = 0.3;
const float CRYSTAL_ALPHA = 0.85;

float linear_fog_fade(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 1.0;
    } else if (vertexDistance >= fogEnd) {
        return 0.0;
    }

    return smoothstep(fogEnd, fogStart, vertexDistance);
}

void main() {
    float grown = vertexColor.a;
    if (grown <= 0.0) {
        discard;
    }
    vec4 frost = texture(Sampler0, texCoord0);
    float soft = fwidth(frost.a) + 0.02;
    float cover = 1.0 - smoothstep(grown - soft, grown + soft, frost.a);
    // Where nothing has grown yet, no frost at all, however soft the edge
    cover *= smoothstep(0.0, 0.05, grown);
    float alpha = cover * mix(HAZE_ALPHA, CRYSTAL_ALPHA, smoothstep(0.3, 1.0, frost.r)) * ColorModulator.a;
    alpha *= linear_fog_fade(vertexDistance, FogStart, FogEnd);
    alpha *= 1.0 - smoothstep(FadeDistance * 0.75, FadeDistance, vertexDistance);
    if (alpha <= 0.004) {
        discard;
    }
    vec3 color = vertexColor.rgb * ColorModulator.rgb * mix(0.75, 1.0, frost.r);
    fragColor = vec4(color * alpha, alpha);
}
