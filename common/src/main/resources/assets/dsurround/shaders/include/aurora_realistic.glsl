// What the realistic aurora's shaders share (RealisticAuroraRenderer): its values. Distances are in kilometres.

// The most arcs (RealisticAurora.MAX_ARCS)
#define MAX_ARCS 4

layout(std140) uniform RealisticAuroraInfo {
    // The colours of the light: oxygen's green, low in an arc; oxygen's red, high above it; nitrogen's pink fringe at
    // its lower edge. rgb; a is unused, but for GreenColor's: how much moonlight washes out the faint parts
    // (RealisticAurora.moonWashout)
    vec4 GreenColor;
    vec4 RedColor;
    vec4 FringeColor;
    // x: seconds, for the animation; y: how strongly it shows, 0 to 1; z: the screen's height in pixels; w: how many
    // arcs there are
    vec4 Frame;
    // x: how high an arc's lower edge is; y: how far an arc leans south for each kilometre it rises; z and w: how
    // strong the red and the fringe are next to the green (RealisticAurora.Moment)
    vec4 Emission;
    // x: how distinct the rays are, 0 (a smooth glow) to 1; y: how strongly patches pulse, 0 to 1
    // (RealisticAurora.Moment); z and w: the cosine and sine of how far magnetic north is from true north, east positive
    // (RealisticAurora.declination). North and east in the arcs' and ray field's values are magnetic
    vec4 Activity;
    // The ray field (RealisticAurora.Moment): x how far north of the viewer its middle is (negative: south); y how far
    // it reaches north and south of that; z how strong it is (0: none); w how far its rays have drifted east
    // (RealisticAuroraMotion)
    vec4 RayField;
    // The tops of the rays above the Earth's shadow, lit by the sun: rgb the colour of their glow, already as strong as it
    // is; w how high the shadow is over the viewer (RealisticAurora.shadowHeight)
    vec4 Sunlit;
    // Each arc's shape (RealisticAurora.Arc): x how far north of the viewer it is now (negative: south); y how far its
    // large folds reach north and south of that; z how far along it from one large fold to the next; w where along their
    // cycle the folds start, in radians
    vec4 ArcShape[MAX_ARCS];
    // Each arc's travelling wave and curls: x how far the wave reaches north and south; y how far from one crest to the
    // next; z how far it has travelled east (RealisticAuroraMotion); w how far the curls reach
    vec4 ArcWave[MAX_ARCS];
    // Each arc's light: x how bright it is now; y how thick it is, north to south; z how far its curls have travelled
    // east; w how far its rays have (RealisticAuroraMotion)
    vec4 ArcLight[MAX_ARCS];
    // Each arc's bow: x how far its ends bend north (south if negative); y, z and w unused
    vec4 ArcBow[MAX_ARCS];
};
