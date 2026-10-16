#version 150

// Ground fog as a volume. Each pixel follows its line of sight from the camera to what it shows (from a copy of the
// scene's depth) and adds up the mist along the way, a few steps through the part of it low enough to be in the mist.
// The mist lies in the lowest MistDepth blocks over the ground, thickest at the ground; how thick depends on the
// column's biome (the grid's green), and drifting noise breaks it into patches and wisps. It is lit by the lightmap
// where the mist looked through lies, on the whole, and fades out where the distance fog takes over, as the weather
// does.
//
// Drawn at a share of the screen's size, to its own target (GroundFogVolume): a pixel for each Scale by Scale of the
// screen's (two by two at Fancy),
// following the line of sight to the first of them. The colour is multiplied by the alpha already, for blending
// smoothly when the composite (ground_fog_composite) lays it over the scene, and the depth where the mist begins along
// the line of sight is written.
//
// The grid: a texel a column, the camera's column at its middle. Red is the ground's height above a base below the
// camera in whole blocks, alpha the part of a block over; the ground is smoothed over a few blocks, so the mist banks
// up against a step and thins toward a ledge. Green is the mist's thickness, blue the block and sky light (16 each).

uniform sampler2D DepthSampler;
uniform sampler2D FogGrid;
uniform sampler2D Sampler2;
uniform sampler2D NoiseSampler;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
// From the screen back to positions relative to the camera, and the other way
uniform mat4 InvViewProj;
uniform mat4 ViewProj;
// The screen's size in pixels, which the scene's depth is
uniform vec2 FullSize;
// The screen's pixels each way for each of the mist's (GroundFogQuality): 1 at the screen's size, 2 at half, 3 at a
// third
uniform float Scale;
// The most steps along a line of sight, for the quality: no more than STEPS
uniform float MaxSteps;
// The camera in the grid's terms: a block to a texel, heights above the grid's base
uniform vec3 CameraLocal;
// Where the grid's first column is in the world, wrapped at WRAP, so the noise stays put as the grid moves
uniform vec2 NoiseOrigin;
// How far the mist has drifted, in blocks, and a time in seconds for it to churn
uniform vec3 Drift;
// Where the grid's base is in the world, for noise that stays put as the camera goes up and down
uniform float BaseY;
uniform float GridSize;
// Where the mist has thinned to nothing, in blocks from the camera across the ground, and over how many blocks before
// that it thins
uniform vec2 EdgeFade;
// The top of the mist anywhere, above the base: nothing is looked for above it
uniform float MistTop;
uniform float MistDepth;
// How quickly the mist hides what is behind it, for each block of it at full thickness looked through
uniform float Thickness;

out vec4 fragColor;

// The most steps along a line of sight, and the fewest: a short stretch through the mist (looking down into it close
// by) needs fewer, about STEPS_PER_BLOCK for each block of it
const int STEPS = 24;
const int MIN_STEPS = 8;
// The stretches of the steps the mist is lit in, each where its mist is on the whole
const int LIGHT_POINTS = 3;
const float STEPS_PER_BLOCK = 2.0;
// Once this much mist has been looked through (at Thickness), what is behind it is 99% hidden: the rest of the line
// of sight can't change the pixel
const float OPAQUE = 4.6;
const float MAX_DISTANCE = 48.0;
const vec3 MIST_COLOR = vec3(0.92, 0.93, 0.95);
// How far below the smoothed ground mist is still found: it may be a block above the ground itself (see
// GroundFogField.SMOOTH_ABOVE), so this much below it is still over the ground
const float UNDER_GROUND = 1.1;
// Where things are east-west and north-south is wrapped at this many blocks (GroundFogVolume.NOISE_WRAP)
const float WRAP = 4620.0;
// The noise texture's size, which it repeats at (GroundFogVolume.NOISE_SIZE)
const float NOISE_SIZE = 60.0;
// How far, in blocks, the swirling pushes the wisps about
const float SWIRL = 3.0;
// How much the wisps change with height. Small: much more and they lean steeply, so the clear patches seem to move as
// the camera rises and falls through the mist (seen from above, the tops of the wisps; from inside, their lower parts)
const float WISP_LEAN = 0.1;
// Detail is only seen where the steps along the line of sight are short enough to follow it: a step longer than about
// half of it lands somewhere different in it for each pixel, which shows as a stipple. So each fades to its average
// between these step lengths, in blocks: the wisps (3.5 blocks across) and the tufts (1.75)
const vec2 WISP_FADE = vec2(1.5, 3.0);
const vec2 TUFT_FADE = vec2(0.75, 1.75);
// The wisps also fade to their average between these distances from the camera, in blocks. Seen from low down, the gaps
// between them are squashed into stripes further off; real mist reads as an even haze there
const vec2 WISP_DISTANCE = vec2(12.0, 30.0);
// The share of the mist left in a gap between the wisps: a thinner place rather than a hole
const float WISP_FLOOR = 0.25;
// The wisps' average, with that floor: what they are scaled by to leave the mist as thick on average as without it
const float WISP_MEAN = 0.5 * (1.0 + WISP_FLOOR);
// How much the tufts thin and thicken the mist, either way: they leave it as thick on average
const float FINE_DEPTH = 0.45;

