package org.orecruncher.dsurround.sound;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;

/**
 * How far a sound carries, as the game works it out. The game plays a sound however far away it is: its channel's
 * gain falls linearly (OpenAL's clamped linear model) to nothing at {@link #audibleRange}, so beyond that it plays
 * silently, and is heard if the listener or the sound comes closer while it lasts.
 */
public final class SoundRange {

    private SoundRange() {
    }

    /**
     * The distance at which a sound fades to nothing: its attenuation distance, carried further by a volume above 1
     * (the game's own sum in SoundEngine.play)
     */
    public static float audibleRange(float volume, int attenuationDistance) {
        return Math.max(volume, 1F) * attenuationDistance;
    }

    /**
     * Whether a sound may still be heard after starting out of range, so it can't be dropped for being far: it loops,
     * it ticks (and may move closer, as a minecart does), or it is streamed (music discs, long recordings)
     */
    public static boolean lasts(SoundInstance sound) {
        if (sound.isLooping() || sound instanceof TickableSoundInstance)
            return true;
        //noinspection ConstantValue
        return sound.getSound() != null && sound.getSound().shouldStream();
    }
}
