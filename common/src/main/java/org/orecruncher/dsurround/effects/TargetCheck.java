package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.orecruncher.dsurround.lib.Library;

import java.util.HashSet;
import java.util.Set;

/**
 * Makes sure what the mod draws with Fabulous graphics goes to the render target it belongs in. Vanilla binds each
 * target (particles, weather) just before drawing to it, and the transparency pass lays them over the scene by their
 * depth. Drawn to the main target instead, what the mod draws hides the water and anything else translucent behind
 * it. That happened with the weather in a large modpack: another mod left the main target bound for the weather pass,
 * and each rain drop cut the water out behind it.
 * <p>
 * Where the mod draws on a target that vanilla bound, it checks first: if another is bound, the right one is bound in
 * its place, and the log says once, for each place, what was bound instead. Render thread only.
 */
public final class TargetCheck {

    // The places a wrong target has been reported for already
    private static final Set<String> WARNED = new HashSet<>();

    private TargetCheck() {
    }

    /**
     * With Fabulous graphics, binds the weather's target if something else is bound for drawing
     *
     * @param where what is about to be drawn, for the log
     */
    public static void ensureWeather(String where) {
        if (Minecraft.useShaderTransparency())
            ensure(Minecraft.getInstance().levelRenderer.getWeatherTarget(), "weather's", where);
    }

    /**
     * With Fabulous graphics, binds the particles' target if something else is bound for drawing
     *
     * @param where what is about to be drawn, for the log
     */
    public static void ensureParticles(String where) {
        if (Minecraft.useShaderTransparency())
            ensure(Minecraft.getInstance().levelRenderer.getParticlesTarget(), "particles'", where);
    }

    /**
     * Binds {@code target} for drawing if something else is bound, and says so in the log the first time for
     * {@code where}
     *
     * @param name  the target's name, for the log
     * @param where what is about to be drawn, for the log
     */
    private static void ensure(@Nullable RenderTarget target, String name, String where) {
        if (target == null)
            return;
        int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (bound == target.frameBufferId)
            return;
        if (WARNED.add(where)) {
            var minecraft = Minecraft.getInstance();
            var levelRenderer = minecraft.levelRenderer;
            Library.LOGGER.warn("With Fabulous graphics the %s target should be bound before %s, but %s was; it was bound in its place. Please report this along with your mod list.",
                    name, where, describe(bound,
                            id(minecraft.getMainRenderTarget()),
                            id(levelRenderer.getTranslucentTarget()),
                            id(levelRenderer.getItemEntityTarget()),
                            id(levelRenderer.getParticlesTarget()),
                            id(levelRenderer.getWeatherTarget()),
                            id(levelRenderer.getCloudsTarget())));
        }
        target.bindWrite(false);
    }

    private static int id(@Nullable RenderTarget target) {
        return target == null ? -1 : target.frameBufferId;
    }

    /**
     * What a bound frame buffer is, by the targets the game has
     */
    static String describe(int bound, int main, int translucent, int itemEntity, int particles, int weather, int clouds) {
        if (bound == 0)
            return "the default frame buffer";
        if (bound == main)
            return "the main target";
        if (bound == translucent)
            return "the translucent target";
        if (bound == itemEntity)
            return "the item entity target";
        if (bound == particles)
            return "the particles target";
        if (bound == weather)
            return "the weather target";
        if (bound == clouds)
            return "the clouds target";
        return "frame buffer " + bound + " (not one of the game's; likely another mod's)";
    }
}
