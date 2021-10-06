package org.orecruncher.dsurround.config.adapters;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundEvent;
import org.orecruncher.dsurround.config.BiomeInfo;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.SoundLibrary;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

/**
 * Proxy for a biomes' music.  This proxy will defer to Dynamic Surroundings configurations to determine
 * what music to play.
 */
@Environment(EnvType.CLIENT)
public class MusicSoundAdaptor extends MusicSound {

    private static final int MIN_TICKS = 20;
    private static final int MAX_TICKS = 600;

    private final BiomeInfo info;

    public MusicSoundAdaptor(BiomeInfo biomeInfo) {
        super(SoundLibrary.MISSING, MIN_TICKS, MAX_TICKS, false);
        this.info = biomeInfo;
    }

    @Override
    public SoundEvent getSound() {
        return this.info.getExtraSound(SoundEventType.MUSIC, XorShiftRandom.current());
    }
}
