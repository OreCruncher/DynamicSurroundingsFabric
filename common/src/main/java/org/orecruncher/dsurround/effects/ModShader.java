package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.function.Supplier;

/**
 * One of the mod's core shaders, and the effect drawn with it. Each platform registers the shader with its own API
 * (see {@link ModShaders}) and hands over the result: {@link #onLoaded} on each resource reload, or {@link #onFailed}
 * if it couldn't be compiled, in which case the effect isn't drawn rather than the error stopping the game from
 * loading its resources.
 * <p>
 * Drawing goes through {@link #run}, which turns the effect off if drawing fails (see {@link RenderFailSafe}); a
 * reload turns it back on. Everything here runs on the render thread.
 */
public final class ModShader {

    private final ResourceLocation id;
    private final VertexFormat format;
    private final String withoutIt;
    private final IModLog logger;
    private final RenderFailSafe failSafe;
    @Nullable
    private ShaderInstance instance;
    private final Supplier<ShaderInstance> supplier = () -> this.instance;

    /**
     * @param name      the shader's name: its files are shaders/core/name.json, .vsh and .fsh in the mod's assets
     * @param format    the vertex format it is drawn with
     * @param effect    what is drawn with it, for the log ("the aurora")
     * @param withoutIt what happens without it, for the log ("auroras will not be drawn")
     * @param logger    where to report loading and failures
     */
    public ModShader(String name, VertexFormat format, String effect, String withoutIt, IModLog logger) {
        this.id = Constants.asId(name);
        this.format = format;
        this.withoutIt = withoutIt;
        this.logger = logger;
        this.failSafe = new RenderFailSafe(effect, logger);
    }

    public ResourceLocation id() {
        return this.id;
    }

    public VertexFormat format() {
        return this.format;
    }

    /**
     * Called by the platform when the shader has been loaded. Also turns the effect back on if drawing it failed
     * before: reloading resources is how a fix would be picked up.
     */
    public void onLoaded(ShaderInstance loaded) {
        this.instance = loaded;
        this.failSafe.reset();
        this.logger.info("Loaded the %s shader", this.id);
    }

    /**
     * Called by the platform when the shader couldn't be loaded.
     */
    public void onFailed(Throwable error) {
        this.instance = null;
        this.logger.error(error, "Unable to load the %s shader; %s", this.id, this.withoutIt);
    }

    /**
     * The loaded shader, or null if it isn't loaded.
     */
    @Nullable
    public ShaderInstance get() {
        return this.instance;
    }

    /**
     * For RenderSystem.setShader.
     */
    public Supplier<ShaderInstance> supplier() {
        return this.supplier;
    }

    /**
     * Whether the effect can be drawn: the shader loaded, and drawing hasn't failed since.
     */
    public boolean isAvailable() {
        return this.instance != null && !this.failSafe.isFailed();
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
