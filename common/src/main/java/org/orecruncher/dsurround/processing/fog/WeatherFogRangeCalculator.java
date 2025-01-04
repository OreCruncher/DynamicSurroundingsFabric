package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;

public class WeatherFogRangeCalculator extends VanillaFogRangeCalculator {

    protected static final float START_IMPACT = 0.9F;
    protected static final float END_IMPACT = 0.4F;

    protected WeatherFogRangeCalculator(Configuration.FogOptions fogOptions) {
        super("Weather", fogOptions);
    }

    @Override
    public boolean enabled() {
        return this.fogOptions.enableWeatherFog;
    }

    @Override
    @NotNull
    public FogRenderer.FogData render(@NotNull final FogRenderer.FogData data, float renderDistance, float partialTick) {
        float rainStr = GameUtils.getWorld().map(w -> w.getRainLevel(partialTick)).orElseThrow();
        if (rainStr > 0) {
            final float startScale = 1F - (START_IMPACT * rainStr);
            final float endScale = 1F - (END_IMPACT * rainStr);
            var result = new FogRenderer.FogData(data.mode);
            result.start = data.start * startScale;
            result.end = data.end * endScale;
            return result;
        }

        return data;
    }
}
