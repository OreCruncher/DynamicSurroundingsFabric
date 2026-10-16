package org.orecruncher.dsurround.effects.groundfog;

import org.orecruncher.dsurround.config.GroundFogQuality;
import org.orecruncher.dsurround.effects.RenderTimer;

/**
 * Draws the ground fog as a volume: a pass over the screen that follows each pixel's line of sight from the camera
 * to what it shows, adding up the mist along the way. There is mist where the line passes within
 * {@link #MIST_DEPTH} blocks of the ground, thickest at the ground; how much depends on the biome, and drifting noise
 * breaks it into patches. So it thickens the more of it is looked through, fills hollows and pools at the foot of
 * cliffs, and has no edges to show.
 * <p>
 * That pass is drawn at a share of the screen's size (half, for {@link GroundFogQuality#FANCY}), to a target of its
 * own: at half, a quarter of the pixels to follow. A second
 * (smooth), at that size, blends each pixel's neighbours, which smooths away the stipple of their steps being
 * offset a little differently, but only those that looked about as far, so the mist doesn't smear across the edges of
 * what is in front of it. A third (composite) lays it over the scene at full size, the same way between the
 * two by two around each pixel.
 * <p>
 * The grid ({@link GroundFogField}) is handed to the shader as a small texture, a texel a column: the ground's height
 * above a base just below the grid's lowest (smoothed, see {@link GroundFogField#smoothHeight}), the mist's thickness,
 * and the block and sky light there. Render thread only.
 * <p>
 * 26.2 port: the passes aren't built yet (0.4.7's are the reference), so there is no ground fog.
 */
public final class GroundFogVolume {

    /**
     * How long the GPU takes over all the passes, for the diagnostics overlay, and over each: the march (with the depth
     * copy it reads), the smoothing and the composite
     */
    public static final RenderTimer RENDER_TIMER = new RenderTimer("Ground Fog");
    public static final RenderTimer MARCH_TIMER = new RenderTimer("- March");
    public static final RenderTimer SMOOTH_TIMER = new RenderTimer("- Smooth");
    public static final RenderTimer COMPOSITE_TIMER = new RenderTimer("- Composite");

    /**
     * How deep the mist is over the ground, in blocks: it thins to nothing at this height
     */
    static final float MIST_DEPTH = 1.8F;
    /**
     * How quickly the mist hides what is behind it, for each block of it looked through at full thickness
     */
    static final float THICKNESS = 0.5F;
    /**
     * Ground heights are kept in a byte, relative to a base this many blocks below the lowest ground in the grid: so
     * they fit wherever the camera is, high above it or deep below. Ground more than a byte's worth above the base (a
     * grid spanning a mountain's height) is held at the top.
     */
    private static final int HEIGHT_BASE_MARGIN = 2;
    /**
     * Where things are east-west and north-south is wrapped at this many blocks for the noise, to keep it to a float's
     * precision far from the world's middle. A whole number of times the noise texture repeats ({@link #NOISE_SIZE}
     * cells) for each of the shader's noises (1.75, 3.5, 7 and 11 blocks a cell), so a wrap lands on the same noise
     * and can't be seen.
     */
    public static final int NOISE_WRAP = 4620;
    /**
     * The noise texture's size, in cells, which it repeats at: a random value a texel, read smoothly between them.
     * One read for each noise in place of working out the four corners of its cell.
     */
    public static final int NOISE_SIZE = 60;

    private GroundFogVolume() {
    }

    /**
     * Whether the ground fog can be drawn. Not yet on 26.2.
     */
    public static boolean isUsable() {
        return false;
    }
}
