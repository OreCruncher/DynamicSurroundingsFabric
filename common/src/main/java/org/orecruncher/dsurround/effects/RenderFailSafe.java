package org.orecruncher.dsurround.effects;

import org.orecruncher.dsurround.lib.logging.IModLog;

/**
 * Guards an effect's drawing. If drawing throws, the error is logged once and the effect is turned off, instead of
 * the error being thrown every frame or ending the game, and the caller gets the chance to put back anything it left
 * half done (drawing state, a half built buffer). Turned back on by {@link #reset}, which effects call when their
 * shader is reloaded: reloading resources (F3+T) is how a player would pick up a fix.
 * <p>
 * Errors the JVM can't recover from (running out of memory, say) are passed on rather than caught. Used on the render
 * thread only.
 */
public final class RenderFailSafe {

    private final String name;
    private final IModLog logger;
    private boolean failed;

    /**
     * @param name   what is being drawn, for the log
     * @param logger where to report a failure
     */
    public RenderFailSafe(String name, IModLog logger) {
        this.name = name;
        this.logger = logger;
    }

    /**
     * Whether the effect has been turned off after failing.
     */
    public boolean isFailed() {
        return this.failed;
    }

    /**
     * Turns the effect back on.
     */
    public void reset() {
        this.failed = false;
    }

    /**
     * Draws, unless an earlier failure turned the effect off. Drawing state the body changes should be put back in a
     * finally inside the body, so it is put back whether or not drawing succeeds.
     *
     * @param body      the drawing
     * @param onFailure run after a failure is logged, to tidy up what the body left half done
     * @return true if the body ran and finished
     */
    public boolean run(Runnable body, Runnable onFailure) {
        if (this.failed)
            return false;
        try {
            body.run();
            return true;
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable t) {
            this.failed = true;
            this.logger.error(t, "Drawing %s failed; it is turned off until resources are reloaded", this.name);
            try {
                onFailure.run();
            } catch (Throwable cleanup) {
                this.logger.error(cleanup, "Unable to tidy up after %s failed", this.name);
            }
            return false;
        }
    }
}
