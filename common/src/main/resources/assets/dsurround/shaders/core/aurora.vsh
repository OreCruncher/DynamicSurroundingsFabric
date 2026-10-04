#version 150

// Dynamic Surroundings aurora: one curtain. UV0.x runs along the curtain, UV0.y up it (0 at its lower edge, 1 at
// its top), and the vertex alpha fades it toward its ends.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out float vertexAlpha;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexAlpha = Color.a;
}
