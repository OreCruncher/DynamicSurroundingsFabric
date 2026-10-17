#version 330

// Dynamic Surroundings realistic aurora (RealisticAuroraRenderer), drawn over the sky with its light added to it.
//
// Each pixel's line of sight goes up from a viewer on the ground of a round Earth, through the layer of the sky where
// auroras shine (SHELL_BOTTOM to SHELL_TOP kilometres up). An arc is a sheet a few kilometres thick: it runs east-west,
// folds north and south (large folds drifting slowly, a wave and small curls travelling along it), and rises from its
// lower edge along the magnetic field, leaning south as it goes. The
// line of sight is stepped along looking for where it crosses each arc's sheet; at each crossing the light it sees is
// added all at once:
//  - how much of the sheet it goes through: its thickness, over how squarely it crosses, so a sheet seen edge on (low
//    in the north, or where it folds toward the viewer) is far brighter than one seen face on
//  - the light's colour by height: oxygen's green from just above a sharp lower edge, dying away upward; oxygen's red,
//    faint and high; nitrogen's pink in a thin fringe at the lower edge
//  - rays: fine bright streaks along the field lines, the green reaching higher along the brighter ones
// Then dimmed and reddened by the air near the horizon, and brought into range so the brightest parts turn a paler
// green rather than cutting off.

#moj_import <dsurround:aurora_realistic.glsl>

// A random value a texel, read smoothly between texels and repeating (NoiseTexture)
uniform sampler2D NoiseSampler;

in vec2 screenPos;
flat in mat4 invViewProj;
flat in float pixelAngle;

out vec4 fragColor;

// The noise texture's size, which it repeats at (RealisticAuroraRenderer.NOISE_SIZE)
const float NOISE_SIZE = 512.0;
const float EARTH_RADIUS = 6371.0;
// The layer the light is looked for in, in kilometres up
const float SHELL_BOTTOM = 80.0;
const float SHELL_TOP = 450.0;
// How far above the lower edge the red rises to its full strength, in kilometres
const float RED_RISE = 60.0;
// How sharp the lower edge is, at its sharpest, in kilometres: the light dies away below it over about this far
const float EDGE_SHARPNESS = 2.5;
// Where all light has faded out by the top of the layer, from this far below it: nothing is cut off there
const float TOP_FADE = 120.0;
// How high a ray reaches, at least and at most: each ray its own height, so the tops are ragged, as real ones are
const float LOWEST_TOP = 200.0;
const float HIGHEST_TOP = 400.0;
// The most steps along the stretch of a line of sight where an arc can be, looking for where it crosses: an arc's
// folds are a hundred kilometres and more long, far more than a step, so none is missed
const int STEPS = 24;
// The fewest steps, where the line of sight barely goes east or west through an arc; and how many rounds a crossing is
// narrowed down by
const int MIN_STEPS = 6;
const int REFINE = 3;
// Kilometres along an arc to a cell of the rays' finest noise (RealisticAuroraRenderer.FINE_RAY), and of the coarser
const float FINE_RAY = 0.7;
const float COARSE_RAY = 2.3;
// The most of a sheet a line of sight is counted as going through, in kilometres: seen exactly edge on, a sheet's
// height and length limit it
const float MAX_PATH = 25.0;
// How bright a kilometre of the light is, before it is brought into range; and how much the brightest parts pale
// toward white
const float EXPOSURE = 0.25;
const float WHITENING = 0.25;
// How much the air dims each colour, looking straight up: blue most, red least, so arcs near the horizon redden
const vec3 EXTINCTION = vec3(0.07, 0.11, 0.22);
// How quickly an arc's large folds drift, in radians a second (RealisticAurora.FOLD_DRIFT), and how far apart its curls
// are, in kilometres (RealisticAurora.CURL_WAVELENGTH)
const float FOLD_DRIFT = 0.01;
const float CURL_WAVELENGTH = 25.0;
// How far along an arc, either way, its bow has bent it half as far as it goes (RealisticAurora.BOW_LENGTH)
const float BOW_LENGTH = 600.0;
// Kilometres along an arc to each of the patches that pulse as it fades
const float PULSE_CELL = 60.0;
// The ray field: samples along a line of sight through it; kilometres across the ground to a cell of the noise that
// places its rays (RealisticAuroraRenderer.FIELD_CELL); how much of it is ray, on average, where its rays are evened
// out; and how bright a kilometre of ray is
const int FIELD_STEPS = 24;
// Fewer samples: the fewest, where a line of sight crosses few rays; and where it crosses so many between samples that
// they are evened out (FIELD_BLENDED of them or more apart), enough to follow the light's change with height. And how
// many rays apart samples are, at most, in between
const int FIELD_FEW_STEPS = 8;
const int FIELD_GLOW_STEPS = 12;
const float FIELD_BLENDED = 1.5;
const float FIELD_SAMPLE_RAYS = 0.75;
const float FIELD_CELL = 3.0;
const float FIELD_MEAN = 0.12;
const float FIELD_GLOW = 0.15;
const float TWO_PI = 6.2831853;

