/**
 * Particle weather: rain, snow and dust drawn as individual drops around the player, in place of vanilla's sheets.
 * What the weather is comes from {@code runtime.weather}; this draws it. The handler that runs it each tick and frame
 * is {@code processing.PrecipitationHandler}.
 * <ul>
 *     <li>{@link org.orecruncher.dsurround.effects.precipitation.PrecipitationField} holds the drops, in flat arrays
 *     for speed: spawning, falling, drifting with the wind, landing, and building their vertices. In the game their
 *     tick runs on a thread of its own while frames are drawn, a tick behind: two sets of the drops, one drawn while
 *     the step fills the other. The step reads only the columns and a snapshot of the wind; whether a drop carried in
 *     under something is in open air is answered from the world when the step finishes, on the client thread.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.precipitation.PrecipitationColumns} looks up, and caches, what falls
 *     in each column of the area, asking an
 *     {@link org.orecruncher.dsurround.effects.precipitation.IColumnSource}: in the game,
 *     {@link org.orecruncher.dsurround.effects.precipitation.LevelColumns}; in tests, a fake.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.precipitation.Landings}: what happens where drops land (splashes,
 *     smoke, {@link org.orecruncher.dsurround.effects.precipitation.DampSpots}, and
 *     {@link org.orecruncher.dsurround.effects.precipitation.SnowFlecks} where snowflakes land, and
 *     {@link org.orecruncher.dsurround.effects.precipitation.WindowStreaks} where rain is blown onto windows), and the
 *     dust storm's sound.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.precipitation.AfterRainDrips}: leaves and overhanging edges drip
 *     for a while after the rain, as vanilla's own dripping water. Not drawn here, so it runs with the drops off.</li>
 *     <li>{@link org.orecruncher.dsurround.effects.precipitation.PrecipitationRenderer},
 *     {@link org.orecruncher.dsurround.effects.precipitation.PrecipitationShaders} and
 *     {@link org.orecruncher.dsurround.effects.precipitation.Snowflakes}: drawing.</li>
 * </ul>
 * It stands aside, and vanilla's weather is drawn, when turned off, with a shader pack in use, when its shader fails,
 * and in a dimension that draws its own weather (its dimension info's {@code particleWeather}). It is off when a mod
 * that draws the weather as particles of its own is installed (Particle Rain, Simple Weather).
 */
package org.orecruncher.dsurround.effects.precipitation;
