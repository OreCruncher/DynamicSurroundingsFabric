#version 150

// The ground fog laid over the scene: a quad over the whole screen, given in normalized device coordinates

in vec3 Position;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
