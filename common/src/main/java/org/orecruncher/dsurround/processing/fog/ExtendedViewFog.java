// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;

/**
 * The mod's fog where another mod draws terrain past the render distance and stretches the game's fog out to it.
 * <p>
 * One tried ends the render distance fog past the render distance, and multiplies the environmental fog's range by as
 * much, but only while that fog ends at least 0.9 of the render distance away: thicker fog is left as it is. Our fog
 * thickens the game's before that, so as a dust storm built it crossed the line and the view snapped from its
 * horizon (about 2 km) to our fog's end (about 230 blocks) in one step (seen in game, 2026-11-08). No range we could
 * hand it comes out in between.
 * <p>
 * So while the view reaches past the render distance, our fog is worked out as always but held back, and put in once
 * the frame's fog is finished ({@code MixinFogRenderer}), stretched by what the other mod stretched the game's by while
 * it is light, by less as it thickens, and by nothing once it hides the render distance's own edge
 * ({@link #stretch}). The far horizon then fades as the render distance's edge does without such a mod, and the fog
 * near the player closes in as ours does.
 * <p>
 * Adding ours as density on top of the stretched fog, which is what fog does, was tried first: over a view nine times
 * as deep the least of our haze hid the horizon, gone at 3% of a dust storm.
 */
public final class ExtendedViewFog {

    // The share of the visible distance ours ends at below which it isn't stretched at all: a thick fog stays as thick
    static final float UNSTRETCHED_SHARE = 0.2F;

    // How far past the render distance the render distance fog has to end for the view to count as reaching past it,
    // in blocks: vanilla ends it at the render distance exactly
    static final float MARGIN = 16F;

    private ExtendedViewFog() {
    }

    /**
     * Whether another mod has the view reaching past the render distance, by the frame's finished fog
     *
     * @param renderDistance the render distance in blocks
     */
    public static boolean isExtended(FogData data, float renderDistance) {
        return data.renderDistanceEnd > renderDistance + MARGIN;
    }

    /**
     * Puts our fog into the frame's, as the game and other mods finished it.
     *
     * @param target         the frame's finished fog; changed in place
     * @param given          the game's fog as our calculators were given it, before any other mod stretched it
     * @param ours           what our calculators made of {@code given}
     * @param renderDistance the render distance in blocks
     */
    public static void apply(FogData target, FogData given, FogData ours, float renderDistance) {
        float stretched = target.environmentalEnd;
        float factor = stretch(ours.environmentalEnd, given.environmentalEnd, stretched, renderDistance);
        // Never thinner than the other mod left it, nor than ours
        float end = Math.max(Math.min(ours.environmentalEnd * factor, stretched), Math.min(ours.environmentalEnd, stretched));
        // The start stays ours: stretched with the end, a thick fog's clear zone near the player grew to hundreds of
        // blocks and the fog over the middle distance came in twice as fast as the storm built
        target.environmentalStart = Math.min(ours.environmentalStart, end);
        target.environmentalEnd = end;
        // The other mod leaves the sky and clouds alone: stretched with the terrain, up to the game's own, or they would haze
        // over in a light dust storm while the land stayed clear to the horizon
        target.skyEnd = Math.min(target.skyEnd, Math.min(ours.skyEnd * factor, given.skyEnd));
        target.cloudEnd = Math.min(target.cloudEnd, Math.min(ours.cloudEnd * factor, given.cloudEnd));
    }

    /**
     * How much our fog's range is multiplied by: the other mod's stretch of the game's fog while ours ends past what
     * the player could see without it (the render distance, or nearer when the game's fog ends nearer), easing to
     * none as ours closes in: the stretch is raised to a smoothstep of how far out ours ends, as a share of that, from
     * {@link #UNSTRETCHED_SHARE} to all of it. The steepest is just inside the render distance, where a dust storm at
     * about 28% adds about 13% more fog at 1000 blocks for each 1% of storm; the dust eases in over seconds, so that is
     * a quick fade, not a jump.
     *
     * @param ours           our fog's end, worked out from the game's
     * @param given          the game's end, as our calculators were given it
     * @param stretched      the end after the other mod
     * @param renderDistance the render distance in blocks
     */
    static float stretch(float ours, float given, float stretched, float renderDistance) {
        if (!(given > 0F) || !(stretched > given) || Float.isInfinite(stretched) || !(ours > 0F))
            return 1F;
        float visible = Math.min(renderDistance, given);
        float share = Mth.clamp((ours / visible - UNSTRETCHED_SHARE) / (1F - UNSTRETCHED_SHARE), 0F, 1F);
        return (float) Math.pow(stretched / given, share * share * (3F - 2F * share));
    }
}
