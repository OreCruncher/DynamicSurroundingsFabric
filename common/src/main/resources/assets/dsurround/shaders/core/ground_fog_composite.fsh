#version 330

// Lays the ground fog, drawn and smoothed at a share of the screen's size (ground_fog_volume, ground_fog_smooth), over
// the scene at its full size. Each pixel blends the two by two of the mist's pixels around it, by how near each is, as
// a texture is read between its texels. Only those that followed a line of sight to about as far as this pixel's are
// counted: the others saw something else (a tree in front, the sky beside a hill), so blending them in would smear the
// mist across the edge.
//
// The mist's pixel at (i, j) followed the line of sight to the screen's pixel at (Scale i, Scale j), so how far that
// went is read from the scene's depth there, as this pixel's own is.

#moj_import <dsurround:ground_fog.glsl>

uniform sampler2D MistSampler;
uniform sampler2D MistDepthSampler;
uniform sampler2D DepthSampler;

// Two rows of the projection's inverse (ground_fog_screen): for a pixel at (x, y, depth) on the screen, how far along
// the view it is
flat in vec4 depthRowZ;
flat in vec4 depthRowW;

out vec4 fragColor;

// How far along the view the line of sight through a screen pixel goes, in blocks
float distanceAt(ivec2 pixel) {
    return distance_at(DepthSampler, pixel, depthRowZ, depthRowW);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 halfSize = textureSize(MistSampler, 0);

    // Where this pixel is among the mist's, and the two by two around it
    vec2 at = (vec2(pixel) + 0.5) / Scale - 0.5;
    ivec2 first = ivec2(floor(at));
    vec2 f = at - vec2(first);

    // Where there is no mist around, nothing is drawn, and how far each looked needn't be worked out
    vec4 mists[4];
    ivec2 taps[4];
    float anyMist = 0.0;
    for (int k = 0; k < 4; k++) {
        taps[k] = clamp(first + ivec2(k % 2, k / 2), ivec2(0), halfSize - 1);
        mists[k] = texelFetch(MistSampler, taps[k], 0);
        anyMist = max(anyMist, mists[k].a);
    }
    if (anyMist <= 0.002) {
        discard;
    }

    // How far each one's line of sight went, and how much further or nearer than this pixel's, and the least of those
    float here = distanceAt(pixel);
    float distances[4];
    float differences[4];
    float least = 1e30;
    for (int k = 0; k < 4; k++) {
        distances[k] = distanceAt(taps[k] * int(Scale));
        differences[k] = abs(distances[k] - here);
        least = min(least, differences[k]);
    }
    // How much the distance changes from one to the next here, along a surface: the lesser change of the two pairs each
    // way, so the edge of something in front doesn't count
    float acrossX = min(abs(distances[1] - distances[0]), abs(distances[3] - distances[2]));
    float acrossY = min(abs(distances[2] - distances[0]), abs(distances[3] - distances[1]));
    // The furthest a counted one is, a step each way, times that, and a little more for the depth's precision
    float tolerance = acrossX + acrossY + 0.02 * here + 0.05;

    // Counted by how near each is, and how much further from this pixel's distance than the closest each is: usually
    // the closest is about as far, and those that looked elsewhere drop out
    vec4 sum = vec4(0.0);
    float weights = 0.0;
    // Where the mist begins, from the one counting most toward the mist here
    float most = -1.0;
    ivec2 mostAt = taps[0];
    for (int k = 0; k < 4; k++) {
        vec2 corner = vec2(k % 2, k / 2);
        vec2 share = mix(1.0 - f, f, corner);
        float w = max(share.x * share.y, 1e-3) * exp(-(differences[k] - least) / tolerance);
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

    gl_FragDepth = WriteDepth > 0.5 ? texelFetch(MistDepthSampler, mostAt, 0).r : gl_FragCoord.z;
    fragColor = mist;
}
