/**
 * Lightning: what is drawn. Run by {@code processing.LightningHandler}, which adds the strikes, flashes the sky for
 * close ones, plays their thunder and stands in for the game's lightning bolts.
 * <ul>
 *     <li>{@link org.orecruncher.dsurround.effects.lightning.BoltGenerator} makes a bolt's shape
 *     ({@link org.orecruncher.dsurround.effects.lightning.BoltShape}): a jagged main channel and the branches forking
 *     from it, more and deeper the stronger the storm.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.lightning.Strike} is one flash over time: a faint leader, then strokes
 *     down the channel that flicker and fade. {@link org.orecruncher.dsurround.effects.lightning.Strikes} makes each
 *     kind (cloud to ground, spider lightning under the cloud base, a flash hidden in the cloud) and holds the
 *     numbers: how strong the lightning is, how often, how far, and its thunder's delay and loudness.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.lightning.LightningRenderer} draws them, where vanilla draws its
 *     weather, after it.</li>
 * </ul>
 * Allowed by the scary content options ({@code Configuration.ScaryContent}), and exposed to scripts as
 * {@code global.allowLightning()}.
 */
package org.orecruncher.dsurround.effects.lightning;
