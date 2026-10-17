#version 330

// The ground fog's passes: a triangle over the whole of what is drawn to, from the vertex's index alone (as vanilla's
// screenquad), no vertex buffer. The matrices the passes need are worked out here, once for the three vertices, from
// those the world was drawn with (view bobbing and all), and handed on unchanged.

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// From positions relative to the camera to the screen, and back
flat out mat4 viewProj;
flat out mat4 invViewProj;
// Two rows of the projection's inverse: for a pixel at (x, y, depth) on the screen, how far along the view it is
flat out vec4 depthRowZ;
flat out vec4 depthRowW;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);

    viewProj = ProjMat * ModelViewMat;
    invViewProj = inverse(viewProj);
    mat4 invProj = inverse(ProjMat);
    depthRowZ = vec4(invProj[0][2], invProj[1][2], invProj[2][2], invProj[3][2]);
    depthRowW = vec4(invProj[0][3], invProj[1][3], invProj[2][3], invProj[3][3]);
}