// Smooth value noise, 0 to 1, a cell to a texel of the noise texture (see NoiseTexture)
float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return texture(NoiseSampler, (cell + f + 0.5) / NOISE_SIZE).r;
}

// How far along a line of sight, going up at the sine mu from the ground, it reaches the height h
float shell_distance(float mu, float h) {
    float b = EARTH_RADIUS * mu;
    return -b + sqrt(b * b + h * (2.0 * EARTH_RADIUS + h));
}

// How high a line of sight going up at the sine mu is, t kilometres along it. Worked out without taking the Earth's
// radius from a number close to it, which loses a float's precision
float altitude_at(float t, float mu) {
    float q = t * t + 2.0 * EARTH_RADIUS * t * mu;
    return q / (sqrt(EARTH_RADIUS * EARTH_RADIUS + q) + EARTH_RADIUS);
}

// How far arc i's bow bends it north (south if negative), east kilometres east of the viewer: hardly at all near the
// viewer, toward its full bend at its ends (RealisticAurora.Arc.bowAt)
float arc_bow(int i, float east) {
    float e2 = east * east;
    return ArcBow[i].x * e2 / (e2 + BOW_LENGTH * BOW_LENGTH);
}

// How far north of the viewer arc i's lower edge is, east kilometres east of the viewer (RealisticAurora.Arc.northAt):
// large folds drifting slowly, a wave travelling along it, curls travelling with its rays, and its bow
float arc_north(int i, float east) {
    vec4 shape = ArcShape[i];
    vec4 wave = ArcWave[i];
    float time = Frame.x;
    float north = shape.x - shape.y * sin(east * TWO_PI / shape.z + shape.w + time * FOLD_DRIFT);
    north -= wave.x * sin((east - wave.z) * TWO_PI / wave.y);
    north -= wave.w * sin((east - ArcLight[i].z) * TWO_PI / CURL_WAVELENGTH + shape.w * 3.0);
    return north + arc_bow(i, east);
}

// How fast arc i's lower edge goes south for each kilometre east, there: the slope of arc_north, negated
float arc_bend(int i, float east) {
    vec4 shape = ArcShape[i];
    vec4 wave = ArcWave[i];
    float time = Frame.x;
    float fold = TWO_PI / shape.z;
    float crest = TWO_PI / wave.y;
    float curl = TWO_PI / CURL_WAVELENGTH;
    float e2 = east * east;
    float spread = e2 + BOW_LENGTH * BOW_LENGTH;
    return shape.y * fold * cos(east * fold + shape.w + time * FOLD_DRIFT)
         + wave.x * crest * cos((east - wave.z) * crest)
         + wave.w * curl * cos((east - ArcLight[i].z) * curl + shape.w * 3.0)
         - ArcBow[i].x * 2.0 * east * BOW_LENGTH * BOW_LENGTH / (spread * spread);
}

// How far north of arc i's sheet a point is, in kilometres: negative south of it (its lower edge, and the lean as it
// rises)
float sheet_offset(int i, float east, float north, float altitude) {
    float arcNorth = arc_north(i, east) - (altitude - Emission.x) * Emission.y;
    return north - arcNorth;
}

// The same, t kilometres along the line of sight. The line of sight is magnetic east, up and north (see main)
float offset_at(int i, vec3 dir, float t, float mu) {
    return sheet_offset(i, dir.x * t, dir.z * t, altitude_at(t, mu));
}

