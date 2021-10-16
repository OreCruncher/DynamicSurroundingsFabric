package org.orecruncher.dsurround.sound;

import com.google.common.base.MoreObjects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class BackgroundSoundLoop extends MovingSoundInstance {

    private static final float INITIAL_SCALE = 0.00002F;
    private static final float SCALE_AMOUNT = 0.02F;

    private float scale;
    private float target;
    private boolean isFading;

    public BackgroundSoundLoop(SoundEvent soundEvent) {
        super(soundEvent, SoundCategory.AMBIENT);
        this.scale = INITIAL_SCALE;
        this.target = 1F;
        this.isFading = false;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = AttenuationType.NONE;
        this.relative = true; // What is this guy really supposed to do?
    }

    public void tick() {
        if (this.scale < this.target && !this.isFading)
            this.scale += SCALE_AMOUNT;
        else if (this.isFading || this.scale > this.target)
            this.scale -= SCALE_AMOUNT;

        this.scale = MathHelper.clamp(this.scale, 0F, this.target);

        if (Float.compare(this.getVolume(), 0F) == 0)
            this.setDone();
    }

    @Override
    public float getVolume() {
        return super.getVolume() * this.scale;
    }

    public void setScaleTarget(float target) {
        this.target = target;
    }

    public boolean isFading() {
        return this.isFading;
    }

    public void fadeOut() {
        this.isFading = true;
    }

    public void fadeIn() {
        this.isFading = false;
    }

    @Override
    public String toString() {
        var temp = MoreObjects.toStringHelper(this)
                .addValue(this.getId().toString())
                .addValue(this.getCategory().getName());

        // Possible that the sound was not yet assigned.  Seen issues when exiting worlds.
        if (this.sound != null) {
            temp.add("v", getVolume())
                .add("ev", SoundVolumeEvaluator.getAdjustedVolume(this))
                .add("p", getPitch());
        }

        return temp.add("f", this.scale)
            .add("fading", isFading())
            .toString();
    }
}