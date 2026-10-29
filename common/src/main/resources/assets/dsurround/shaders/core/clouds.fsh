#version 330

// Dynamic Surroundings clouds (see CloudRenderer). The vertex's shade times the light on the clouds, which changes
// with the time of day and the weather without the blocks being built again. Fog is measured across the ground only, not
// up: the sky's fog lies along the horizon, and rain's, which closes in to about a hundred blocks, would otherwise fog a
// deck overhead into a pale sheet. Far off they fade out, as vanilla's do toward the clouds' range, and into the
// weather's fog, rather than only taking on its color: the sky behind them, looking up, isn't the fog's color. And they
// are gone before the edge of the cloud grid, wherever the fog is. Lightning lights them from inside: up to eight points
// of light (CloudLights), each brightening the cloud around it, most where it is and none past its reach.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Kept the same as in clouds.vsh
layout(std140) uniform DSCloudInfo {
    vec4 OffsetNow;
    vec4 CloudColorFade;
    vec4 EdgeFade;
    vec4 LightPowerA;
    vec4 LightPowerB;
    vec4 Lights[8];
};

in float flatDistance;
in vec3 cloudPos;
in vec4 vertexColor;

out vec4 fragColor;

// The color of lightning's light in the cloud: LightningRenderer's glow
const vec3 GLOW = vec3(0.62, 0.64, 0.86);

// How much of one light reaches here
float lightFrom(vec4 point, float power) {
    if (power <= 0.0) {
        return 0.0;
    }
    float near = clamp(1.0 - length(cloudPos - point.xyz) / point.w, 0.0, 1.0);
    return power * near * near;
}

void main() {
    vec4 color = vec4(vertexColor.rgb * CloudColorFade.rgb, vertexColor.a) * ColorModulator;
    float lit = lightFrom(Lights[0], LightPowerA.x) + lightFrom(Lights[1], LightPowerA.y) + lightFrom(Lights[2], LightPowerA.z)
            + lightFrom(Lights[3], LightPowerA.w) + lightFrom(Lights[4], LightPowerB.x) + lightFrom(Lights[5], LightPowerB.y)
            + lightFrom(Lights[6], LightPowerB.z) + lightFrom(Lights[7], LightPowerB.w);
    color.rgb += GLOW * lit;

    // The weather's fog, across the ground: it fades them and takes on their color
    float weather = smoothstep(0.0, 1.0, linear_fog_value(flatDistance, FogEnvironmentalStart, FogEnvironmentalEnd));
    float fade = (1.0 - weather) * (1.0 - linear_fog_value(flatDistance, 0.0, FogCloudsEnd))
            * (1.0 - smoothstep(EdgeFade.x, EdgeFade.y, flatDistance));
    color.a *= fade;
    if (color.a <= 0.0) {
        discard;
    }
    color.rgb = mix(color.rgb, FogColor.rgb, weather * FogColor.a);
    fragColor = color;
}
