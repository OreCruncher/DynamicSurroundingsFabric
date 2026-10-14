package org.orecruncher.dsurround.config;

public enum ClockStyle {
    NONE(false, false),
    TEXT(true, false),
    POCKET_WATCH(false, true),
    POCKET_WATCH_WITH_TEXT(true, true);

    private final boolean showText;
    private final boolean showWatch;

    ClockStyle(boolean showText, boolean showWatch) {
        this.showText = showText;
        this.showWatch = showWatch;
    }

    /**
     * Whether the clock is displayed at all
     */
    public boolean isShown() {
        return this.showText || this.showWatch;
    }

    /**
     * The time and season as text, above the hot bar
     */
    public boolean showText() {
        return this.showText;
    }

    /**
     * A pocket watch above the crosshair, its hands at the time and a window showing the season
     */
    public boolean showWatch() {
        return this.showWatch;
    }
}
