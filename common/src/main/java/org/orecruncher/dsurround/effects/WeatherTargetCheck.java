package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.orecruncher.dsurround.lib.Library;

/**
 * Makes sure the weather is drawn to the weather's own target with Fabulous graphics. Vanilla binds it just before the
 * weather is drawn, and the transparency pass lays it over the scene by its depth. Drawn to the main target instead,
 * each rain drop's depth hides the water behind it, and the bottom shows through the drop untinted. Should the weather's
 * target not be bound when the weather pass starts, it is bound, and the log says once what was bound in its place.
 * Render thread only.
 */
public final class WeatherTargetCheck {

    private static boolean warned;

    private WeatherTargetCheck() {
    }

    /**
     * With Fabulous graphics, binds the weather's target if something else is bound for drawing
     *
     * @param where what is about to be drawn, for the log
     */
    public static void ensureBound(String where) {
        if (!Minecraft.useShaderTransparency())
            return;
        var levelRenderer = Minecraft.getInstance().levelRenderer;
        var weather = levelRenderer.getWeatherTarget();
        if (weather == null)
            return;
        int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (bound == weather.frameBufferId)
            return;
        if (!warned) {
            warned = true;
            Library.LOGGER.warn("With Fabulous graphics the weather's target should be bound before %s, but %s was; it was bound in its place. Please report this along with your mod list.",
                    where, describe(bound,
                            id(Minecraft.getInstance().getMainRenderTarget()),
                            id(levelRenderer.getTranslucentTarget()),
                            id(levelRenderer.getItemEntityTarget()),
                            id(levelRenderer.getParticlesTarget()),
                            id(levelRenderer.getCloudsTarget())));
        }
        weather.bindWrite(false);
    }

    private static int id(@Nullable RenderTarget target) {
        return target == null ? -1 : target.frameBufferId;
    }

    /**
     * What a bound frame buffer is, by the targets the level renderer has
     */
    static String describe(int bound, int main, int translucent, int itemEntity, int particles, int clouds) {
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
        if (bound == clouds)
            return "the clouds target";
        return "frame buffer " + bound + " (not one of the level renderer's; likely another mod's)";
    }
}
