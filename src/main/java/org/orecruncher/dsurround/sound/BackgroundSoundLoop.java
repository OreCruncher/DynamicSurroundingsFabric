package org.orecruncher.dsurround.sound;

import com.google.common.base.MoreObjects;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.random.Randomizer;

public class BackgroundSoundLoop extends AbstractTickableSoundInstance {

    private static final float INITIAL_SCALE = 0.00002F;
    private static final float SCALE_AMOUNT = 0.033F;

    private float scale;
    private float target;
    private boolean isFading;

    public BackgroundSoundLoop(SoundEvent soundEvent) {
        super(soundEvent, SoundSource.AMBIENT, Randomizer.current());
        this.scale = INITIAL_SCALE;
        this.target = 1F;
        this.isFading = false;
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.NONE;
        this.relative = true;
    }

    public BackgroundSoundLoop(SoundEvent soundEvent, BlockPos pos) {
        super(soundEvent, SoundSource.AMBIENT, Randomizer.current());
        this.scale = INITIAL_SCALE;
        this.target = 1F;
        this.isFading = false;
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.LINEAR;
        this.relative = false;

        this.x = pos.getX() + 0.5F;
        this.y = pos.getY() + 0.5F;
        this.z = pos.getZ() + 0.5F;
    }

    public void tick() {
        if (this.scale < this.target && !this.isFading)
            this.scale += SCALE_AMOUNT;
        else if (this.isFading || this.scale > this.target)
            this.scale -= SCALE_AMOUNT;

        this.scale = Mth.clamp(this.scale, 0F, this.target);

        if (Float.compare(this.getVolume(), 0F) == 0)
            this.stop();
    }

    public BlockPos getPos() {
        return BlockPos.containing(this.x, this.y, this.z);
    }

    public BackgroundSoundLoop setVolume(float volume) {
        this.volume = volume;
        return this;
    }

    public BackgroundSoundLoop setPitch(float pitch) {
        this.pitch = pitch;
        return this;
    }

    @Override
    public float getVolume() {
        // Possible that the sound was not yet assigned.  Seen issues when exiting worlds.
        if (this.sound != null) {
            return super.getVolume() * this.scale;
        } else {
            return 0F;
        }
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
    public @NotNull String toString() {
        var temp = MoreObjects.toStringHelper(this)
                .addValue(this.getLocation().toString())
                .addValue(this.getSource().getName());

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