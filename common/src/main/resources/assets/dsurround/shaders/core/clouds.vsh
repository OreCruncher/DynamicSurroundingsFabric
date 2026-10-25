#version 150

// Dynamic Surroundings clouds (see CloudRenderer): blocks of cloud, each vertex's color its shade, gray, and its
// alpha how much it hides. Positions are from the corner of the cloud grid; Offset moves them to be from the camera,
// where fog is measured from: across the ground only (see clouds.fsh). A part of a cloud that is changing fades in or out over FadeTicks: UV0 is when its change
// began (x, in ticks, from when the blocks were built) and which way (y: 1 in, -1 out, 0 for a settled part, which
// doesn't fade).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// From the grid's corner to the camera's, in blocks
uniform vec3 Offset;
// Ticks since the blocks were built, with the part of a tick
uniform float Now;
uniform float FadeTicks;

// How far from the camera across the ground, for the fog and for fading out before the grid's edge
out float flatDistance;
// Where it is, from the camera, for the light of lightning inside the clouds
out vec3 cloudPos;
out vec4 vertexColor;

void main() {
    vec3 pos = Position + Offset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    flatDistance = length(pos.xz);
    cloudPos = pos;

    float fade = 1.0;
    if (UV0.y != 0.0) {
        float along = clamp((Now - UV0.x) / FadeTicks, 0.0, 1.0);
        along = along * along * (3.0 - 2.0 * along);
        fade = UV0.y > 0.0 ? along : 1.0 - along;
    }
    vertexColor = vec4(Color.rgb, Color.a * fade);
}
