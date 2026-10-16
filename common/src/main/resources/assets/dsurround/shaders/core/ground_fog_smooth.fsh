#version 330

// Smooths the ground fog drawn at a share of the screen's size (ground_fog_volume), at that size: each pixel blends the
// three by three around it, the nearest counting the most. That smooths away the stipple of each one's steps being
// offset a little differently. Only those that followed a line of sight to about as far as this one's are counted: the
// others saw something else (a tree in front, the sky beside a hill), so blending them in would smear the mist across
// the edge.
// The composite (ground_fog_composite) then lays it over the scene at full size.
//
// How far each of its pixels' lines of sight went, the march wrote alongside the mist (DistanceSampler).

#moj_import <dsurround:ground_fog.glsl>

uniform sampler2D MistSampler;
uniform sampler2D MistDepthSampler;
// How far along the view each of the mist's pixels' line of sight went, from the march
uniform sampler2D DistanceSampler;

out vec4 fragColor;

// How far along the view the line of sight through one of the mist's pixels went, in blocks
float distanceAt(ivec2 at) {
    return texelFetch(DistanceSampler, at, 0).r;
}

void main() {
    ivec2 at = ivec2(gl_FragCoord.xy);
    ivec2 halfSize = textureSize(MistSampler, 0);

    // The mist around this pixel. Where there is none (the sky, above the mist, a biome without it), nothing is drawn,
    // and how far each looked needn't be worked out: much of the screen, often
    vec4 mists[9];
    ivec2 taps[9];
    float anyMist = 0.0;
    for (int k = 0; k < 9; k++) {
        taps[k] = clamp(at + ivec2(k % 3 - 1, k / 3 - 1), ivec2(0), halfSize - 1);
        mists[k] = texelFetch(MistSampler, taps[k], 0);
        anyMist = max(anyMist, mists[k].a);
    }
    if (anyMist <= 0.002) {
        discard;
    }

    // How far each one's line of sight went, and how much further or nearer than this one's, and the least of those
    float here = distanceAt(at);
    float distances[9];
    float differences[9];
    float least = 1e30;
    for (int k = 0; k < 9; k++) {
        distances[k] = distanceAt(taps[k]);
        differences[k] = abs(distances[k] - here);
        least = min(least, differences[k]);
    }

    // How much the distance changes from one to the next here, along a surface: the least change between neighbouring
    // ones, so the edge of something in front doesn't count. A surface seen at a slant (the ground, far off) changes a
    // lot from one to the next, and all of it should be blended
    float acrossX = 1e30;
    float acrossY = 1e30;
    for (int j = 0; j < 3; j++) {
        for (int i = 0; i < 2; i++) {
            acrossX = min(acrossX, abs(distances[j * 3 + i + 1] - distances[j * 3 + i]));
            acrossY = min(acrossY, abs(distances[(i + 1) * 3 + j] - distances[i * 3 + j]));
        }
    }
    // The furthest a counted one is, a step each way, times that, and a little more for the depth's precision
    float tolerance = 1.5 * (acrossX + acrossY) + 0.02 * here + 0.05;

    // Counted by how much further from this one's distance than the closest each is. Usually the closest is this one,
    // and those that looked elsewhere drop out. Where none is as far (a strip too thin to be drawn at this size), the
    // closest few are blended
    vec4 sum = vec4(0.0);
    float weights = 0.0;
    // Where the mist begins, from the one counting most toward the mist here
    float most = -1.0;
    ivec2 mostAt = at;
    for (int k = 0; k < 9; k++) {
        vec2 offset = vec2(k % 3 - 1, k / 3 - 1);
        float w = exp(-dot(offset, offset) * 0.6) * exp(-(differences[k] - least) / tolerance);
        sum += mists[k] * w;
        weights += w;
        if (w * mists[k].a > most) {
            most = w * mists[k].a;
            mostAt = taps[k];
        }
    }
    vec4 mist = sum / weights;
    if (mist.a <= 0.002) {
        discard;
    }

    gl_FragDepth = texelFetch(MistDepthSampler, mostAt, 0).r;
    fragColor = mist;
}