// How the steps lengthen along the line of sight: how far along it, 0 to 1, a share of the steps reaches, as that share
// to the power 1.5. Over the furthest 48 blocks, the first step is under half a block and the last 3 blocks
float spread(float share) {
    return share * sqrt(share);
}

float linear_fog_fade(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 1.0;
    } else if (vertexDistance >= fogEnd) {
        return 0.0;
    }

    return smoothstep(fogEnd, fogStart, vertexDistance);
}

// Smooth value noise, 0 to 1, a cell to a texel of the noise texture, repeating every NOISE_SIZE cells: where things
// are is wrapped at a whole number of those for each noise (GroundFogVolume.NOISE_WRAP), so a wrap lands on the same
// noise. The texture is read between texels in a straight line; moving the point within its cell along an S-curve
// first rounds that off, as working out the four corners did, in one read
float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return texture(NoiseSampler, (cell + f + 0.5) / NOISE_SIZE).r;
}

// A column's light, from the lightmap
vec3 columnLight(ivec2 cell) {
    cell = clamp(cell, ivec2(0), ivec2(int(GridSize) - 1));
    float packed = floor(texelFetch(FogGrid, cell, 0).b * 255.0 + 0.5);
    return texelFetch(Sampler2, ivec2(floor(packed / 16.0), mod(packed, 16.0)), 0).rgb;
}

// The light at a point, blended between the four nearest columns: one column's alone changes at each block's edge,
// which shows as straight-edged patches where the light changes (under trees, beside a wall). The block and sky light
// are packed together, so they can't be blended in the grid; their colours are
vec3 lightAt(vec2 xz) {
    vec2 q = xz - 0.5;
    ivec2 cell = ivec2(floor(q));
    vec2 f = fract(q);
    vec3 near = mix(columnLight(cell), columnLight(cell + ivec2(1, 0)), f.x);
    vec3 far = mix(columnLight(cell + ivec2(0, 1)), columnLight(cell + ivec2(1, 1)), f.x);
    return mix(near, far, f.y);
}

