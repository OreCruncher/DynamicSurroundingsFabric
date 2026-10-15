package org.orecruncher.dsurround.config;

/**
 * A side or corner of the screen, for the compass and pocket watch when they are shown in the screen's margin
 */
public enum MarginPosition {
    TOP_LEFT(-1, -1),
    TOP(0, -1),
    TOP_RIGHT(1, -1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    BOTTOM_LEFT(-1, 1),
    BOTTOM(0, 1),
    BOTTOM_RIGHT(1, 1);

    private final int horizontal;
    private final int vertical;

    MarginPosition(int horizontal, int vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    /**
     * -1 against the left edge, 1 against the right, 0 centered
     */
    public int horizontal() {
        return this.horizontal;
    }

    /**
     * -1 against the top edge, 1 against the bottom, 0 centered
     */
    public int vertical() {
        return this.vertical;
    }

    /**
     * Whether the compass and watch sit side by side (top and bottom center) rather than one above the other
     */
    public boolean sideBySide() {
        return this.horizontal == 0;
    }
}
