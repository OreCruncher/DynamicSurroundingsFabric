#version 330

// Dynamic Surroundings aurora: one curtain of light, drawn with additive blending.
//
// texCoord0.x runs along the curtain (in sky units, roughly 6 to 10 across the whole curtain) and texCoord0.y up it,
// from 0 at the lower edge to 1 at the top. The look:
//  - fine vertical rays: noise that changes quickly along the curtain and slowly up it
//  - large slow folds, which bend the rays sideways as they drift
//  - a sharp, bright lower edge, with the light dying away above it (further up along the brighter rays)
//  - colour by height: BottomColor at the edge, through MiddleColor, to TopColor where it fades out
//  - patches of brightness travelling along the curtain, and a faint flicker

// Colours are vec4 for std140 layout; only rgb is used
layout(std140) uniform AuroraInfo {
    vec4 BottomColor;
    vec4 MiddleColor;
    vec4 TopColor;
    float AuroraTime;  // seconds
    float Alpha;       // overall strength, 0 to 1
};

in vec2 texCoord0;
in float vertexAlpha;

out vec4 fragColor;

// Integer hash of a lattice point, 0 to 1
float hash(ivec2 p) {
    uint h = uint(p.x) * 0x8da6b343u ^ uint(p.y) * 0xd8163841u;
    h ^= h >> 16;
    h *= 0x7feb352du;
    h ^= h >> 15;
    h *= 0x846ca68bu;
    h ^= h >> 16;
    return float(h) * (1.0 / 4294967295.0);
}

// Smooth value noise, 0 to 1
float noise(vec2 p) {
    ivec2 i = ivec2(floor(p));
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + ivec2(1, 0));
    float c = hash(i + ivec2(0, 1));
    float d = hash(i + ivec2(1, 1));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

void main() {
    float x = texCoord0.x;
    float v = clamp(texCoord0.y, 0.0, 1.0);
    float t = AuroraTime;

    // Folds: shift where along the curtain the rays are taken from, slowly and by a lot, so they sway and bunch
    float fold = (noise(vec2(x * 0.6, t * 0.05)) - 0.5) * 1.0
               + (noise(vec2(x * 1.7 + 13.0, t * 0.11)) - 0.5) * 0.35;
    float rx = x + fold;

    // Rays: noise stretched up the curtain, two sizes of it, drifting
    float rays = noise(vec2(rx * 9.0, v * 0.6 + t * 0.15)) * 0.65
               + noise(vec2(rx * 23.0 - t * 0.4, v * 1.2 + 7.0)) * 0.35;
    rays = smoothstep(0.25, 0.95, rays);

    // The lower edge, waving a little
    float edge = 0.04 + 0.08 * noise(vec2(x * 2.5, t * 0.3));
    float above = max(v - edge, 0.0);
    float lower = smoothstep(edge - 0.03, edge + 0.02, v);
    // Light dies away above the edge: slowly along bright rays, quickly between them; plus a glow right at the edge
    float decay = mix(1.4, 3.6, 1.0 - rays);
    float glow = exp(-above * 18.0);
    float profile = lower * (exp(-above * decay) + 0.6 * glow);
    profile *= 1.0 - smoothstep(0.75, 1.0, v);

    // Bright patches travelling along the curtain, and a faint flicker
    float pulse = 0.55 + 0.45 * noise(vec2(x * 0.45 - t * 0.35, t * 0.07));
    float flicker = 0.9 + 0.1 * noise(vec2(x * 4.0, t * 4.0));

    float intensity = profile * (0.25 + 0.75 * rays) * pulse * flicker * vertexAlpha * Alpha;

    vec3 color = v < 0.3
        ? mix(BottomColor.rgb, MiddleColor.rgb, smoothstep(0.0, 0.3, v))
        : mix(MiddleColor.rgb, TopColor.rgb, smoothstep(0.3, 0.7, v));
    // The lower edge burns a little whiter
    color = mix(color, vec3(1.0), 0.25 * lower * glow);

    fragColor = vec4(color, clamp(intensity, 0.0, 1.0));
}
