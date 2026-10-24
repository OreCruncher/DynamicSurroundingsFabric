#version 330

// Frost on windows: quads lit by the lightmap, in vanilla's particle vertex format. The colour's alpha is not an alpha
// but how far the frost has grown there (see FrostRenderer), so the light is applied to the colour alone.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    texCoord0 = UV0;
    vec4 light = sample_lightmap(Sampler2, UV2);
    vertexColor = vec4(Color.rgb * light.rgb, Color.a);
}
