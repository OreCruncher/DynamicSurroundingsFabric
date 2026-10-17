/**
 * What the weather is: the rules and state the rest of the mod reads. Nothing here draws.
 * <ul>
 *     <li>{@link org.orecruncher.dsurround.runtime.weather.WeatherSimulation} gives each storm its own strength
 *     ({@code rainLevel}), in place of vanilla's rain level, for the fog, sounds, scripts and particle weather. It can
 *     be held at a strength by {@code /dsweather}.</li>
 *     <li>{@link org.orecruncher.dsurround.runtime.weather.Wind} is the wind: its direction, its speed from the storm,
 *     and gusts that sweep across from upwind. {@link org.orecruncher.dsurround.runtime.weather.WindService} ticks it
 *     and publishes it through {@link org.orecruncher.dsurround.runtime.weather.SceneWind}, which particles read to
 *     drift. A dimension can have none (its dimension info's {@code simulateWind}).</li>
 *     <li>{@link org.orecruncher.dsurround.runtime.weather.Shelter}: how open to the weather a place is, from its sky
 *     light and any roof over it. The wind, its sounds, the weather's fog and the ground fog reach only as far.</li>
 *     <li>{@link org.orecruncher.dsurround.runtime.weather.DustStorms}: where storms raise dust instead of rain, and
 *     its colour.</li>
 *     <li>{@link org.orecruncher.dsurround.runtime.weather.StormSounds} and
 *     {@link org.orecruncher.dsurround.runtime.weather.WindSounds}: how loud the rain, dust and wind sound for the
 *     weather there is.</li>
 * </ul>
 * The weather is drawn by {@code effects.precipitation}.
 */
package org.orecruncher.dsurround.runtime.weather;
