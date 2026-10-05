package org.orecruncher.dsurround.runtime.audio;

/**
 * Decides whether a sound's ray traced results can be reused: they can if the sound and the player's eyes are in
 * the same blocks as when they were traced, in the same world, and nothing in the world has changed since (see
 * {@link WorldChangeTracker}). Movement within a block is ignored; its effect on the results is negligible.
 * <p>
 * Used by one sound's calculation at a time, so not thread-safe.
 */
final class TraceCache {

    private Object world;
    private long soundBlock;
    private long eyeBlock;
    private long generation;
    private boolean valid;

    /**
     * Whether results traced under these conditions are still good.
     */
    boolean matches(Object world, long soundBlock, long eyeBlock, long generation) {
        return this.valid
                && this.world == world
                && this.soundBlock == soundBlock
                && this.eyeBlock == eyeBlock
                && this.generation == generation;
    }

    /**
     * Records the conditions results were just traced under. {@code generation} should be read before tracing, so
     * a change while tracing makes the next check fail.
     */
    void update(Object world, long soundBlock, long eyeBlock, long generation) {
        this.world = world;
        this.soundBlock = soundBlock;
        this.eyeBlock = eyeBlock;
        this.generation = generation;
        this.valid = true;
    }

    void invalidate() {
        this.valid = false;
        this.world = null;
    }
}