// Where between t0 and t1 the line of sight crosses arc i's sheet, the offset from it changing sign between them. The
// offset doesn't change evenly (the line rises along a curve, and the sheet leans), so a straight guess between the two
// can be kilometres out, which the sharp lower edge would show; a few rounds of narrowing it down (regula falsi) are
// close enough
float crossing(int i, vec3 dir, float mu, float t0, float g0, float t1, float g1) {
    for (int k = 0; k < REFINE; k++) {
        float t = t0 + (t1 - t0) * g0 / (g0 - g1);
        float g = offset_at(i, dir, t, mu);
        if ((g < 0.0) == (g0 < 0.0)) {
            t0 = t;
            g0 = g;
        } else {
            t1 = t;
            g1 = g;
        }
    }
    return t0 + (t1 - t0) * g0 / (g0 - g1);
}

// A random value from 0 to 1 for a point
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// How much of the light at a height is the blue-violet glow of a ray's top lit by the sun, above the Earth's shadow
// (Sunlit), fading further up
vec3 sunlit_at(float altitude, float above) {
    return Sunlit.rgb * smoothstep(Sunlit.w - 20.0, Sunlit.w + 20.0, altitude) * exp(-max(above, 0.0) / 120.0);
}

// A value from 0 to 1 for a pixel, from a 4 by 4 ordered (Bayer) pattern: neighbouring pixels as different as can be
float ordered_dither(vec2 pixel) {
    const float pattern[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    ivec2 cell = ivec2(mod(pixel, 4.0));
    return (pattern[cell.x + cell.y * 4] + 0.5) / 16.0;
}

// How strong the red is at a height, given how far above the lower edge it is: high up, and none below the edge,
// rising over the first RED_RISE kilometres above it, as oxygen's red is never seen low down
float red_at(float altitude, float above) {
    float d = (altitude - 230.0) / 70.0;
    return Emission.z * exp(-d * d) * smoothstep(0.0, RED_RISE, above);
}

// How much of a ray's light there is at a height, given how far up it reaches: all of it below, none of it a little
// above; and none at the top of the layer whatever the ray
float ray_top(float altitude, float top) {
    return (1.0 - smoothstep(top - 60.0, top + 30.0, altitude))
         * (1.0 - smoothstep(SHELL_TOP - TOP_FADE, SHELL_TOP, altitude));
}

// How bright a stretch of arc i is as it pulses, 0 (off) to 1 (on): each PULSE_CELL kilometres along it at a pace of
// its own, a few seconds to fifteen, as the patches of an aurora fading after its height pulse
float blink(int i, float cell) {
    float pace = mix(3.0, 15.0, hash(vec2(cell, float(i) * 17.0 + 3.0)));
    float start = hash(vec2(cell + 91.0, float(i) * 17.0 + 7.0)) * TWO_PI;
    return smoothstep(-0.3, 0.5, sin(Frame.x * TWO_PI / pace + start));
}

// The same, blended from one stretch to the next, so the patches have soft edges
float pulse_at(int i, float along) {
    float cell = along / PULSE_CELL;
    float first = floor(cell);
    return mix(blink(i, first), blink(i, first + 1.0), smoothstep(0.0, 1.0, fract(cell)));
}

// The light seen where the line of sight crosses arc i's sheet, t kilometres along it
vec3 arc_light(int i, vec3 dir, float t, float mu) {
    vec4 light = ArcLight[i];
    float time = Frame.x;
    float altitude = altitude_at(t, mu);
    float east = dir.x * t;

    // How squarely the line crosses the sheet: the sheet's normal, east, up and north, from how its offset changes.
    // Where a fold or curl turns the sheet toward the viewer, it is seen more edge on, and brightens
    vec3 normal = normalize(vec3(arc_bend(i, east), Emission.y, 1.0));
    float square = abs(dot(dir, normal));
    float thickness = light.y;
    float path = thickness / max(square, thickness / MAX_PATH);

    // Rays, two sizes of them, stretched up the field lines, moving along the arc and shimmering as they go. Where a
    // pixel spans more than a ray along the arc, its rays are evened out, rather than flicker as the view moves or
    // zig-zag where the sheet bends. A pixel spans more of the sheet the more slantwise it is seen, stretched the way the
    // line of sight runs across it; only as much of that as is along the arc spans more rays. Looking along an arc,
    // nearly edge on, its rays blend into a band; looking up a sheet overhead, along its rays, they stay distinct
    float s = east - light.w;
    vec3 lengthwise = normalize(vec3(1.0, 0.0, -normal.x / normal.z));
    vec3 slant = dir - dot(dir, normal) * normal;
    float slantLength = length(slant);
    float alongArc = slantLength > 1e-4 ? abs(dot(slant / slantLength, lengthwise)) : 0.0;
    float footprint = t * pixelAngle * (1.0 + (1.0 / max(square, 0.05) - 1.0) * alongArc);
    float fine = mix(0.5, noise(vec2(s / FINE_RAY, altitude / 40.0 + time * 0.15)),
                     1.0 - smoothstep(0.4, 1.2, footprint / FINE_RAY));
    float coarse = mix(0.5, noise(vec2(s / COARSE_RAY + 17.0, altitude / 150.0 + time * 0.03)),
                       1.0 - smoothstep(0.4, 1.2, footprint / COARSE_RAY));
    // Less distinct as the aurora calms and goes diffuse
    float rays = mix(0.55, smoothstep(0.2, 0.9, coarse * 0.6 + fine * 0.4), Activity.x);
    // Brighter and dimmer stretches along the arc, slowly changing; and patches pulsing on and off. These stay where
    // they are along it, a different place for each arc, rather than move with its rays
    float along = east + ArcShape[i].w * 150.0;
    float patches = 0.55 + 0.45 * noise(vec2(along / 60.0 + 41.0, time * 0.02));
    patches *= mix(1.0, 1.6 * pulse_at(i, along), Activity.y);

    // The lower edge: waving up and down along the arc, with a finer ripple, and each ray starting a little lower or
    // higher, the brighter ones lower, so it is ragged rather than one clean line
    float edge = Emission.x + 14.0 * (noise(vec2(along / 35.0 + 5.0, time * 0.01)) - 0.5)
               + 5.0 * (noise(vec2(s / 9.0 + 23.0, time * 0.05)) - 0.5)
               - 4.0 * (coarse - 0.5);
    float above = altitude - edge;
    // How sharp it is: a few kilometres, or a little more than a pixel spans up and down there, so far off it doesn't
    // show as a hard line
    float soft = max(EDGE_SHARPNESS, t * pixelAngle * 2.0);
    // Green from the edge up, reaching higher along the brighter rays; red high up; the fringe just at the edge
    float green = above < 0.0 ? exp(above / soft) : exp(-above / mix(18.0, 55.0, rays));
    float red = red_at(altitude, above);
    float rim = (above + 2.0) / soft;
    float fringe = Emission.w * exp(-rim * rim);
    // How far up this ray reaches: higher along the brighter, coarser rays
    float top = ray_top(altitude, mix(LOWEST_TOP, HIGHEST_TOP, clamp(coarse * 1.4 - 0.2, 0.0, 1.0)));

    float intensity = light.x * path * patches * (0.2 + 0.8 * rays) * top;
    return intensity * (GreenColor.rgb * green + RedColor.rgb * red + FringeColor.rgb * fringe + sunlit_at(altitude, above));
}

// The light seen along the line of sight through the ray field: a belt of separate rays, each a thin column along the
// magnetic field, placed by noise across the ground. It is sampled at FIELD_STEPS points through the belt, each at the
// field line through it traced down to the lower edge, so a ray is the same all the way up. Looking up along the field,
// a line of sight stays in one ray and the rays around it radiate from that point: a corona. Where a line of sight
// sweeps across many rays between samples, or a pixel spans many, they are evened out rather than flicker
vec3 ray_field(vec3 dir, float mu, float tIn, float tOut, float leanUp, float leanDown) {
    float northward = dir.z;
    float southmost = RayField.x - RayField.y - leanUp;
    float northmost = RayField.x + RayField.y + leanDown;
    float tFrom = tIn;
    float tTo = tOut;
    if (abs(northward) > 0.0001) {
        float a = southmost / northward;
        float b = northmost / northward;
        tFrom = max(tIn, min(a, b));
        tTo = min(tOut, max(a, b));
    } else if (southmost > 0.0 || northmost < 0.0) {
        return vec3(0.0);
    }
    if (tFrom >= tTo) {
        return vec3(0.0);
    }

    // How far the line of sight moves across the field lines: its angle to the field, which rises leaning south
    vec3 field = normalize(vec3(0.0, 1.0, -Emission.y));
    float across = length(cross(dir, field));
    // As many samples as show its rays. Sweeping across few of them (near the corona's middle, looking along the field)
    // or across so many that they are evened out into a glow, fewer do as well as more; in between, enough that no
    // more than FIELD_SAMPLE_RAYS rays pass between two
    float rays = (tTo - tFrom) * across / FIELD_CELL;
    int samples = rays > float(FIELD_STEPS) * FIELD_BLENDED ? FIELD_GLOW_STEPS
                : clamp(int(ceil(rays / FIELD_SAMPLE_RAYS)), FIELD_FEW_STEPS, FIELD_STEPS);
    float stride = (tTo - tFrom) / float(samples);
    // Each pixel's samples start a little differently along the line, which breaks up banding between steps: in an
    // ordered pattern, which shows far less than random offsets would as grain where the field is a smooth glow
    float start = ordered_dither(gl_FragCoord.xy);

    vec3 sum = vec3(0.0);
    for (int n = 0; n < samples; n++) {
        float t = tFrom + stride * (float(n) + start);
        float altitude = altitude_at(t, mu);
        float above = altitude - Emission.x;
        // Where the field line through here comes down to the lower edge: further north, as it leans south rising
        float base = dir.z * t + above * Emission.y;
        float belt = 1.0 - smoothstep(RayField.y * 0.5, RayField.y, abs(base - RayField.x));
        if (belt <= 0.0) {
            continue;
        }
        float sweep = stride * across + t * pixelAngle;
        float detail = 1.0 - smoothstep(0.5, 1.5, sweep / FIELD_CELL);
        float placed = noise(vec2(dir.x * t - RayField.w, base) / FIELD_CELL);
        float ray = mix(FIELD_MEAN, smoothstep(0.62, 0.85, placed), detail);

        // Each ray starting a little lower or higher, as the arcs' do, and as soft as a pixel spans there
        float rayAbove = above + 4.0 * (placed - 0.5);
        float soft = max(EDGE_SHARPNESS, t * pixelAngle * 2.0);
        float green = rayAbove < 0.0 ? exp(rayAbove / soft) : exp(-rayAbove / 40.0);
        float red = red_at(altitude, rayAbove);
        float rim = (rayAbove + 2.0) / soft;
        float fringe = Emission.w * exp(-rim * rim);
        float top = ray_top(altitude, mix(LOWEST_TOP, HIGHEST_TOP, clamp(placed * 1.4 - 0.2, 0.0, 1.0)));
        sum += ray * belt * top * (GreenColor.rgb * green + RedColor.rgb * red + FringeColor.rgb * fringe + sunlit_at(altitude, above));
    }
    return sum * stride * FIELD_GLOW * RayField.z;
}

void main() {
    // The pixel's direction: from one point along its line of sight to another further along it. Not from the camera:
    // view bobbing is in the projection, and moves the camera a little too, which next to points this close would
    // swing every direction as the player walks. Taken between two points, that cancels; its turning stays, so the
    // aurora sways as the stars do
    vec4 near = invViewProj * vec4(screenPos, 1.0, 1.0);
    vec4 far = invViewProj * vec4(screenPos, 0.5, 1.0);
    vec3 view = normalize(far.xyz / far.w - near.xyz / near.w);
    if (view.y < -0.01 || Frame.y <= 0.0) {
        discard;
    }
    float mu = max(view.y, 0.0);
    // The same direction as magnetic east, up and north: true east is +x and true north -z, and magnetic north is turned
    // east of true north by the declination (Activity.z and w, its cosine and sine)
    vec3 dir = vec3(view.x * Activity.z + view.z * Activity.w, view.y, view.x * Activity.w - view.z * Activity.z);

    float tIn = shell_distance(mu, SHELL_BOTTOM);
    float tOut = shell_distance(mu, SHELL_TOP);
    // How fast the line of sight goes north, for each kilometre along it (south, if negative); and how far east it is
    // as it enters the layer and as it leaves, for the bounds of the arcs' bows
    float northward = dir.z;
    float eastIn = dir.x * tIn;
    float eastOut = dir.x * tOut;
    float leastEast = eastIn * eastOut <= 0.0 ? 0.0 : min(abs(eastIn), abs(eastOut));
    float mostEast = max(abs(eastIn), abs(eastOut));
    // How far an arc's sheet leans south from its lower edge to the top of the layer, and north from it to the bottom
    float leanUp = (SHELL_TOP - Emission.x) * Emission.y;
    float leanDown = (Emission.x - SHELL_BOTTOM) * Emission.y;

    vec3 light = vec3(0.0);
    int arcs = int(Frame.w + 0.5);
    for (int i = 0; i < arcs; i++) {
        // Only the stretch of the line of sight within the band the arc's sheet can be in, north to south, is stepped
        // along: a few hundred kilometres of it at most, where the whole layer can be a couple of thousand. Its reach
        // is RealisticAurora.Arc.reach, the most its folds, wave and curls take it either way
        float reach = ArcShape[i].y + ArcWave[i].x + ArcWave[i].w;
        // And however far its bow bends it, where the line of sight passes: the bow only grows away from the viewer
        float bowNear = arc_bow(i, leastEast);
        float bowFar = arc_bow(i, mostEast);
        float southmost = ArcShape[i].x - reach - leanUp + min(bowNear, bowFar);
        float northmost = ArcShape[i].x + reach + leanDown + max(bowNear, bowFar);
        float tFrom = tIn;
        float tTo = tOut;
        if (abs(northward) > 0.0001) {
            // Arcs come overhead and pass to the south in a storm, so the line may be going either way
            float a = southmost / northward;
            float b = northmost / northward;
            tFrom = max(tIn, min(a, b));
            tTo = min(tOut, max(a, b));
        } else if (southmost > 0.0 || northmost < 0.0) {
            // Going neither north nor south (east, west or straight up), it stays over the viewer's own east-west line
            continue;
        }
        if (tFrom >= tTo) {
            continue;
        }
        // As many steps as the folds need: a line of sight crosses a fold more than once only where it runs along the
        // arc, so a quarter of its wave's length east or west to a step, and few where it barely goes east or west at
        // all (looking north)
        float eastward = abs(dir.x) * (tTo - tFrom);
        int steps = clamp(int(eastward / (ArcWave[i].y * 0.25)) + MIN_STEPS, MIN_STEPS, STEPS);
        float stride = (tTo - tFrom) / float(steps);
        float t0 = tFrom;
        float g0 = offset_at(i, dir, t0, mu);
        for (int n = 1; n <= steps; n++) {
            float t1 = tFrom + stride * float(n);
            float g1 = offset_at(i, dir, t1, mu);
            if ((g0 < 0.0) != (g1 < 0.0)) {
                light += arc_light(i, dir, crossing(i, dir, mu, t0, g0, t1, g1), mu);
            }
            t0 = t1;
            g0 = g1;
        }
    }

    if (RayField.z > 0.0) {
        light += ray_field(dir, mu, tIn, tOut, leanUp, leanDown);
    }

    // The air between: the lower the line of sight, the more of it, dimming blue most
    float airMass = 1.0 / (mu + 0.05);
    light *= exp(-EXTINCTION * airMass);

    // Into range by its brightest colour, the others scaled with it, so it keeps its colour however bright: a storm
    // overhead, seen edge on, is many times brighter than an arc low in the north. Then a little of each colour
    // brought into range on its own, which pales the very brightest parts toward white, as a photograph does
    vec3 exposed = light * EXPOSURE;
    float peak = max(exposed.r, max(exposed.g, exposed.b));
    vec3 kept = peak > 0.0 ? exposed * ((1.0 - exp(-peak)) / peak) : vec3(0.0);
    vec3 color = mix(kept, 1.0 - exp(-exposed), WHITENING);
    // A bright moon lights the sky behind: the faint parts are lost against it, the bright ones hardly dimmed
    float washout = GreenColor.a;
    if (washout > 0.0) {
        float bright = max(color.r, max(color.g, color.b));
        color *= bright / (bright + washout);
    }
    color *= Frame.y;
    color *= smoothstep(-0.01, 0.02, view.y);
    if (max(color.r, max(color.g, color.b)) > 0.5 / 255.0) {
        color += hash(gl_FragCoord.xy) / 255.0;
    }
    fragColor = vec4(color, 0.0);
}
