package org.orecruncher.dsurround.sound;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.gui.sound.ConfigSoundInstance;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Handles sound block and culling.
 */
@Environment(EnvType.CLIENT)
public final class SoundInstanceHandler {

    private static final IAudioPlayer audioPlayer = ContainerManager.resolve(IAudioPlayer.class);
    private static final ITickCount tickCount = ContainerManager.resolve(ITickCount.class);
    private static final Configuration config = ContainerManager.resolve(Configuration.class);

    private static final Object2LongOpenHashMap<Identifier> soundCull = new Object2LongOpenHashMap<>(32);
    private static final Set<Identifier> thunderSounds = new HashSet<>();
    private static final ISoundFactory THUNDER_SOUND;

    static {
        thunderSounds.add(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER.getId());

        THUNDER_SOUND = SoundFactoryBuilder.create(new Identifier(Client.ModId, "thunder"))
                .category(SoundCategory.WEATHER)
                .volume(10000)
                .build();
    }

    private static boolean isSoundBlocked(final Identifier id) {
        return Client.SoundConfig.isBlocked(id);
    }

    private static boolean isSoundCulled(final Identifier id) {
        return Client.SoundConfig.isCulled(id);
    }

    private static boolean isSoundCulledLogical(final Identifier sound) {
        int cullInterval = config.soundSystem.cullInterval;
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
     * Based on configuration the sound play will be discarded if it is blocked or if it is
     * within it's culling interval.
     *
     * @param theSound The sound that is being played
     * @return True if the sound play is to be blocked, false otherwise
     */
    public static boolean shouldBlockSoundPlay(final SoundInstance theSound) {
        // Don't block ConfigSoundInstances.  They are triggered from the individual sound config
        // options and though it may be blocked the player may wish to hear.
        if (theSound instanceof ConfigSoundInstance)
            return false;

        final Identifier id = theSound.getId();

        if (config.thunderStorms.replaceThunderSounds && thunderSounds.contains(id)) {
            // Yeah - a bit reentrant but it should be good
            var sound = THUNDER_SOUND.createAtLocation(
                    new Vec3d(theSound.getX(), theSound.getY(), theSound.getZ()));
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
    public static boolean inRange(final Vec3d listener, final SoundInstance sound, final int pad) {
        // Do not cancel if:
        // - The sound is global.  Distance is not a factor.
        // - It is repeatable.  Player can move into range.
        // - Weather related (thunder, lightening strike)
        if (sound.isRelative() || sound.getAttenuationType() == SoundInstance.AttenuationType.NONE || sound.isRepeatable() || sound.getCategory() == SoundCategory.WEATHER)
            return true;

        // If it is a loud sound let it through
        if (sound.getVolume() > 1F)
            return true;

        // Get the max distance of the sound range.  Pad is added because a player may move into hearing
        // range before the sound terminates.
        int distSq = sound.getSound().getAttenuation() + pad;
        distSq *= distSq;
        return listener.squaredDistanceTo(sound.getX(), sound.getY(), sound.getZ()) < distSq;
    }

    public static boolean inRange(final Vec3d listener, final SoundInstance sound) {
        return inRange(listener, sound, 0);
    }
}
