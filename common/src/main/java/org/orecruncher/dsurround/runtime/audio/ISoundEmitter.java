package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * What a processed sound comes from, as its {@link SourceContext} sees it: a game sound's {@code SoundInstance}
 * ({@link InstanceEmitter}) or a voice, whose position its player pushes in. Read by the worker threads.
 */
public interface ISoundEmitter {

    /**
     * Where the sound is now. A calculation takes it once at its start.
     */
    Vec3 position();

    SoundSource category();

    /**
     * Whether the listener is close enough to hear it; if not, it is left unprocessed.
     */
    boolean inRange(Vec3 listener);

    /**
     * For logs and diagnostics.
     */
    String describe();
}
