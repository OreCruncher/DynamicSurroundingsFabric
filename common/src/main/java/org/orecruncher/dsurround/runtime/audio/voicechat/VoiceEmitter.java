package org.orecruncher.dsurround.runtime.audio.voicechat;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.runtime.audio.ISoundEmitter;

/**
 * A voice: voice chat gives its position with each packet it plays.
 */
final class VoiceEmitter implements ISoundEmitter {

    // Set by the voice's speaker thread, read by the worker threads
    private volatile Vec3 position;

    VoiceEmitter(Vec3 position) {
        this.position = position;
    }

    void moveTo(Vec3 position) {
        this.position = position;
    }

    @Override
    public Vec3 position() {
        return this.position;
    }

    @Override
    public SoundSource category() {
        return SoundSource.VOICE;
    }

    /**
     * Always: voice chat stops playing a voice that is out of range.
     */
    @Override
    public boolean inRange(Vec3 listener) {
        return true;
    }

    @Override
    public String describe() {
        return "voice at " + this.position;
    }
}
