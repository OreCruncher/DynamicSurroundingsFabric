package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;

public class WeatherFogRangeCalculator extends VanillaFogRangeCalculator {

    protected static final float START_IMPACT = 0.9F;
    protected static final float END_IMPACT = 0.4F;

    // Sky light below which rain adds no fog, and the range over which it builds to full
    static final int MIN_SKY_LIGHT = 8;
    static final float SKY_LIGHT_RANGE = 7F;
    // Rain fog in a biome without precipitation, where rain falls nearby but not on the player
    static final float DRY_BIOME_FACTOR = 0.5F;
    // Fraction of the way to the target covered each tick
    static final float EASE_PER_TICK = 0.2F;

    private float previous;
    private float current;

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
        float rainStr = Mth.lerp(partialTick, this.previous, this.current);
        if (rainStr > 0) {
            final float startScale = 1F - (START_IMPACT * rainStr);
            final float endScale = 1F - (END_IMPACT * rainStr);
            return withRange(data, data.start * startScale, data.end * endScale);
        }

        return data;
    }

    @Override
    public void tick() {
        var world = GameUtils.getWorld().orElseThrow();
        var pos = BlockPos.containing(GameUtils.getPlayer().orElseThrow().getEyePosition());
        var target = rainFogStrength(world.getRainLevel(1F), world.getBrightness(LightLayer.SKY, pos), world.getBiome(pos).value().hasPrecipitation());
        this.previous = this.current;
        this.current += (target - this.current) * EASE_PER_TICK;
    }

    @Override
    public void disconnect() {
        this.previous = this.current = 0F;
    }

    /**
     * How strongly rain thickens the fog where the player is: only out under the sky, as the game's own rain fog does
     * in later versions, so caves and buildings stay clear, and less where the biome itself gets no rain.
     */
    static float rainFogStrength(float rainLevel, int skyLight, boolean biomeHasPrecipitation) {
        var exposure = Mth.clamp((skyLight - MIN_SKY_LIGHT) / SKY_LIGHT_RANGE, 0F, 1F);
        return rainLevel * exposure * (biomeHasPrecipitation ? 1F : DRY_BIOME_FACTOR);
    }
}
