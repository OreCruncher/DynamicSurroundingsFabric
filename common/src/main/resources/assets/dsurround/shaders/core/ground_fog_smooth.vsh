#version 150

// The ground fog smoothed at half size: a quad over the whole target, given in normalized device coordinates

in vec3 Position;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
