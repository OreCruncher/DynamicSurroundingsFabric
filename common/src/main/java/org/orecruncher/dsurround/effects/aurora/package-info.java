// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

/**
 * The aurora, in two styles ({@code auroraOptions.style}): the classic one,
 * {@link org.orecruncher.dsurround.effects.aurora.AuroraRenderer}'s mesh of bands, and the realistic one, the default,
 * modeled on real auroras. {@code maxBands} is under
 * {@code auroraOptions.classic}; {@code stormChance}, {@code quality} and {@code moonWashout} under
 * {@code auroraOptions.realistic}. Both draw at {@code ISkyRender}, once the sky's pass is closed. The realistic one:
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
 * {@code clear} or disconnecting.
 */
package org.orecruncher.dsurround.effects.aurora;
