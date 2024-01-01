package org.orecruncher.dsurround.sound;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.gui.sound.ConfigSoundInstance;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Handles sound block and culling.
 */
public final class SoundInstanceHandler {

    private static final ISoundLibrary soundLibrary = ContainerManager.resolve(ISoundLibrary.class);
    private static final IAudioPlayer audioPlayer = ContainerManager.resolve(IAudioPlayer.class);
    private static final ITickCount tickCount = ContainerManager.resolve(ITickCount.class);
    private static final Configuration.SoundSystem SOUND_SYSTEM_CONFIG = ContainerManager.resolve(Configuration.SoundSystem.class);
    private static final Configuration.ThunderStorms THUNDERSTORM_CONFIG = ContainerManager.resolve(Configuration.ThunderStorms.class);

    private static final Object2LongOpenHashMap<ResourceLocation> soundCull = new Object2LongOpenHashMap<>(32);
    private static final Set<ResourceLocation> thunderSounds = new HashSet<>();
    private static final ISoundFactory THUNDER_SOUND;

    static {
        thunderSounds.add(SoundEvents.LIGHTNING_BOLT_THUNDER.getLocation());

        THUNDER_SOUND = SoundFactoryBuilder.create(new ResourceLocation(Constants.MOD_ID, "thunder"))
                .category(SoundSource.WEATHER)
                .volume(10000)
                .build();
    }

    private static boolean isSoundBlocked(final ResourceLocation id) {
        return soundLibrary.isBlocked(id);
    }

    private static boolean isSoundCulled(final ResourceLocation id) {
        return soundLibrary.isCulled(id);
    }

    private static boolean isSoundCulledLogical(final ResourceLocation sound) {
        int cullInterval = SOUND_SYSTEM_CONFIG.cullInterval;
        if (cullInterval > 0 && isSoundCulled(sound)) {
            final long lastOccurrence = soundCull.getLong(Objects.requireNonNull(sound));
            final long currentTick = tickCount.getTickCount();
            if ((currentTick - lastOccurrence) < cullInterval) {
                return true;
            } else {
                // Set when it happened and fall through for remapping and stuff
                soundCull.put(sound, currentTick);
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

        if (THUNDERSTORM_CONFIG.replaceThunderSounds && thunderSounds.contains(id)) {
            // Yeah - a bit reentrant but it should be good
            var sound = THUNDER_SOUND.createAtLocation(
                    new Vec3(theSound.getX(), theSound.getY(), theSound.getZ()));
            audioPlayer.play(sound);
            return true;
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
        // - It is repeatable.  Player can move into range.
        // - Weather related (thunder, lightning strike)
        if (sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE || sound.isLooping() || sound.getSource() == SoundSource.WEATHER)
            return true;

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
