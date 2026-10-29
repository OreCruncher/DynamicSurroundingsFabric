#version 330

// Dynamic Surroundings realistic aurora, laid over the sky from where it was drawn at a share of the screen's size
// (RealisticAuroraRenderer, at FAST): a triangle over the whole screen, from the vertex's index alone, no vertex buffer.

out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    texCoord = uv;
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
}
