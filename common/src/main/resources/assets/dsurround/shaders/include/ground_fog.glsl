#version 330

// What the ground fog's passes share (GroundFogVolume): their values, and turning the scene's depth into distance.

layout(std140) uniform GroundFogInfo {
    // The screen's size in pixels, which the scene's depth is
    vec2 FullSize;
    // The screen's pixels each way for each of the mist's (GroundFogQuality): 1 at the screen's size, 2 at half, 3 at
    // a third
    float Scale;
    // The most steps along a line of sight, for the quality: no more than STEPS
    float MaxSteps;
    // The camera in the grid's terms: a block to a texel, heights above the grid's base
    vec3 CameraLocal;
    // Where the grid's base is in the world, for noise that stays put as the camera goes up and down
    float BaseY;
    // Where the grid's first column is in the world, wrapped at WRAP, so the noise stays put as the grid moves
    vec2 NoiseOrigin;
    float GridSize;
    // The top of the mist anywhere, above the base: nothing is looked for above it
    float MistTop;
    // How far the mist has drifted, in blocks, and a time in seconds for it to churn
    vec3 Drift;
    float MistDepth;
    // Where the mist has thinned to nothing, in blocks from the camera across the ground, and over how many blocks
    // before that it thins
    vec2 EdgeFade;
    // How quickly the mist hides what is behind it, for each block of it at full thickness looked through
    float Thickness;
    // 1 if clip space depth runs 0 to 1, 0 if it runs -1 to 1
    float DepthZeroToOne;
    // With Fabulous graphics, 1: the composite writes the depth where the mist begins along the line of sight, which
    // vanilla's transparency pass sorts the weather's target by. Otherwise 0, and nothing is written.
    float WriteDepth;
};

// How far nothing is: the sky, whose depth is 1 (the far plane), can't be turned into a distance
const float FAR_AWAY = 1.0e6;

// A depth buffer value as normalized device coordinates' z
float depth_to_ndc(float depth) {
    return DepthZeroToOne > 0.5 ? depth : depth * 2.0 - 1.0;
}

// Normalized device coordinates' z as a depth buffer value
float ndc_to_depth(float ndc) {
    return DepthZeroToOne > 0.5 ? ndc : ndc * 0.5 + 0.5;
}

// How far along the view the line of sight through a screen pixel goes, in blocks, from the scene's depth there and two
// rows of the projection's inverse: enough to tell whether two pixels looked about as far
float distance_at(sampler2D depthSampler, ivec2 pixel, vec4 depthRowZ, vec4 depthRowW) {
    pixel = clamp(pixel, ivec2(0), ivec2(FullSize) - 1);
    float depth = texelFetch(depthSampler, pixel, 0).r;
    if (depth >= 1.0) {
        return FAR_AWAY;
    }
    vec2 uv = (vec2(pixel) + 0.5) / FullSize;
    vec4 screen = vec4(uv * 2.0 - 1.0, depth_to_ndc(depth), 1.0);
    return min(-dot(depthRowZ, screen) / dot(depthRowW, screen), FAR_AWAY);
}
