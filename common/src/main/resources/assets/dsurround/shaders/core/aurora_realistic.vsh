#version 330

// Dynamic Surroundings realistic aurora: a triangle over the whole screen, from the vertex's index alone, no vertex
// buffer. The camera's rotation is in ModelViewMat (the sky is drawn around the camera); the inverse of it and the
// projection together, worked out here once for the three vertices, turns a pixel back into a direction.

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <dsurround:aurora_realistic.glsl>

out vec2 screenPos;
flat out mat4 invViewProj;
// Radians across a pixel, up and down the screen, at its middle
flat out float pixelAngle;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    screenPos = uv * 2.0 - 1.0;
    gl_Position = vec4(screenPos, 0.0, 1.0);

    invViewProj = inverse(ProjMat * ModelViewMat);
    pixelAngle = 2.0 / (ProjMat[1][1] * Frame.z);
}
