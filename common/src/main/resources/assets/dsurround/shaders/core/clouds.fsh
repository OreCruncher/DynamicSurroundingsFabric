#version 150

// Dynamic Surroundings clouds (see CloudRenderer). The vertex's shade times the light on the clouds, which changes
// with the time of day and the weather without the blocks being built again. Fog is measured across the ground only, not
// up: the sky's fog lies along the horizon, and rain's, which closes in to about a hundred blocks, would otherwise fog a
// deck overhead into a pale sheet. Far off they fade out into the fog, rather than only taking on its color: the sky
// behind them, looking up, isn't the fog's color. And they are gone before the edge of the cloud grid, wherever the fog
// is. Lightning lights them from inside: up to eight points of light (CloudLights), each brightening the cloud around
// it, most where it is and none past its reach. linear_fog and linear_fog_fade from fog.glsl are copied in rather than
// imported.

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
// The light on the clouds' tops
uniform vec3 CloudColor;
// How far from the camera, across the ground, the clouds begin to fade out, and are gone: inside the grid's edge
uniform vec2 EdgeFade;
// The lights inside the clouds: where each is, from the camera, and its reach (w); and the power of each, 0 for none
uniform vec4 Light0;
uniform vec4 Light1;
uniform vec4 Light2;
uniform vec4 Light3;
uniform vec4 Light4;
uniform vec4 Light5;
uniform vec4 Light6;
uniform vec4 Light7;
uniform vec4 LightPowerA;
uniform vec4 LightPowerB;

in float flatDistance;
in vec3 cloudPos;
in vec4 vertexColor;

out vec4 fragColor;

vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
    if (vertexDistance <= fogStart) {
        return inColor;
    }

    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

float linear_fog_fade(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 1.0;
    } else if (vertexDistance >= fogEnd) {
        return 0.0;
    }

    return smoothstep(fogEnd, fogStart, vertexDistance);
}

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
    vec4 color = vec4(vertexColor.rgb * CloudColor, vertexColor.a) * ColorModulator;
    float lit = lightFrom(Light0, LightPowerA.x) + lightFrom(Light1, LightPowerA.y) + lightFrom(Light2, LightPowerA.z)
            + lightFrom(Light3, LightPowerA.w) + lightFrom(Light4, LightPowerB.x) + lightFrom(Light5, LightPowerB.y)
            + lightFrom(Light6, LightPowerB.z) + lightFrom(Light7, LightPowerB.w);
    color.rgb += GLOW * lit;
    float fade = linear_fog_fade(flatDistance, FogStart, FogEnd) * (1.0 - smoothstep(EdgeFade.x, EdgeFade.y, flatDistance));
    color.a *= fade;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = linear_fog(color, flatDistance, FogStart, FogEnd, FogColor);
}
