package org.orecruncher.dsurround.sound;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.gui.sound.ConfigSoundInstance;
import org.orecruncher.dsurround.lib.TickCounter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Handles sound block and culling.
 */
@Environment(EnvType.CLIENT)
public final class SoundInstanceHandler {

    private static final Object2LongOpenHashMap<Identifier> soundCull = new Object2LongOpenHashMap<>(32);
    private static final Set<Identifier> thunderSounds = new HashSet<>();

    static {
        thunderSounds.add(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER.getId());
    }

    private static boolean isSoundBlocked(final Identifier id) {
        return Client.SoundConfig.isBlocked(id);
    }

    private static boolean isSoundCulled(final Identifier id) {
        return Client.SoundConfig.isCulled(id);
    }

    private static boolean isSoundCulledLogical(final Identifier sound) {
        int cullInterval = Client.Config.soundSystem.cullInterval;
        if (cullInterval > 0 && isSoundCulled(sound)) {
            final long lastOccurrence = soundCull.getLong(Objects.requireNonNull(sound));
            final long currentTick = TickCounter.getTickCount();
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

        if (Client.Config.thunderStorms.replaceThunderSounds && thunderSounds.contains(id)) {
            // Yeah - a bit reentrant but it should be good
            var sound = SoundFactory.cloneThunder(theSound);
            MinecraftAudioPlayer.INSTANCE.play(sound);
            return true;
        }

        return isSoundBlocked(id) || isSoundCulledLogical(id);
    }
}
