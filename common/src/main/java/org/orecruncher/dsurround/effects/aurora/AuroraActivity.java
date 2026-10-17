package org.orecruncher.dsurround.effects.aurora;

/**
 * How active a night's realistic aurora is: how much it does when it breaks up partway through the night (see
 * {@link RealisticAurora#at}). A quiet night's arcs only drift closer as the night goes on; the others brighten
 * suddenly, come closer, move faster and show more colour, then fade into pulsing patches. In a storm the arcs come
 * overhead, and their rays meet above the viewer in a corona.
 *
 * @param surgeGain     how much brighter it is at the height of the breakup: 1 is twice as bright
 * @param nearestAtPeak how far north of the viewer the nearest arc comes, in kilometres, at the height of the
 *                      breakup (negative: south of the viewer, so it has passed overhead); NaN if it doesn't come closer
 * @param speedBoost    how much faster its waves and rays move at the height of the breakup: 1 is twice as fast
 * @param extraRed      how much the red high up strengthens at the height of the breakup
 * @param fringe        how strong the pink fringe at the lower edge is at the height of the breakup
 * @param edgeDrop      how far the lower edge comes down at the height of the breakup, in kilometres
 * @param pulse         how strongly its patches pulse as it fades afterwards, 0 to 1
 * @param rayField      how strong its ray field is at the height of the breakup, 0 to 1: a belt of separate rays along
 *                      the magnetic field, beyond its arcs; overhead, they radiate from a point above the viewer
 * @param rayFieldWidth how far the ray field reaches north and south of its middle, in kilometres
 */
public enum AuroraActivity {
    QUIET(0F, Float.NaN, 0F, 0F, 0F, 0F, 0F, 0F, 0F),
    MODERATE(0.8F, 200F, 1.5F, 0.1F, 0.1F, 3F, 0.5F, 0F, 0F),
    ACTIVE(2.0F, 110F, 3F, 0.25F, 0.3F, 8F, 0.8F, 0.5F, 60F),
    STORM(2.0F, -40F, 5F, 1.0F, 0.45F, 12F, 0.9F, 1F, 100F);

    private final float surgeGain;
    private final float nearestAtPeak;
    private final float speedBoost;
    private final float extraRed;
    private final float fringe;
    private final float edgeDrop;
    private final float pulse;
    private final float rayField;
    private final float rayFieldWidth;

    AuroraActivity(float surgeGain, float nearestAtPeak, float speedBoost, float extraRed, float fringe, float edgeDrop,
                   float pulse, float rayField, float rayFieldWidth) {
        this.surgeGain = surgeGain;
        this.nearestAtPeak = nearestAtPeak;
        this.speedBoost = speedBoost;
        this.extraRed = extraRed;
        this.fringe = fringe;
        this.edgeDrop = edgeDrop;
        this.pulse = pulse;
        this.rayField = rayField;
        this.rayFieldWidth = rayFieldWidth;
    }

    /**
     * Whether it breaks up partway through the night: all but a quiet night
     */
    public boolean breaksUp() {
        return this != QUIET;
    }

    public float surgeGain() {
        return this.surgeGain;
    }

    public float nearestAtPeak() {
        return this.nearestAtPeak;
    }

    public float speedBoost() {
        return this.speedBoost;
    }

    public float extraRed() {
        return this.extraRed;
    }

    public float fringe() {
        return this.fringe;
    }

    public float edgeDrop() {
        return this.edgeDrop;
    }

    public float pulse() {
        return this.pulse;
    }

    public float rayField() {
        return this.rayField;
    }

    public float rayFieldWidth() {
        return this.rayFieldWidth;
    }
}
