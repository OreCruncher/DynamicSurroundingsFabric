/**
 * Particle weather: rain, snow and dust drawn as individual drops around the player, in place of vanilla's sheets.
 * What the weather is comes from {@code runtime.weather}; this draws it. The handler that runs it each tick and frame
 * is {@code processing.PrecipitationHandler}.
 * <ul>
 *     <li>{@link org.orecruncher.dsurround.effects.precipitation.PrecipitationField} holds the drops, in flat arrays
 *     for speed: spawning, falling, drifting with the wind, landing, and building their vertices.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.precipitation.PrecipitationColumns} looks up, and caches, what falls
 *     in each column of the area, asking an
 *     {@link org.orecruncher.dsurround.effects.precipitation.IColumnSource}: in the game,
 *     {@link org.orecruncher.dsurround.effects.precipitation.LevelColumns}; in tests, a fake.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.precipitation.Landings}: what happens where drops land (splashes,
 *     smoke, {@link org.orecruncher.dsurround.effects.precipitation.DampSpots}), and the dust storm's sound.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.precipitation.PrecipitationRenderer},
 *     {@link org.orecruncher.dsurround.effects.precipitation.PrecipitationShaders} and
 *     {@link org.orecruncher.dsurround.effects.precipitation.Snowflakes}: drawing.</li>
 * </ul>
 * It stands aside, and vanilla's weather is drawn, when turned off, with a shader pack in use, when its shader fails,
 * in a dimension that draws its own weather (its dimension info's {@code particleWeather}), and when the Particle Rain
 * mod is installed.
 */
package org.orecruncher.dsurround.effects.precipitation;
