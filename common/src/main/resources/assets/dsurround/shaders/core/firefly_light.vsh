#version 330

// Dynamic Surroundings firefly light: a small point light, lighting whatever is near it on screen. All four vertices
// of a light are at its centre; this spreads them into a square facing the camera that covers the light's sphere
// on screen. When the camera is in or right next to the sphere, the square covers the whole screen instead.

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

layout(std140) uniform FireflyLightInfo {
    // 1 if clip space depth runs 0 to 1, 0 if it runs -1 to 1
    float DepthZeroToOne;
    float LightRadius;
};

in vec3 Position;  // the light's centre, relative to the camera
in vec2 UV0;       // which corner: -1 or 1 on each axis
in vec4 Color;     // the light's colour; alpha is its strength

flat out vec3 lightCenter;  // in view space
flat out vec4 lightColor;

void main() {
    vec3 center = (ModelViewMat * vec4(Position, 1.0)).xyz;
    lightCenter = center;
    lightColor = Color;

    float dist = length(center);
    if (dist < LightRadius * 1.05) {
        gl_Position = vec4(UV0, 0.0, 1.0);
        return;
    }

    // A square through the centre, square to the view of it, as wide as the sphere looks from here
    vec3 dir = center / dist;
    vec3 up = abs(dir.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 right = normalize(cross(dir, up));
    up = cross(right, dir);
    float halfSize = LightRadius * dist / sqrt(dist * dist - LightRadius * LightRadius);
    vec3 corner = center + (right * UV0.x + up * UV0.y) * halfSize;
    gl_Position = ProjMat * vec4(corner, 1.0);
}
