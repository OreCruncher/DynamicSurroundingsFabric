package org.orecruncher.dsurround.mixinutils;

import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.biome.BiomeMusicSelector;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

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

}
