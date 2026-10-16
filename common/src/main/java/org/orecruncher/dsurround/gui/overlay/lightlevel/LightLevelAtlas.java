package org.orecruncher.dsurround.gui.overlay.lightlevel;

import net.minecraft.resources.Identifier;
import org.orecruncher.dsurround.Constants;

/**
 * The numbers 0 to 15, drawn once with the game's font into a texture, a 4 by 4 grid of cells: each a white number
 * with a black outline, on clear. The overlay draws a label as one quad from its cell, tinted by vertex color, rather
 * than as a quad per character of text. Drawn again after resources reload, so a resource pack's font shows, and when
 * the "force Unicode font" option changes.
 * <p>
 * Drawn on the client thread outside of rendering (from the tick), into a target of its own, and read back.
 * <p>
 * 26.2 port: drawing the numbers into the texture isn't built yet (0.4.7's is the reference), so it is never ready
 * and the overlay stays off.
 */
final class LightLevelAtlas {

    static final Identifier ID = Constants.asId("dynamic/light_levels");
    static final int CELLS = 4;
    static final int CELL_SIZE = 64;
    static final int SIZE = CELLS * CELL_SIZE;
    // Space left around a number in its cell, in texture pixels, so neighbours never bleed into it
    private static final int CELL_PADDING = 4;

    /**
     * The fonts have changed: draw the numbers again when next asked
     */
    void invalidate() {
    }

    /**
     * Whether the texture can be drawn from. Not yet on 26.2.
     */
    boolean isReady() {
        return false;
    }

    /**
     * Draws the numbers if the font has changed since they were last drawn. Called on the client thread, between
     * frames.
     */
    void refresh() {
    }

    /**
     * The left of a number's cell, as a fraction of the texture
     */
    static float cellU(int value) {
        return (value & (CELLS - 1)) / (float) CELLS;
    }

    /**
     * The top of a number's cell, as a fraction of the texture
     */
    static float cellV(int value) {
        return (value / CELLS) / (float) CELLS;
    }

    /**
     * The whole number of texture pixels per font pixel that fits the widest number, outline and all, in a cell
     */
    static int fitScale(int widestText, int lineHeight) {
        int room = CELL_SIZE - 2 * CELL_PADDING;
        // The outline adds a pixel either side
        int scale = Math.min(room / (widestText + 2), room / (lineHeight + 2));
        return Math.max(1, scale);
    }
}
