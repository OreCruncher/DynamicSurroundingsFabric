package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.Identifier;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.logging.IModLog;

/**
 * One of the mod's render pipelines (its shaders and drawing state), and the effect drawn with it.
 * <p>
 * The pipeline is not added to vanilla's list of pipelines: those are compiled on every resource reload, and one that
 * fails stops the reload. Instead, once the game's shaders have reloaded (see MixinShaderManager), each pipeline is
 * compiled here and {@link #validate} reports the result: if it couldn't be compiled the effect isn't drawn, rather
 * than the error stopping the game from loading its resources.
 * <p>
 * Drawing goes through {@link #run}, which turns the effect off if drawing fails (see {@link RenderFailSafe}); a
 * reload turns it back on. Everything here runs on the render thread.
 */
public final class ModShader {

    private final RenderPipeline pipeline;
    private final String withoutIt;
    private final IModLog logger;
    private final RenderFailSafe failSafe;
    private boolean loaded;

    /**
     * @param pipeline  the pipeline; its shaders are in the mod's assets, shaders/core
     * @param effect    what is drawn with it, for the log ("the aurora")
     * @param withoutIt what happens without it, for the log ("auroras will not be drawn")
     * @param logger    where to report loading and failures
     */
    public ModShader(RenderPipeline pipeline, String effect, String withoutIt, IModLog logger) {
        this.pipeline = pipeline;
        this.withoutIt = withoutIt;
        this.logger = logger;
        this.failSafe = new RenderFailSafe(effect, logger);
    }

    public Identifier id() {
        return this.pipeline.getLocation();
    }

    public RenderPipeline pipeline() {
        return this.pipeline;
    }

    /**
     * Compiles the pipeline with the shaders just loaded, and records whether that worked. Also turns the effect back
     * on if drawing it failed before: reloading resources is how a fix would be picked up.
     */
    public void validate() {
        boolean valid;
        try {
            valid = RenderSystem.getDevice().precompilePipeline(this.pipeline).isValid();
        } catch (Throwable t) {
            this.onFailed(t);
            return;
        }
        this.onCompiled(valid);
    }

    /**
     * Records the result of compiling the pipeline.
     */
    void onCompiled(boolean valid) {
        if (valid) {
            this.loaded = true;
            this.failSafe.reset();
            this.logger.info("Loaded the %s shader", this.id());
        } else {
            this.onFailed(new IllegalStateException("The pipeline did not compile; the game log has the shader errors"));
        }
    }

    /**
     * Records that the pipeline couldn't be compiled.
     */
    void onFailed(Throwable error) {
        this.loaded = false;
        this.logger.error(error, "Unable to load the %s shader; %s", this.id(), this.withoutIt);
    }

    /**
     * Whether the effect can be drawn: the pipeline compiled, and drawing hasn't failed since.
     */
    public boolean isAvailable() {
        return this.loaded && !this.failSafe.isFailed();
    }

    /**
     * Whether the effect should be drawn: it {@link #isAvailable is available}, and no Iris shader pack is in use (a
     * pack replaces the rendering, so a shader of ours wouldn't fit in).
     */
    public boolean isUsable() {
        return this.isAvailable() && !IrisCompat.isShaderPackInUse();
    }

    /**
     * Draws the effect, guarded: see {@link RenderFailSafe#run}.
     *
     * @return true if drawing ran and finished
     */
    public boolean run(Runnable draw, Runnable onFailure) {
        return this.failSafe.run(draw, onFailure);
    }
}
