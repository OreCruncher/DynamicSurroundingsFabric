package org.orecruncher.dsurround.mixinutils;

import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.biome.BiomeMusicSelector;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.processing.FogHandler;
import org.orecruncher.dsurround.processing.GroundFogHandler;
import org.orecruncher.dsurround.processing.PrecipitationHandler;
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
    public static final Configuration.SoundOptions soundOptions = ContainerManager.resolve(Configuration.SoundOptions.class);
    public static final Configuration.FogOptions fogOptions = ContainerManager.resolve(Configuration.FogOptions.class);
    public static final Configuration.WeatherOptions weatherOptions = ContainerManager.resolve(Configuration.WeatherOptions.class);
    public static final Configuration.MusicManagerOptions musicOptions = ContainerManager.resolve(Configuration.MusicManagerOptions.class);
    public static final BiomeMusicSelector BIOME_MUSIC = new BiomeMusicSelector();

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

}
