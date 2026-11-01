/**
 * The mod's particles: flames, sparks, smoke, steam, breath, death mist, fireflies, waterfall mist, water foam and
 * ripples, lava drops. Unlike the weather in {@code effects.precipitation}, they are the game's own particles, ticked
 * and drawn by its particle engine in layers of ours ({@code SingleQuadParticle.Layer}).
 * <ul>
 *     <li>{@link org.orecruncher.dsurround.effects.particles.SoftParticles}: the layers, and the shader that fades a
 *     particle into whatever is behind it.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.particles.PointLights}: the light flames and fireflies cast on what
 *     is around them.</li>
 * </ul>
 * <h2>Why these aren't a system like the weather's</h2>
 * The weather keeps its drops in flat arrays, steps them on a thread of its own and builds their quads ahead of the
 * frame. That paid off because thousands of drops all behave alike, their step reads only the columns and a snapshot
 * of the wind, and it was measured to cost something (about 0.2 ms CPU at High's range, and frames waiting for the
 * build). Weighed for these particles (2026-11-01), it doesn't carry over as a whole:
 * <ul>
 *     <li>Tens to a few hundred are alive at once. Only waterfall mist, and fireflies at night, get numerous.</li>
 *     <li>About fifteen kinds behave each their own way: a layout and a step for each, for a small saving each.</li>
 *     <li>Some read the world every tick: {@link org.orecruncher.dsurround.effects.particles.WaterFoam} follows the
 *     water's surface, {@link org.orecruncher.dsurround.effects.particles.LavaDroplet} collides and looks at the
 *     fluid it lands in, {@link org.orecruncher.dsurround.effects.particles.WanderingFlight} avoids solid blocks.
 *     Another thread may read the world only through {@code WorldContext.blocks}, so their steps can't simply move
 *     off the client thread.</li>
 *     <li>As the game's particles they get what it gives particles: drawn by an Iris shader pack's own program (our
 *     weather stands aside under a pack), thinned by the players' Particles setting, and handled by particle-culling
 *     mods.</li>
 * </ul>
 * So, measure before converting any. Neither our particle layers' time nor how many of each are alive is in
 * {@code /dsprofile} recordings yet: a render timer around our layers, a count by kind, and bench scenarios
 * ({@code scripts/bench}) at a large waterfall and in a swamp at night, at each performance preset, would show it. The
 * likely candidate is {@link org.orecruncher.dsurround.effects.particles.WaterfallMist}: one kind, many of them, the
 * world read only when one spawns (the water's color), and its motion a formula, so it could become a field like the
 * dust's, built ahead with the weather's quads. Foam and fireflies stay particles unless the numbers say otherwise.
 */
package org.orecruncher.dsurround.effects.particles;
