// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

/**
 * The aurora, in three styles ({@code auroraOptions.style}): the classic one,
 * {@link org.orecruncher.dsurround.effects.aurora.AuroraRenderer}'s mesh of bands; the realistic one, the default,
 * modeled on real auroras; and the cinematic one, stylized ribbons. {@code maxBands} is under
 * {@code auroraOptions.classic}; {@code stormChance}, {@code quality} and {@code moonWashout} under
 * {@code auroraOptions.realistic}; the cinematic one's {@code quality} under {@code auroraOptions.cinematic} (it also
 * follows {@code stormChance}, through the realistic night it takes its activity from). All draw at {@code ISkyRender},
 * once the sky's pass is closed. The cinematic one:
 * <ul>
 *     <li>{@link org.orecruncher.dsurround.effects.aurora.CinematicAurora} is the model: one night's ribbons (a layout
 *     of waves or a horseshoe overhead, any heading), each a curtain hanging from a folded, bowed line about 100 km
 *     up, most curled into a spiral or hook by a twist of the layer (an offset alone can't double back). How active
 *     it is comes from the same night's {@code RealisticAurora}, which {@code AuroraHandler} keeps but doesn't draw:
 *     its {@code Moment}'s gain, speed, ray contrast and surge (a breakup draws the ribbons in toward
 *     overhead, lowers their edge, adds violet). One fixed palette, in the shader.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.aurora.CinematicAuroraMotion}: folds, waves and rays moved frame
 *     by frame, as the realistic one's are.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.aurora.CinematicAuroraRenderer} and
 *     {@code shaders/core/aurora_cinematic.*}: one screen pass, adding light. Each ribbon is a soft sheet (a bell curve
 *     across it); the shader steps along the line of sight through the band it can be in and adds, a step at a time,
 *     how much of the sheet the line goes through, worked out with the error function, so nothing is missed between
 *     steps and edge on is brighter of itself. Color by height (yellow-green edge, emerald, violet top), rays,
 *     patches and a violet halo, as real photographs show them. {@code FAST} draws at half size and lays it over
 *     with the realistic one's composite shader.</li>
 * </ul>
 * The realistic one:
 * <ul>
 *     <li>{@link org.orecruncher.dsurround.effects.aurora.RealisticAurora} is the model: one night's arcs, in
 *     kilometers around a viewer on a round Earth, from the night's number. Arcs run along magnetic east-west
 *     ({@code declination} from true north), bow at their ends, fold three ways (large drifting folds, a traveling
 *     wave, small curls) and lean south up the magnetic field. A night has an
 *     {@link org.orecruncher.dsurround.effects.aurora.AuroraActivity} (quiet to storm, storms at {@code stormChance})
 *     and an {@link org.orecruncher.dsurround.effects.aurora.AuroraColor} character. {@code at(progress)} gives a
 *     {@code Moment} for how far through the night it is ({@code DayCycle.getNightProgress}): growth, then a breakup
 *     (brighter, closer, faster; a storm comes overhead), its height, and recovery (pulsing patches). Active nights
 *     and storms have a ray field.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.aurora.RealisticAuroraMotion}: waves, curls, rays and the ray field
 *     move frame by frame at the moment's speed, wrapped where their patterns repeat; not {@code time × speed}, which
 *     leaps when the speed changes.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.aurora.RealisticAuroraRenderer} and
 *     {@code shaders/core/aurora_realistic.*}: one screen pass after the stars, adding light, with no depth. Each
 *     pixel's line of sight goes through 80 to 450 km up. For each arc it steps only through the band the sheet can be
 *     in, as many steps as the folds need, finds sign changes of the offset from the sheet, narrows each crossing
 *     (regula falsi) and adds the light there at once: thickness over how squarely it is crossed, so edge on is
 *     brighter. Light by height (green, red above the edge, a pink fringe, blue-violet above the Earth's shadow), rays
 *     from noise along the arc, ragged ray tops and lower edge. The ray field is sampled along the line, as many
 *     samples as show its rays. Brought into range by the brightest channel; a bright moon washes out the faint parts.
 *     At {@code FAST} it is drawn at half size and laid over the sky ({@code aurora_composite}).</li>
 *     <li>Direction from the screen: taken between two unprojected points, not from the camera, as view bobbing in the
 *     projection moves the camera too.</li>
 * </ul>
 * {@code /dsaurora} shows tonight's aurora; {@code activity}, {@code phase} and {@code colour} hold one until
 * {@code clear} or disconnecting ({@code colour} is the realistic one's alone).
 */
package org.orecruncher.dsurround.effects.aurora;
