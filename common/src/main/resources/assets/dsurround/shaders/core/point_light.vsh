#version 330

// Dynamic Surroundings point light (a firefly's, a flame jet's): a small light, lighting whatever is near it on
// screen. All four vertices of a light are at its centre; this spreads them into a square facing the camera that
// covers the light's sphere on screen. When the camera is in or right next to the sphere, the square covers the whole
// screen instead.

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;  // the light's centre, relative to the camera
in vec2 UV0;       // which corner, at the light's radius: -radius or radius on each axis
in vec4 Color;     // the light's colour; alpha is its strength

flat out vec3 lightCenter;  // in view space
flat out vec4 lightColor;
flat out float lightRadius;

void main() {
    vec3 center = (ModelViewMat * vec4(Position, 1.0)).xyz;
    float radius = abs(UV0.x);
    vec2 corner = sign(UV0);
    lightCenter = center;
    lightColor = Color;
    lightRadius = radius;

    // No early return: Veil's shader parser (glsl-processor) fails on a return without a value
    float dist = length(center);
    if (dist < radius * 1.05) {
        gl_Position = vec4(corner, 0.0, 1.0);
    } else {
        // A square through the centre, square to the view of it, as wide as the sphere looks from here
        vec3 dir = center / dist;
        vec3 up = abs(dir.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
        vec3 right = normalize(cross(dir, up));
        up = cross(right, dir);
        float halfSize = radius * dist / sqrt(dist * dist - radius * radius);
        vec3 cornerPosition = center + (right * corner.x + up * corner.y) * halfSize;
        gl_Position = ProjMat * vec4(cornerPosition, 1.0);
    }
}
