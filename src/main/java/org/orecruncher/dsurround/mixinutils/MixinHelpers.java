package org.orecruncher.dsurround.mixinutils;

import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

public class MixinHelpers {
    public static final IModLog LOGGER = ContainerManager.resolve(IModLog.class);
    public static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);
    public static final Configuration.SoundSystem soundSystemConfig = ContainerManager.resolve(Configuration.SoundSystem.class);
    public static final Configuration.FootstepAccents footstepAccentsConfig = ContainerManager.resolve(Configuration.FootstepAccents.class);
    public static final Configuration.ParticleTweaks particleTweaksConfig = ContainerManager.resolve(Configuration.ParticleTweaks.class);
    public static final Configuration.SoundOptions soundOptions = ContainerManager.resolve(Configuration.SoundOptions.class);

}
