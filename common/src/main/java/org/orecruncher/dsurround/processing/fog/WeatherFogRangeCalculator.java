package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.effects.precipitation.DustStorms;
import org.orecruncher.dsurround.effects.precipitation.IColumnSource;
import org.orecruncher.dsurround.processing.PrecipitationHandler;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;

/**
 * Fog from the weather: rain thickens it a little, and a dust storm (see {@link DustStorms}) a great deal, closing in
 * the view to a few dozen blocks at its height and turning the fog the colour of the dust.
 */
public class WeatherFogRangeCalculator extends VanillaFogRangeCalculator {

    protected static final float START_IMPACT = 0.9F;
    protected static final float END_IMPACT = 0.4F;

    // Sky light below which the weather adds no fog, and the range over which it builds to full
    static final int MIN_SKY_LIGHT = 8;
    static final float SKY_LIGHT_RANGE = 7F;
    // Rain fog where nothing falls on the player (a dry biome, or a dry season), though rain falls nearby
    static final float DRY_BIOME_FACTOR = 0.5F;
    // Fraction of the way to the target covered each tick
    static final float EASE_PER_TICK = 0.2F;

    /**
     * How far a dust storm at full strength thickens the fog, for {@link #thicken}: the fog ends at this much less of
     * its usual distance
     */
    static final float DUST_FOG_INTENSITY = 0.85F;
    // The nearest a dust storm brings the start of the fog, in blocks
    static final float DUST_MIN_START = 2F;
    // Dust fog comes and goes more slowly than rain's, so walking into or out of a desert in a storm isn't sudden
    static final float DUST_EASE_PER_TICK = 0.05F;
    /**
     * How much of the way to the dust's colour a dust storm at full strength turns the fog
     */
    static final float DUST_TINT = 0.8F;

    private final IWeatherSimulation weatherSimulation;
    private final ISeasonalInformation seasons;
    private final IBiomeLibrary biomeLibrary;
    private final Configuration.WeatherOptions weatherOptions;
    private final PrecipitationHandler precipitation;
    private float previous;
    private float current;
    private float previousDust;
    private float currentDust;
    // The colour of the dust, kept from the last dusty tick so the tint keeps it while it fades
    private int dustColor = DustStorms.SAND_COLOR;

    protected WeatherFogRangeCalculator(Configuration.FogOptions fogOptions, IWeatherSimulation weatherSimulation,
                                        ISeasonalInformation seasons, IBiomeLibrary biomeLibrary,
                                        Configuration.WeatherOptions weatherOptions, PrecipitationHandler precipitation) {
        super("Weather", fogOptions);
        this.weatherSimulation = weatherSimulation;
        this.seasons = seasons;
        this.biomeLibrary = biomeLibrary;
        this.weatherOptions = weatherOptions;
        this.precipitation = precipitation;
    }

    @Override
    public boolean enabled() {
        return this.fogOptions.enableWeatherFog;
    }

    @Override
    @NotNull
    public FogRenderer.FogData render(@NotNull final FogRenderer.FogData data, float renderDistance, float partialTick) {
        var result = data;
        float rainStr = Mth.lerp(partialTick, this.previous, this.current);
        if (rainStr > 0) {
            final float startScale = 1F - (START_IMPACT * rainStr);
            final float endScale = 1F - (END_IMPACT * rainStr);
            result = withRange(data, data.start * startScale, data.end * endScale);
        }

        float dustStr = Mth.lerp(partialTick, this.previousDust, this.currentDust);
        if (dustStr > 0)
            result = thicken(result, dustStr * DUST_FOG_INTENSITY, DUST_MIN_START);

        return result;
    }

