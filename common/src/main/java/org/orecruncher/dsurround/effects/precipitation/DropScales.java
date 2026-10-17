package org.orecruncher.dsurround.effects.precipitation;

/**
 * How big rain and snow are drawn, as a multiple of their usual size: the player's settings. Only the drawing
 * changes; a drop's size as the weather knows it (how fast it falls, whether it splashes) stays the same.
 */
public record DropScales(float rain, float snow) {

    public static final DropScales USUAL = new DropScales(1F, 1F);
}
