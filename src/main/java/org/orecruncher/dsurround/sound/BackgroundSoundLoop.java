package org.orecruncher.dsurround.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class BackgroundSoundLoop extends MovingSoundInstance {

    private float scale;
    private int delta;
    private int strength;

    public BackgroundSoundLoop(SoundEvent soundEvent) {
        super(soundEvent, SoundCategory.AMBIENT);
        this.scale = 1F;
        this.fadeIn();
    }

    public void tick() {
        this.strength += this.delta;
        float volume = (float) this.strength / 40.0F * this.scale;

        if (volume <= 0) {
            this.setDone();
        }

        this.volume = MathHelper.clamp(volume, 0.0F, 1.0F);
    }

    public void setScaling(float scale) {
        this.scale = scale;
    }

    public boolean isFading() {
        return this.delta < 0;
    }

    public void fadeOut() {
        this.strength = Math.min(this.strength, 40);
        this.delta = -1;
    }

    public void fadeIn() {
        this.strength = Math.max(0, this.strength);
        this.delta = 1;
    }
}