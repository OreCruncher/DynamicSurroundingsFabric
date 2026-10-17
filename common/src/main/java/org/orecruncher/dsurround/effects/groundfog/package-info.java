/**
 * Ground fog: mist lying low over the ground in the biomes given it ({@code groundFog} in the biome configs), run by
 * {@code processing.GroundFogHandler}.
 * <ul>
 *     <li>{@link org.orecruncher.dsurround.effects.groundfog.GroundFogField} works out, for each column of blocks
 *     around the camera, the ground's height and how thick the mist is. Posts and tree trunks are left out of the
 *     ground, the ground the mist is shaped by is smoothed, and the thickness is blended at biome edges. It reads the
 *     level through an {@link org.orecruncher.dsurround.effects.groundfog.IGroundFogSource}: in the game, the player's
 *     level; in tests, a fake.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.groundfog.GroundFogVolume} draws it: the grid is handed to a shader
 *     as a small texture, and a pass over the screen follows each pixel's line of sight through the mist, adding it
 *     up. So it thickens the more of it is looked through, and has no edges to show. Noise drifting with the wind
 *     breaks it into patches and wisps. That pass is drawn at half size, a second smooths it there, blending
 *     neighbouring pixels that looked about as far, and a third lays it over the scene.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.groundfog.GroundFogConditions}: how strong it is for the time of day,
 *     the wind, rain and being out in it, as a share of what the biomes give it.</li>
 * </ul>
 * It is off with a shader pack, when its shader fails, and in a dimension without it ({@code groundFog} in the dimension
 * configs). {@code /dsweather groundfog} gives it a thickness everywhere, whatever the biomes and conditions, for seeing
 * how it looks.
 */
package org.orecruncher.dsurround.effects.groundfog;
