#version 150

// Dynamic Surroundings lightning (see LightningRenderer). Light falls off from the middle of a ribbon or a patch of
// cloud to nothing at its edge. A channel has a white hot core along its middle; a patch of cloud is broken up by
// noise into the cloud's own texture. Drawn with additive blending.

uniform sampler2D NoiseSampler;
// 1 for a channel, with its core; 0 for the cloud's glow, without
uniform float CoreStrength;
// With Fabulous graphics, 1: the light comes with next to no alpha, for the transparency pass to add as it is
uniform float AddOnly;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// The core's half width, as a share of the ribbon's: one over LightningRenderer.HALO
const float CORE = 0.125;
// A patch's noise cell is added to UV0.x this many apart: LightningRenderer.NOISE_CELL_STEP
const float CELL_STEP = 4.0;
// LightningRenderer.NOISE_SIZE
const float NOISE_SIZE = 128.0;

// Smooth value noise from the noise texture, in one read (see NoiseTexture)
float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return texture(NoiseSampler, (cell + f + 0.5) / NOISE_SIZE).r;
}

void main() {
    // Where in the ribbon or patch, from its middle; and a patch's noise cell (0 for a ribbon)
    float cell = floor(texCoord0.x / CELL_STEP + 0.5);
    vec2 local = vec2(texCoord0.x - cell * CELL_STEP, texCoord0.y);
    float d = length(local);
    if (d >= 1.0) {
        discard;
    }

    float brightness = vertexColor.a * 2.0;
    vec3 light;
    if (CoreStrength > 0.5) {
        float halo = 1.0 - d;
        halo = halo * halo * halo * 0.6;
        float core = 1.0 - smoothstep(CORE * 0.5, CORE, d);
        light = (vertexColor.rgb * halo + vec3(core)) * brightness;
    } else {
        // Lumpy cloud: two octaves, each patch from its own part of the texture. Thick parts catch the light, thin
        // parts barely show.
        vec2 p = local * 3.0 + vec2(cell * 7.3, cell * 3.1);
        float n = noise(p) * 0.65 + noise(p * 2.1 + 17.0) * 0.35;
        float lumps = smoothstep(0.2, 0.85, n);
        float falloff = 1.0 - d;
        falloff *= falloff;
        light = vertexColor.rgb * falloff * mix(0.15, 1.5, lumps) * brightness;
    }

    // Too faint to show: left alone, so no depth is written for it
    if (max(light.r, max(light.g, light.b)) < 0.004) {
        discard;
    }
    fragColor = vec4(light, AddOnly > 0.5 ? 1.0 / 255.0 : 0.0);
}
