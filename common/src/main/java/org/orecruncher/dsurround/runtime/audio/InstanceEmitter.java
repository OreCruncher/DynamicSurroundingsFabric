package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;

/**
 * A game sound, played by the sound engine from a {@link SoundInstance}.
 *
 * @param hearingRangeSq the square of the distance it can be heard within, taken on the client thread when it started
 *                       (see {@link SoundInstanceHandler#hearingRangeSq}): working it out draws on the sound's random
 *                       source, which the sound engine uses on the client thread, so a worker mustn't
 */
record InstanceEmitter(SoundInstance sound, double hearingRangeSq) implements ISoundEmitter {

    /**
     * Client thread only
     */
    InstanceEmitter(SoundInstance sound) {
        this(sound, SoundInstanceHandler.hearingRangeSq(sound, 0));
    }

    @Override
    public Vec3 position() {
        return new Vec3(this.sound.getX(), this.sound.getY(), this.sound.getZ());
    }

    @Override
    public SoundSource category() {
        return this.sound.getSource();
    }

    @Override
    public boolean inRange(Vec3 listener) {
        return listener.distanceToSqr(this.sound.getX(), this.sound.getY(), this.sound.getZ()) < this.hearingRangeSq;
    }

    @Override
    public String describe() {
        return AudioUtilities.debugString(this.sound);
    }
}
