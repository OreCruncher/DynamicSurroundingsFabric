package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;

/**
 * A game sound, played by the sound engine from a {@link SoundInstance}.
 */
record InstanceEmitter(SoundInstance sound) implements ISoundEmitter {

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
        return SoundInstanceHandler.inRange(listener, this.sound);
    }

    @Override
    public String describe() {
        return AudioUtilities.debugString(this.sound);
    }
}
