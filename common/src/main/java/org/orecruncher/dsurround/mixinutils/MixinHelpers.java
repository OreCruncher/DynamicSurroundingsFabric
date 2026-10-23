package org.orecruncher.dsurround.mixinutils;

import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.biome.BiomeMusicSelector;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.function.Guard;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.processing.FogHandler;
import org.orecruncher.dsurround.processing.GroundFogHandler;
import org.orecruncher.dsurround.processing.PrecipitationHandler;
import org.orecruncher.dsurround.processing.WindowFrostHandler;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;

/**
 * Static class definitions with mixins do not work well. Any statics have been placed
 * here for mixin access.
 */
public class MixinHelpers {
    public static final IModLog LOGGER = ContainerManager.memoize(IModLog.class);
    public static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);
    public static final ISoundLibrary SOUND_LIBRARY = ContainerManager.resolve(ISoundLibrary.class);
    public static final Configuration.SoundSystem soundSystemConfig = ContainerManager.resolve(Configuration.SoundSystem.class);
    public static final Configuration.FootstepAccents footstepAccentsConfig = ContainerManager.resolve(Configuration.FootstepAccents.class);
    public static final Configuration.ParticleTweaks particleTweaksConfig = ContainerManager.resolve(Configuration.ParticleTweaks.class);
    public static final Configuration.Guards guardsConfig = ContainerManager.resolve(Configuration.Logging.class).guards;
    public static final Configuration.SoundOptions soundOptions = ContainerManager.resolve(Configuration.SoundOptions.class);
    public static final Configuration.FogOptions fogOptions = ContainerManager.resolve(Configuration.FogOptions.class);
    public static final Configuration.WeatherOptions weatherOptions = ContainerManager.resolve(Configuration.WeatherOptions.class);
    public static final Configuration.MusicManagerOptions musicOptions = ContainerManager.resolve(Configuration.MusicManagerOptions.class);
    public static final BiomeMusicSelector BIOME_MUSIC = new BiomeMusicSelector();

    // Mixin bodies run every frame, tick or particle inside the game's own code: a failure is logged a few times and
    // the game's own behaviour used in its place, rather than thrown into the game
    public static final Guard FOG_TINT_GUARD = Guard.named("Fog colour tint");
    public static final Guard SKY_TINT_GUARD = Guard.named("Sky colour tint");
    public static final Guard STORM_RAIN_LEVEL_GUARD = Guard.named("Storm rain level");
    public static final Guard RAIN_SOUND_GUARD = Guard.named("Storm rain sound");
    public static final Guard RAIN_SPLASH_GUARD = Guard.named("Vanilla rain splash suppression");
    public static final Guard WIND_BLOWN_GUARD = Guard.named("Marking wind blown particles");
    public static final Guard WIND_DRIFT_GUARD = Guard.named("Wind drift");
    public static final Guard VANILLA_FIRE_GUARD = Guard.named("Vanilla fire suppression on burning mobs");
    public static final Guard DEATH_PUFF_GUARD = Guard.named("Death mist");
    public static final Guard PARTICLE_BATCH_GUARD = Guard.named("Particle batch repair");
    // The sound engine's hooks: each sound as it plays, and each playing sound on every tick of the sound engine
    public static final Guard SOUND_FILTER_GUARD = Guard.named("Blocking, culling and remapping sounds");
    public static final Guard SOUND_RANGE_GUARD = Guard.named("Pruning sounds out of range");
    public static final Guard SOUND_VOLUME_GUARD = Guard.named("Adjusting sound volume");
    public static final Guard SOUND_START_GUARD = Guard.named("Starting a sound's enhanced processing");
    public static final Guard SOUND_TICK_GUARD = Guard.named("Updating a sound's enhanced processing");
    public static final Guard SOUND_STOP_GUARD = Guard.named("Stopping a sound's enhanced processing");

    // Resolved on first use, not when this class loads. A holder class rather than ContainerManager.memoize():
    // the biome library is used for every biome fog colour lookup (hundreds per frame), and a memoized proxy goes
    // through reflection on every call.
    private static final class BiomeLibraryHolder {
        static final IBiomeLibrary INSTANCE = ContainerManager.resolve(IBiomeLibrary.class);
    }

    public static IBiomeLibrary biomeLibrary() {
        return BiomeLibraryHolder.INSTANCE;
    }

    // Read twice a frame and once a tick by the weather renderer
    private static final class WeatherSimulationHolder {
        static final IWeatherSimulation INSTANCE = ContainerManager.resolve(IWeatherSimulation.class);
    }

    public static IWeatherSimulation weatherSimulation() {
        return WeatherSimulationHolder.INSTANCE;
    }

    // Asked every frame to tint the fog's colour
    private static final class FogHandlerHolder {
        static final FogHandler INSTANCE = ContainerManager.resolve(FogHandler.class);
    }

    public static FogHandler fogHandler() {
        return FogHandlerHolder.INSTANCE;
    }

    // Asked every frame whether it draws the weather
    private static final class PrecipitationHolder {
        static final PrecipitationHandler INSTANCE = ContainerManager.resolve(PrecipitationHandler.class);
    }

    public static PrecipitationHandler precipitation() {
        return PrecipitationHolder.INSTANCE;
    }

    // Asked every frame to draw the mist
    private static final class GroundFogHolder {
        static final GroundFogHandler INSTANCE = ContainerManager.resolve(GroundFogHandler.class);
    }

    public static GroundFogHandler groundFog() {
        return GroundFogHolder.INSTANCE;
    }

    // Asked every frame to draw the frost on windows
    private static final class WindowFrostHolder {
        static final WindowFrostHandler INSTANCE = ContainerManager.resolve(WindowFrostHandler.class);
    }

    public static WindowFrostHandler windowFrost() {
        return WindowFrostHolder.INSTANCE;
    }

}
