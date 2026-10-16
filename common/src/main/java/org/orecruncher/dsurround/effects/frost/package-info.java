/**
 * Frost on the outside of clear glass windows when it is cold. Run by {@code processing.WindowFrostHandler}, which
 * draws it where the level calls for the weather, before it.
 * <ul>
 *     <li>{@link org.orecruncher.dsurround.effects.frost.FrostFaces}: which sides of a window frost (the outside), where
 *     the glass is, and how far the window reaches beyond each, as {@link org.orecruncher.dsurround.effects.frost.FrostFace}s.
 *     The world comes through {@link org.orecruncher.dsurround.effects.frost.IFrostWorld}: in tests, a fake.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.frost.FrostField}: the faces near the player, a section at a time.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.frost.FrostConditions}: how much frost, from the cold, the time of
 *     day and the sun on the glass, and how it grows and melts.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.frost.FrostRenderer} and
 *     {@link org.orecruncher.dsurround.effects.frost.FrostTexture}: drawing, with a texture made at run time whose alpha
 *     says when each texel frosts over.</li>
 * </ul>
 */
package org.orecruncher.dsurround.effects.frost;