    @Override
    public void tick() {
        var world = GameUtils.getWorld().orElseThrow();
        var pos = BlockPos.containing(GameUtils.getPlayer().orElseThrow().getEyePosition());
        var biome = world.getBiome(pos).value();
        var rainLevel = this.weatherSimulation.rainLevel(world.getRainLevel(1F));
        var skyLight = world.getBrightness(LightLayer.SKY, pos);

        // What falls here now, as the season provider says, as the particles go by: with Serene Seasons, a tropical
        // biome's season decides whether it rains, not the biome's own setting
        var fallingNow = this.seasons.getPrecipitationAt(pos);

        // In a dust storm the dust takes the place of the rain fog a dry biome would otherwise get
        var dusty = this.precipitation.isActive() && DustStorms.enabled(this.weatherOptions)
                && DustStorms.isDusty(fallingNow, biome, this.biomeLibrary);
        var target = dusty ? 0F : rainFogStrength(rainLevel, skyLight, fallingNow != Biome.Precipitation.NONE);
        var dustTarget = dusty ? dustFogStrength(rainLevel, skyLight) : 0F;
        if (dusty)
            this.dustColor = fogDustColor(this.precipitation.regionalDustColor(), DustStorms.dustColor(biome, this.biomeLibrary));

        this.previous = this.current;
        this.current += (target - this.current) * EASE_PER_TICK;
        this.previousDust = this.currentDust;
        this.currentDust += (dustTarget - this.currentDust) * DUST_EASE_PER_TICK;
    }

    @Override
    public void disconnect() {
        this.previous = this.current = 0F;
        this.previousDust = this.currentDust = 0F;
    }

    /**
     * The colour the fog turns in a dust storm: the dust's around the player, as the puffs take it, so fog and dust
     * match; or, when no dust is being drawn, what the biome is known for
     *
     * @param regional the dust's colour around the player, or {@link IColumnSource#NOT_A_DUST_SOURCE}
     * @param biome    the biome's dust colour
     */
    static int fogDustColor(int regional, int biome) {
        return regional == IColumnSource.NOT_A_DUST_SOURCE ? biome : regional;
    }

    /**
     * Turns the fog's colour toward the dust's in a dust storm.
     *
     * @param rgb the fog's colour, red, green and blue from 0 to 1; changed in place
     */
    public void tintFogColor(float[] rgb, float partialTick) {
        float dustStr = Mth.lerp(partialTick, this.previousDust, this.currentDust);
        if (dustStr > 0F)
            tint(rgb, this.dustColor, dustStr * DUST_TINT);
    }

    /**
     * Turns a fog colour {@code amount} of the way toward a dust colour. The dust's colour is first darkened to the
     * fog's own brightness, so the haze is tan by day and dark at night, rather than glowing in the dark.
     *
     * @param rgb    red, green and blue from 0 to 1; changed in place
     * @param color  the dust's colour, 0xRRGGBB
     * @param amount 0 for none of the way, 1 for all of it
     */
    static void tint(float[] rgb, int color, float amount) {
        float red = ((color >> 16) & 0xFF) / 255F, green = ((color >> 8) & 0xFF) / 255F, blue = (color & 0xFF) / 255F;
        var brightness = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        var dustBrightness = Math.max(red, Math.max(green, blue));
        var scale = dustBrightness > 0F ? brightness / dustBrightness : 0F;
        var t = Mth.clamp(amount, 0F, 1F);
        rgb[0] = Mth.lerp(t, rgb[0], red * scale);
        rgb[1] = Mth.lerp(t, rgb[1], green * scale);
        rgb[2] = Mth.lerp(t, rgb[2], blue * scale);
    }

    /**
     * How much of the open sky the player is under, 0 to 1: caves and buildings keep the weather's fog out
     */
    static float skyExposure(int skyLight) {
        return Mth.clamp((skyLight - MIN_SKY_LIGHT) / SKY_LIGHT_RANGE, 0F, 1F);
    }

    /**
     * How strongly rain thickens the fog where the player is: only out under the sky, as the game's own rain fog does
     * in later versions, so caves and buildings stay clear, and less where it isn't raining on the player.
     *
     * @param fallingHere whether rain or snow falls where the player is now, by the biome and its season
     */
    static float rainFogStrength(float rainLevel, int skyLight, boolean fallingHere) {
        return rainLevel * skyExposure(skyLight) * (fallingHere ? 1F : DRY_BIOME_FACTOR);
    }

    /**
     * How strongly a dust storm thickens the fog where the player is, 0 to 1: with the storm, and only out under the
     * sky
     */
    static float dustFogStrength(float rainLevel, int skyLight) {
        return Mth.clamp(rainLevel, 0F, 1F) * skyExposure(skyLight);
    }
}
