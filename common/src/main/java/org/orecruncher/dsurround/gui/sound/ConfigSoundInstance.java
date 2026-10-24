package org.orecruncher.dsurround.gui.sound;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.function.Supplier;

/**
 * Special sound instance created by the sound configuration option menu.  The type is detected through the pipeline
 * to avoid applying behaviors like blocking and volume scaling.
 * <p>
 * It is tickable so the sound engine re-reads {@link #getVolume()} every tick, which lets the volume slider change
 * a sound that is already playing.
 */
public class ConfigSoundInstance extends SimpleSoundInstance implements TickableSoundInstance {

    private final Supplier<Float> volumeScale;

    ConfigSoundInstance(Identifier id, SoundSource category, Supplier<Float> volumeScale) {
        // Base volume is 1: getVolume() applies the scale, so passing it here as well would apply it twice
        super(id, category, 1F, 1F, Randomizer.current(), false, 0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);

        this.volumeScale = volumeScale;
    }

    @Override
    public float getVolume() {
        return super.getVolume() * this.volumeScale.get();
    }

    public static ConfigSoundInstance create(Identifier location, SoundSource category, Supplier<Float> volumeScale) {
        return new ConfigSoundInstance(location, category, volumeScale);
    }

    @Override
    public boolean isStopped() {
        return false;
    }

    @Override
    public void tick() {

    }
}
