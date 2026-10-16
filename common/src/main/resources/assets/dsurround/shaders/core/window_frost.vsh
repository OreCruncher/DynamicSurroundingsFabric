#version 150

// Frost on windows: quads lit by the lightmap, in vanilla's particle vertex format. The colour's alpha is not an alpha
// but how far the frost has grown there (see FrostRenderer). fog_distance from fog.glsl is copied in rather than
// imported.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

float fog_distance(vec3 pos, int shape) {
    if (shape == 0) {
        return length(pos);
    } else {
        float distXZ = length(pos.xz);
        float distY = abs(pos.y);
        return max(distXZ, distY);
    }
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(Position, FogShape);
    texCoord0 = UV0;
    vec4 light = texelFetch(Sampler2, UV2 / 16, 0);
    vertexColor = vec4(Color.rgb * light.rgb, Color.a);
}
