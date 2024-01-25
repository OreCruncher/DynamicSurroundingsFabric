package org.orecruncher.dsurround.sound;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.gui.sound.ConfigSoundInstance;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Handles sound block and culling.
 */
public final class SoundInstanceHandler {

    private static final ISoundLibrary SOUND_LIBRARY = ContainerManager.resolve(ISoundLibrary.class);
    private static final IAudioPlayer AUDIO_PLAYER = ContainerManager.resolve(IAudioPlayer.class);
    private static final ITickCount TICK_COUNT = ContainerManager.resolve(ITickCount.class);
    private static final Configuration.SoundSystem SOUND_SYSTEM_CONFIG = ContainerManager.resolve(Configuration.SoundSystem.class);
    private static final Configuration.SoundOptions THUNDERSTORM_CONFIG = ContainerManager.resolve(Configuration.SoundOptions.class);

    private static final Object2LongOpenHashMap<ResourceLocation> SOUND_CULL = new Object2LongOpenHashMap<>(32);
    private static final Set<ResourceLocation> THUNDER_SOUNDS = new HashSet<>();
    private static final ResourceLocation THUNDER_SOUND_FACTORY = new ResourceLocation(Constants.MOD_ID, "thunder");

    static {
        THUNDER_SOUNDS.add(SoundEvents.LIGHTNING_BOLT_THUNDER.getLocation());
    }

    private static boolean isSoundBlocked(final ResourceLocation id) {
        return SOUND_LIBRARY.isBlocked(id);
    }

    private static boolean isSoundCulled(final ResourceLocation id) {
        return SOUND_LIBRARY.isCulled(id);
    }

    private static boolean isSoundCulledLogical(final ResourceLocation sound) {
        int cullInterval = SOUND_SYSTEM_CONFIG.cullInterval;
        if (cullInterval > 0 && isSoundCulled(sound)) {
            final long lastOccurrence = SOUND_CULL.getLong(Objects.requireNonNull(sound));
            final long currentTick = TICK_COUNT.getTickCount();
            if ((currentTick - lastOccurrence) < cullInterval) {
                return true;
            } else {
                // Set when it happened and fall through for remapping and stuff
                SOUND_CULL.put(sound, currentTick);
            }
        }
        return false;
    }

    /**
     * Special hook in the Minecraft SoundSystem that will be invoked when a sound is played.
     * Based on configuration, the sound play will be discarded if it is blocked or if it is
     * within its culling interval.
     *
     * @param theSound The sound that is being played
     * @return True if the sound play is to be blocked, false otherwise
     */
    public static boolean shouldBlockSoundPlay(final SoundInstance theSound) {
        // Don't block ConfigSoundInstances.  They are triggered from the individual sound config
        // options, and though it may be blocked, the player may wish to hear.
        if (theSound instanceof ConfigSoundInstance)
            return false;

        final ResourceLocation id = theSound.getLocation();

        if (THUNDERSTORM_CONFIG.replaceThunderSounds && THUNDER_SOUNDS.contains(id)) {
            // Yeah - a bit reentrant but it should be good
            var soundFactory = SOUND_LIBRARY.getSoundFactory(THUNDER_SOUND_FACTORY);
            if (soundFactory.isPresent()) {
                var sound = soundFactory.get().createAtLocation(new Vec3(theSound.getX(), theSound.getY(), theSound.getZ()));
                AUDIO_PLAYER.play(sound);
                return true;
            }
        }

        return isSoundBlocked(id) || isSoundCulledLogical(id);
    }

    /**
     * Determines if a sound is in range of a listener based on the sound's properties.
     *
     * @param listener Location of the listener
     * @param sound    The sound that is to be evaluated
     * @param pad      Additional distance to add when evaluating
     * @return true if the sound is within the attenuation distance; false otherwise
     */
    public static boolean inRange(final Vec3 listener, final SoundInstance sound, final int pad) {
        // Do not cancel if:
        // - The sound is global. Distance is not a factor.
        // - Weather related (thunder, lightning strike)
        if (sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE || sound.getSource() == SoundSource.WEATHER)
            return true;

        // Make sure a sound is assigned so that the volume check can work
        sound.resolve(GameUtils.getSoundManager());

        // If it is a loud sound, let it through
        if (sound.getVolume() > 1F)
            return true;

        // Get the max distance of the sound range.  Pad is added because a player may move into hearing
        // range before the sound terminates.
        int distSq = sound.getSound().getAttenuationDistance() + pad;
        distSq *= distSq;
        return listener.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) < distSq;
    }

    public static boolean inRange(final Vec3 listener, final SoundInstance sound) {
        return inRange(listener, sound, 0);
    }
}
