package org.orecruncher.dsurround.runtime.weather;

import java.util.Optional;

/**
 * Gives each storm its own strength. The server only says whether it is raining, and vanilla always builds up to
 * full strength; the simulation picks a maximum when a storm starts and caps the client's rain level there, so one
 * storm is a drizzle and the next a downpour. Every client picks the same maximum for the same day.
 * <p>
 * Only what is drawn and heard changes. The world's own rain level, and with it {@code isRaining()}, is left alone.
 */
public interface IWeatherSimulation {

    /**
     * The rain level to show in place of the game's. Takes the level for the frame or tick being worked on, so the
     * game's fading in and out stays smooth up to the cap.
     */
    float rainLevel(float vanillaLevel);

    /**
     * The rain level as of the last tick, capped; 0 when it isn't raining
     */
    float intensity();

    /**
     * The most this storm will reach; 0 when it isn't raining
     */
    float maxIntensity();

    /**
     * The world's own rain level as of the last tick, before it is capped
     */
    float vanillaLevel();

    /**
     * The band the current rain level is in
     */
    StormIntensity band();

    /**
     * Whether storms are being given their own strengths; when not, they have the game's, unless one is set
     */
    boolean isSimulating();

    /**
     * Sets the strength of every storm, 0 to 1, in place of the one picked for it, until {@link #clearStrength} or the
     * player disconnects. Applies even with the simulation turned off. For seeing how storms of a strength look.
     */
    void setStrength(float strength);

    /**
     * Goes back to the strength picked for each storm
     */
    void clearStrength();

    /**
     * The strength set by {@link #setStrength}, if one is
     */
    Optional<Float> strengthSet();
}
