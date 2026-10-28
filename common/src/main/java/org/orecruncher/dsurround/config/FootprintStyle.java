package org.orecruncher.dsurround.config;

/**
 * The look of a footprint: a pair of prints, left and right, in the footprint texture, in this order.
 */
public enum FootprintStyle {
    SHOE,
    SQUARE,
    HORSESHOE,
    BIRD,
    PAW,
    SQUARE_SOLID,
    LOWRES_SQUARE;

    // The texture holds 8 pairs side by side, each pair a left print then a right
    private static final float PAIR_WIDTH = 1F / 8F;
    private static final float PRINT_WIDTH = PAIR_WIDTH / 2F;
    // Kept clear of the next print's edge, so filtering doesn't bleed it in
    private static final float INSET = 1F / 512F;

    /**
     * Where the print starts across the texture, 0 to 1
     */
    public float u0(boolean rightFoot) {
        return this.ordinal() * PAIR_WIDTH + (rightFoot ? PRINT_WIDTH : 0F) + INSET;
    }

    /**
     * Where the print ends across the texture, 0 to 1
     */
    public float u1(boolean rightFoot) {
        return this.ordinal() * PAIR_WIDTH + (rightFoot ? PAIR_WIDTH : PRINT_WIDTH) - INSET;
    }
}
