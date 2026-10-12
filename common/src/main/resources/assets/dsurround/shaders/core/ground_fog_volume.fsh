#version 150

// Ground fog as a volume. Each pixel follows its line of sight from the camera to what it shows (from a copy of the
// scene's depth) and adds up the mist along the way, a few steps through the part of it low enough to be in the mist.
// The mist lies in the lowest MistDepth blocks over the ground, thickest at the ground; how thick depends on the
// column's biome (the grid's green), and drifting noise breaks it into patches and wisps. It is lit by the lightmap at
// the thickest point passed, and fades out where the distance fog takes over, as the weather does.
//
// The grid: a texel a column, the camera's column at its middle. Red is the ground's height above a base below the
// camera in whole blocks, alpha the part of a block over; the ground is smoothed over a few blocks, so the mist banks
// up against a step and thins toward a ledge. Green is the mist's thickness, blue the block and sky light (16 each).

uniform sampler2D DepthSampler;
uniform sampler2D FogGrid;
uniform sampler2D Sampler2;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
// From the screen back to positions relative to the camera, and the other way
uniform mat4 InvViewProj;
uniform mat4 ViewProj;
// With Fabulous graphics, 1: the mist writes the depth where it begins along the line of sight, which vanilla's
// transparency pass sorts the weather's target by. Otherwise 0, and nothing is written.
uniform float WriteDepth;
// The camera in the grid's terms: a block to a texel, heights above the grid's base
uniform vec3 CameraLocal;
// Where the grid's first column is in the world, wrapped at WRAP, so the noise stays put as the grid moves
uniform vec2 NoiseOrigin;
// How far the mist has drifted, in blocks, and a time in seconds for it to churn
uniform vec3 Drift;
// Where the grid's base is in the world, for noise that stays put as the camera goes up and down
uniform float BaseY;
uniform float GridSize;
// The top of the mist anywhere, above the base: nothing is looked for above it
uniform float MistTop;
uniform float MistDepth;
// How quickly the mist hides what is behind it, for each block of it at full thickness looked through
uniform float Thickness;

in vec2 screenUV;

out vec4 fragColor;

const int STEPS = 24;
const float MAX_DISTANCE = 48.0;
const vec3 MIST_COLOR = vec3(0.92, 0.93, 0.95);
// How far below the smoothed ground mist is still found: it may be a block above the ground itself (see
// GroundFogField.SMOOTH_ABOVE), so this much below it is still over the ground
const float UNDER_GROUND = 1.1;
// Where things are east-west and north-south is wrapped at this many blocks (GroundFogVolume.NOISE_WRAP)
const float WRAP = 4620.0;
// How far, in blocks, the swirling pushes the wisps about
const float SWIRL = 3.0;
// How much the wisps change with height. Small: much more and they lean steeply, so the clear patches seem to move as
// the camera rises and falls through the mist (seen from above, the tops of the wisps; from inside, their lower parts)
const float WISP_LEAN = 0.1;

float linear_fog_fade(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 1.0;
    } else if (vertexDistance >= fogEnd) {
        return 0.0;
    }

    return smoothstep(fogEnd, fogStart, vertexDistance);
}

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// Smooth value noise, 0 to 1, repeating every period cells: where things are is wrapped at a whole number of each
// noise's cells (GroundFogVolume.NOISE_WRAP), so a wrap lands on the same noise
float noise(vec2 p, float period) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(mod(cell, period));
    float b = hash(mod(cell + vec2(1.0, 0.0), period));
    float c = hash(mod(cell + vec2(0.0, 1.0), period));
    float d = hash(mod(cell + vec2(1.0, 1.0), period));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// The mist at a point, in the grid's terms: how thick it is, and the column's light
float mist(vec3 p, out vec4 column) {
    if (p.x < 0.0 || p.z < 0.0 || p.x >= GridSize || p.z >= GridSize) {
        column = vec4(0.0);
        return 0.0;
    }
    column = texelFetch(FogGrid, ivec2(p.xz), 0);
    // The ground and the thickness, blended smoothly between columns: the mist's top rounds off over a ledge rather
    // than showing each column's square corner. Below the blended ground (near the foot of a step) is at the ground:
    // nothing solid is reached, as the line of sight ends at the first block
    vec4 smoothed = texture(FogGrid, p.xz / GridSize);
    float above = p.y - (smoothed.r * 255.0 + smoothed.a);
    // The smoothed ground is never more than a block above the ground itself: further below it than that is in the
    // ground or under it (a cave, beneath an overhang), where the surface's mist doesn't reach
    if (above > MistDepth || above < -UNDER_GROUND) {
        return 0.0;
    }
    above = max(above, 0.0);
    // Thickest at the ground, thinning to nothing at the top
    float profile = pow(1.0 - above / MistDepth, 1.5);
    // Large slow patches it gathers in, and wisps drifting and churning across them, a little different at each
    // height; both where the point is in the world, so they stay put as the camera moves
    vec2 at = p.xz + NoiseOrigin - Drift.xy;
    float patches = 0.35 + 0.65 * smoothstep(0.25, 0.75, noise(at / 11.0 + vec2(Drift.z * 0.02, 0.0), WRAP / 11.0));
    // The wisps are pushed about by a slowly changing field, so they curl and eddy rather than only sliding along
    vec2 swirl = vec2(noise(at / 7.0 + vec2(Drift.z * 0.04, 0.0), WRAP / 7.0),
                      noise(at / 7.0 + vec2(19.0, -Drift.z * 0.04), WRAP / 7.0)) - 0.5;
    float wisps = noise((at + swirl * SWIRL) / 3.5 + vec2((p.y + BaseY) * WISP_LEAN, Drift.z * 0.09), WRAP / 3.5);
    return smoothed.g * profile * patches * smoothstep(0.2, 0.8, wisps);
}

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
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
    // spreads the offsets evenly between neighbouring pixels rather than at random, so it shows as less grain
    float stepLength = (t1 - t0) / float(STEPS);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float amount = 0.0;
    float thickest = 0.0;
    vec4 litBy = vec4(0.0);
    // Where along the line the mist begins
    float begins = -1.0;
    for (int i = 0; i < STEPS; i++) {
        float t = t0 + (float(i) + jitter) * stepLength;
        vec4 column;
        float m = mist(start + dir * t, column);
        amount += m * stepLength;
        if (begins < 0.0 && m > 0.0) {
            begins = t;
        }
        if (m > thickest) {
            thickest = m;
            litBy = column;
        }
    }
    float alpha = 1.0 - exp(-amount * Thickness);
    if (alpha <= 0.002) {
        discard;
    }

    if (WriteDepth > 0.5) {
        vec4 clipAt = ViewProj * vec4(dir * max(begins, t0), 1.0);
        gl_FragDepth = clamp(clipAt.z / clipAt.w * 0.5 + 0.5, 0.0, 1.0);
    } else {
        gl_FragDepth = gl_FragCoord.z;
    }

    float packed = floor(litBy.b * 255.0 + 0.5);
    vec3 light = texelFetch(Sampler2, ivec2(floor(packed / 16.0), mod(packed, 16.0)), 0).rgb;
    alpha *= linear_fog_fade(t0 + (t1 - t0) * 0.5, FogStart, FogEnd);
    fragColor = vec4(MIST_COLOR * light, alpha) * ColorModulator;
}
