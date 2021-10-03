package org.orecruncher.dsurround.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class IndividualSoundConfigEntry {
    public final String id;
    public int volumeScale = 100;
    public boolean block = false;
    public boolean cull = false;
    public boolean startup = false;

    IndividualSoundConfigEntry(IndividualSoundConfigEntry source) {
        this.id = source.id;
        this.volumeScale = source.volumeScale;
        this.block = source.block;
        this.cull = source.cull;
        this.startup = source.startup;
    }

    IndividualSoundConfigEntry(String id) {
        this.id = id;
    }

    public boolean isDefault() {
        this.volumeScale = MathHelper.clamp(this.volumeScale, 0, 400);
        return this.volumeScale == 100 && !this.block && !this.cull && !this.startup;
    }

    public static IndividualSoundConfigEntry createDefault(final SoundEvent event) {
        return new IndividualSoundConfigEntry(event.getId().toString());
    }

    public static IndividualSoundConfigEntry from(IndividualSoundConfigEntry source) {
        return new IndividualSoundConfigEntry(source);
    }
}
