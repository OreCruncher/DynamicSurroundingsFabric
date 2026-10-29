package org.orecruncher.dsurround.effects.aurora;

/**
 * A night's realistic aurora's color character: how strong its red high up, its pink lower border and its green are,
 * next to a usual night's. Most nights are {@link #NORMAL}. Some are rich in red, a deep red glow above the green, far
 * more often in a storm; some have a strong pink border; and now and then a storm is {@link #CRIMSON}, a sky of red with
 * little green, as the greatest storms are.
 *
 * @param red    how much stronger the red is
 * @param fringe how much stronger the pink lower border is
 * @param green  how much stronger (or weaker) the green is
 */
public enum AuroraColor {
    NORMAL(1F, 1F, 1F),
    RED(2.5F, 1F, 0.9F),
    PINK(1F, 3F, 1F),
    CRIMSON(5F, 1.5F, 0.35F);

    private final float red;
    private final float fringe;
    private final float green;

    AuroraColor(float red, float fringe, float green) {
        this.red = red;
        this.fringe = fringe;
        this.green = green;
    }

    /**
     * The color character for a night's roll, 0 to 1, and how active it is: a storm is red-rich two times in five and
     * crimson one in five; other nights are red-rich about one in seven, and have a pink border about one in eight
     */
    public static AuroraColor of(float roll, AuroraActivity activity) {
        if (activity == AuroraActivity.STORM)
            return roll < 0.2F ? CRIMSON : roll < 0.6F ? RED : roll < 0.7F ? PINK : NORMAL;
        return roll < 0.15F ? RED : roll < 0.27F ? PINK : NORMAL;
    }

    public float red() {
        return this.red;
    }

    public float fringe() {
        return this.fringe;
    }

    public float green() {
        return this.green;
    }
}
