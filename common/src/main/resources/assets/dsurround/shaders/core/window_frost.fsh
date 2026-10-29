#version 330

// Frost on windows. The texture's alpha says when each texel frosts over as the frost grows (0 first, 1 never); the
// vertex alpha says how far it has grown there. A texel is shown once the frost has grown past it, with a soft edge
// as wide as a texel on screen, so it neither shimmers far off nor shows steps close up. Crystals are brighter and
// more solid than the haze between them. The colour is written premultiplied, for blending "over" (ONE,
// ONE_MINUS_SRC_ALPHA), as Fabulous graphics' transparency pass also takes it. It fades out toward the edge of the
// range windows frost in (FADE_DISTANCE, set by the pipeline), and into the fog.
//
// With DEPTH_COPY (without Fabulous graphics), drawn after the translucent blocks with no depth test of its own: tested
// here against a copy of the scene's depth from before them, so water or stained glass in front of the frost doesn't
// hide it, while what is solid still does. Both depths come from the same projection, so they are compared as they are
// (nearer is smaller).

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
#ifdef DEPTH_COPY
uniform sampler2D DepthSampler;
#endif

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// How solid the haze and the brightest crystals are
const float HAZE_ALPHA = 0.3;
const float CRYSTAL_ALPHA = 0.85;

void main() {
#ifdef DEPTH_COPY
    if (gl_FragCoord.z > texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r) {
        discard;
    }
#endif
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
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    alpha *= 1.0 - smoothstep(0.0, 1.0, fog);
    alpha *= 1.0 - smoothstep(FADE_DISTANCE * 0.75, FADE_DISTANCE, sphericalVertexDistance);
    if (alpha <= 0.004) {
        discard;
    }
    vec3 color = vertexColor.rgb * ColorModulator.rgb * mix(0.75, 1.0, frost.r);
    fragColor = vec4(color * alpha, alpha);
}