// The mist at a point, in the grid's terms, fromCamera blocks along the line of sight, sampled for a step of stepLength
// blocks
float mist(vec3 p, float fromCamera, float stepLength) {
    if (p.x < 0.0 || p.z < 0.0 || p.x >= GridSize || p.z >= GridSize) {
        return 0.0;
    }
    // The ground and the thickness, blended smoothly between columns: the mist's top rounds off over a ledge rather
    // than showing each column's square corner. Below the blended ground (near the foot of a step) is at the ground:
    // nothing solid is reached, as the line of sight ends at the first block
    vec4 smoothed = texture(FogGrid, p.xz / GridSize);
    // No mist in this column (or the columns it is blended with): nothing more to work out
    if (smoothed.g <= 0.0) {
        return 0.0;
    }
    float above = p.y - (smoothed.r * 255.0 + smoothed.a);
    // The smoothed ground is never more than a block above the ground itself: further below it than that is in the
    // ground or under it (a cave, beneath an overhang), where the surface's mist doesn't reach
    if (above > MistDepth || above < -UNDER_GROUND) {
        return 0.0;
    }
    above = max(above, 0.0);
    // Thinning toward the edge of the area, from where the camera is
    float edge = clamp((EdgeFade.x - length(p.xz - CameraLocal.xz)) / EdgeFade.y, 0.0, 1.0);
    if (edge <= 0.0) {
        return 0.0;
    }
    // Thickest at the ground, thinning to nothing at the top: to the power 1.5
    float below = 1.0 - above / MistDepth;
    float profile = below * sqrt(below);
    // Large slow patches it gathers in, and wisps drifting and churning across them, a little different at each
    // height; both where the point is in the world, so they stay put as the camera moves
    vec2 at = p.xz + NoiseOrigin - Drift.xy;
    float patches = 0.35 + 0.65 * smoothstep(0.25, 0.75, noise(at / 11.0 + vec2(Drift.z * 0.02, 0.0)));
    float m = smoothed.g * edge * profile * patches;
    // Where the steps are too long for the wisps and tufts, or (the wisps) further off, they are only their average:
    // their noise needn't be read. Often, as the steps lengthen with distance
    float wispDetail = (1.0 - smoothstep(WISP_FADE.x, WISP_FADE.y, stepLength))
            * (1.0 - smoothstep(WISP_DISTANCE.x, WISP_DISTANCE.y, fromCamera));
    float tuftDetail = 1.0 - smoothstep(TUFT_FADE.x, TUFT_FADE.y, stepLength);
    if (wispDetail <= 0.0 && tuftDetail <= 0.0) {
        return m * 0.5;
    }
    // The wisps are pushed about by a slowly changing field, so they curl and eddy rather than only sliding along
    vec2 swirl = vec2(noise(at / 7.0 + vec2(Drift.z * 0.04, 0.0)),
                      noise(at / 7.0 + vec2(19.0, -Drift.z * 0.04))) - 0.5;
    // Never clearing entirely between them, and scaled so the mist is as thick on average as without the floor. Half,
    // their average, where the steps are too long for them, and further off
    float wisp = 0.5;
    if (wispDetail > 0.0) {
        float wisps = noise((at + swirl * SWIRL) / 3.5 + vec2((p.y + BaseY) * WISP_LEAN, Drift.z * 0.09));
        wisp = mix(0.5, 0.5 * mix(WISP_FLOOR, 1.0, smoothstep(0.2, 0.8, wisps)) / WISP_MEAN, wispDetail);
    }
    m *= wisp;
    // Close by, small tufts within the wisps, churning faster and changing more with height, so the mist near the
    // camera has texture and keeps changing where it is rather than only sliding past
    if (m > 0.0 && tuftDetail > 0.0) {
        float tufts = noise((at + swirl * SWIRL) / 1.75 + vec2((p.y + BaseY) * 0.3, Drift.z * 0.25));
        m *= 1.0 + tuftDetail * FINE_DEPTH * (tufts * 2.0 - 1.0);
    }
    return m;
}

