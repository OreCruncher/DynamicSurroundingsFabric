// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

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

    /**
     * A loop that plays at the listener, not fading with distance.
     */
    public BackgroundSoundLoop(SoundEvent soundEvent) {
        super(soundEvent, SoundSource.AMBIENT, Randomizer.current());
        this.init();
        this.attenuation = Attenuation.NONE;
        this.relative = true;
    }

    /**
     * A loop at the center of the block, fading with distance.
     */
    public BackgroundSoundLoop(SoundEvent soundEvent, BlockPos pos) {
        super(soundEvent, SoundSource.AMBIENT, Randomizer.current());
        this.init();
        this.attenuation = Attenuation.LINEAR;
        this.relative = false;
        this.x = pos.getX() + 0.5D;
        this.y = pos.getY() + 0.5D;
        this.z = pos.getZ() + 0.5D;
    }

    private void init() {
        this.scale = INITIAL_SCALE;
        this.target = 1F;
        this.isFading = false;
        this.looping = true;
        this.delay = 0;
    }

    public void tick() {
        this.scale = nextScale(this.scale, this.target, this.isFading);

        if (Float.compare(this.getVolume(), 0F) == 0)
            this.stop();
    }

    /**
     * A tick's step towards the target, or towards silence while fading out. Stops at the target, in both directions:
     * a sound following the wind has its target changed every tick, and a full step down for each small fall would
     * take a quiet one nearly to silence and back, tick after tick, or stop it.
     */
    static float nextScale(float scale, float target, boolean fading) {
        if (fading)
            return Mth.clamp(scale - SCALE_AMOUNT, 0F, target);
        return scale < target ? Math.min(scale + SCALE_AMOUNT, target) : Math.max(scale - SCALE_AMOUNT, target);
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
        //noinspection ConstantValue
        return this.sound == null ? 0F : super.getVolume() * this.scale;
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
                .addValue(this.getIdentifier().toString())
                .addValue(this.getSource().getName());

        // Possible that the sound was not yet assigned.  Seen issues when exiting worlds.
        //noinspection ConstantValue
        if (this.sound != null) {
            temp.add("v", getVolume())
                    .add("ev", SoundVolumeEvaluator.getAdjustedVolume(this, null))
                    .add("p", getPitch());
        }

        return temp.add("f", this.scale)
            .add("fading", isFading())
            .toString();
    }
}