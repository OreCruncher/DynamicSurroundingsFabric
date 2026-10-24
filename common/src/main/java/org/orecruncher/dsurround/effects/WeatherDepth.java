package org.orecruncher.dsurround.effects;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.Library;

/**
 * With Fabulous graphics, gives the weather's target the scene's depth at the start of the weather pass, so what is
 * drawn there is tested against the scene.
 * <p>
 * The weather's target starts the pass with no depth, and the transparency pass lays it over the scene by the one depth
 * it keeps at each pixel: the nearest thing drawn there. Rain behind a wall was drawn all the same, so where a drop in
 * front of the wall crossed it, both were in one layer at the near drop's depth, and the one behind showed through the
 * wall. With the scene's depth, what is behind the scene isn't drawn at all. Without Fabulous graphics the weather is
 * drawn to the main target, which has it already.
 * <p>
 * Render thread only, outside any render pass.
 */
public final class WeatherDepth {

    private static final RenderFailSafe FAIL_SAFE = new RenderFailSafe("the scene's depth for the weather", Library.LOGGER);

    private WeatherDepth() {
    }

    /**
     * Copies the main target's depth into the weather's, with Fabulous graphics
     *
     * @param needed whether anything of the mod's is drawn there this frame: otherwise the weather is left as vanilla
     *               has it
     */
    public static void prepare(boolean needed) {
        if (!needed)
            return;
        var minecraft = Minecraft.getInstance();
        var weather = minecraft.levelRenderer.weatherTarget();
        if (weather == null)
            return;
        var main = minecraft.gameRenderer.mainRenderTarget();
        FAIL_SAFE.run(() -> weather.copyDepthFrom(main), () -> {});
    }
}