void main() {
    // The screen's pixel this one stands for: the first of its Scale by Scale (the smoothing and composite read the
    // same)
    ivec2 pixel = min(ivec2(gl_FragCoord.xy) * int(Scale), ivec2(FullSize) - 1);
    float depth = texelFetch(DepthSampler, pixel, 0).r;
    vec2 screenUV = (vec2(pixel) + 0.5) / FullSize;
    vec4 clip = vec4(screenUV * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewed = InvViewProj * clip;
    vec3 end = viewed.xyz / viewed.w;
    float length_ = length(end);
    if (length_ <= 0.0) {
        discard;
    }
    vec3 dir = end / length_;
    vec3 start = CameraLocal;

    // Only the part of the line low enough to be in the mist
    float t0 = 0.0;
    float t1 = min(length_, MAX_DISTANCE);
    if (start.y > MistTop) {
        if (dir.y >= 0.0) {
            discard;
        }
        t0 = (MistTop - start.y) / dir.y;
    } else if (dir.y > 0.0) {
        t1 = min(t1, (MistTop - start.y) / dir.y);
    }
    if (t1 <= t0) {
        discard;
    }

    // Stepping through, offset a little per pixel so the steps don't show as bands: interleaved gradient noise, which
    // spreads the offsets evenly between neighbouring pixels rather than at random, so it shows as less grain. The
    // steps lengthen with distance (spread): short close by, where the wisps and tufts are seen, and long further
    // off, where only the patches are. Steps too long for what they pass over show as a stipple
    float span = t1 - t0;
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float amount = 0.0;
    // Where the mist passed is, on the whole, in each of LIGHT_POINTS stretches of the steps: the points along the way,
    // each counted by how much mist is there, and how much mist that is
    vec3 mistSums[LIGHT_POINTS];
    float amounts[LIGHT_POINTS];
    for (int s = 0; s < LIGHT_POINTS; s++) {
        mistSums[s] = vec3(0.0);
        amounts[s] = 0.0;
    }
    // Where along the line the mist begins
    float begins = -1.0;
    int steps = int(clamp(ceil(span * STEPS_PER_BLOCK), float(MIN_STEPS), min(MaxSteps, float(STEPS))));
    float stepCount = float(steps);
    // How far along the line, 0 to 1, this step begins: where the last ended
    float stepStart = 0.0;
    for (int i = 0; i < STEPS; i++) {
        if (i >= steps || amount * Thickness > OPAQUE) {
            break;
        }
        float t = t0 + span * spread((float(i) + jitter) / stepCount);
        float stepEnd = spread(float(i + 1) / stepCount);
        float stepLength = span * (stepEnd - stepStart);
        stepStart = stepEnd;
        vec3 p = start + dir * t;
        float m = mist(p, t, stepLength);
        amount += m * stepLength;
        int stretch = min(i * LIGHT_POINTS / steps, LIGHT_POINTS - 1);
        mistSums[stretch] += p * (m * stepLength);
        amounts[stretch] += m * stepLength;
        if (begins < 0.0 && m > 0.0) {
            begins = t;
        }
    }
    float alpha = 1.0 - exp(-amount * Thickness);
    if (alpha <= 0.002) {
        discard;
    }

    // Where the mist begins: with Fabulous graphics, the composite writes it to the weather's target, which vanilla's
    // transparency pass sorts by
    vec4 clipAt = ViewProj * vec4(dir * max(begins, t0), 1.0);
    gl_FragDepth = clamp(clipAt.z / clipAt.w * 0.5 + 0.5, 0.0, 1.0);

    // Lit where the mist passed is on the whole, in each stretch, blended by how much each is seen: as much as it
    // hides, less what the mist in front of it hides. Not at any one point along the way: each pixel's steps are offset
    // a little differently, and where the light changes from block to block (around a torch) neighbouring pixels would
    // be lit from different columns, which shows as a stipple. And not at one place for the whole line of sight: where
    // the light changes along it (from a torch's to the moon's), that shows as bands by distance
    vec3 light = vec3(0.0);
    float seen = 0.0;
    float hiddenBefore = 0.0;
    for (int s = 0; s < LIGHT_POINTS; s++) {
        if (amounts[s] > 0.0) {
            float shows = exp(-hiddenBefore * Thickness) * (1.0 - exp(-amounts[s] * Thickness));
            light += lightAt(mistSums[s].xz / amounts[s]) * shows;
            seen += shows;
        }
        hiddenBefore += amounts[s];
    }
    light /= max(seen, 1e-6);
    alpha *= linear_fog_fade(t0 + (t1 - t0) * 0.5, FogStart, FogEnd);
    fragColor = vec4(MIST_COLOR * light, alpha) * ColorModulator;
    fragColor.rgb *= fragColor.a;
}
