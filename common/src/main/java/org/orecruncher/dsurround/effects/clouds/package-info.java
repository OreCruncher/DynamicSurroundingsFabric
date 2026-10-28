/**
 * Dynamic clouds: blocky clouds that change with the weather, drawn in place of vanilla's, run by
 * {@code processing.CloudHandler}. What the clouds are like for the weather is {@code runtime.weather.CloudCover}.
 * <ul>
 *     <li>{@link org.orecruncher.dsurround.effects.clouds.CloudField} lays them out as columns of blocks on vanilla's
 *     12 block cells: a flat base, and a top as high as noise and the cover let it build, in 4 block steps; and thin
 *     streaks high above, drawn out along the wind. Cells are in cloud space (the world less the drift), so a cloud
 *     keeps its shape as it drifts.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.clouds.CloudNoise}: the value noise they are laid out with, and the
 *     rank that turns a share of the sky into a threshold.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.clouds.CloudMesh} turns the columns into quads, shaded darker
 *     underneath the thicker the cloud above.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.clouds.CloudRenderer} uploads the quads when the clouds change and
 *     draws them, depth first and then color, as vanilla's are; with Fabulous graphics to the clouds' target.</li>
 * </ul>
 * Off with a shader pack, when its shader fails, with vanilla's clouds turned off, and in a dimension without them
 * ({@code dynamicClouds} in the dimension configs; by default, those that draw vanilla's clouds).
 */
package org.orecruncher.dsurround.effects.clouds;
