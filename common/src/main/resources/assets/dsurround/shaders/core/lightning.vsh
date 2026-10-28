#version 150

// Dynamic Surroundings lightning: a channel's ribbon, or a patch of the cloud's glow around a strike. UV0 is where in
// it the vertex is, from its middle: across the ribbon (y, -1 to 1), or across the patch (x and y), with the patch's
// noise cell added to x (see lightning.fsh). The vertex colour is the light's, and its alpha half its brightness.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
}
