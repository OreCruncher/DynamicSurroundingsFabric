package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.LogThrottle;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.processing.PrecipitationHandler;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;

import java.util.Optional;
import java.util.stream.Collectors;

public class HolisticFogRangeCalculator implements IFogRangeCalculator {

    protected final IModLog logger;
    protected final Configuration.FogOptions fogOptions;
    protected final ObjectArray<IFogRangeCalculator> calculators = new ObjectArray<>(3);
    private final WeatherFogRangeCalculator weather;
    // A bad range would be reported every frame: once for each calculator is enough
    private final LogThrottle<String> invalidRanges;

    public HolisticFogRangeCalculator(IModLog logger, Configuration.FogOptions fogOptions, IBiomeLibrary biomeLibrary, ISeasonalInformation seasonInfo, IWeatherSimulation weatherSimulation, Configuration.WeatherOptions weatherOptions, PrecipitationHandler precipitation) {
        this.logger = ModLog.createChild(logger, "HolisticFogRangeCalculator");
        this.invalidRanges = LogThrottle.oncePerKey(this.logger, "invalid fog ranges", "rejoining a world");
        this.fogOptions = fogOptions;

        this.calculators.add(new BiomeFogRangeCalculator(biomeLibrary, this.fogOptions));
        this.calculators.add(new MorningFogRangeCalculator(seasonInfo, this.fogOptions));
        this.weather = new WeatherFogRangeCalculator(this.fogOptions, weatherSimulation, seasonInfo, biomeLibrary, weatherOptions, precipitation);
        this.calculators.add(this.weather);
    }

    @Override
    @NotNull
    public String getName() {
        return "HolisticFogRangeCalculator";
    }

    @Override
    public boolean enabled() {
        return this.fogOptions.enableFogEffects;
    }

    @NotNull
    public FogRenderer.FogData render(@NotNull final FogRenderer.FogData data, float renderDistance, float partialTick) {

        if (!this.enabled())
            return data;

        // The thickest fog wins: its start and end are kept together, so one calculator's start isn't paired with
        // another's end, which made fog thicker than any of them asked for
        float start = data.start;
        float end = data.end;

        for (final IFogRangeCalculator calc : this.calculators) {
            if (calc.enabled()) {
                final FogRenderer.FogData result = calc.render(data, renderDistance, partialTick);
                if (!isValid(result)) {
                    this.invalidRanges.warn(calc.getName(), "Fog calculator '%s' reporting invalid fog range (start %f, end %f); ignored", calc.getName(), result.start, result.end);
                } else if (result.end < end) {
                    start = result.start;
                    end = result.end;
                }
            }
        }

        var result = new FogRenderer.FogData(data.mode);
        result.shape = data.shape;
        result.start = start;
        result.end = end;
        return result;
    }

    /**
     * Whether a fog range can be used. The start may be negative: vanilla starts the fog behind the camera under water
     * (-8), and calculators hand on the range they were given when they have nothing to change.
     */
    static boolean isValid(FogRenderer.FogData data) {
        return Float.isFinite(data.start) && Float.isFinite(data.end) && data.end >= 0 && data.start <= data.end;
    }

    @Override
    public void tick() {
        if (GameUtils.isInGame())
            this.calculators.forEach(IFogRangeCalculator::tick);
    }

    @Override
    public void disconnect() {
        this.invalidRanges.reset();
        this.calculators.forEach(IFogRangeCalculator::disconnect);
    }

    public Optional<String> getDisabledText() {
        if (!this.enabled()) {
            return Optional.of("(ALL DISABLED)");
        }

        var result = this.calculators.stream().filter(e -> !e.enabled()).map(IFogRangeCalculator::getName).collect(Collectors.joining(", "));

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of("(DISABLED: " + result + ")");
    }

    /**
     * Turns the fog's colour toward the dust's in a dust storm, if fog effects and the weather's fog are on
     *
     * @param rgb the fog's colour, red, green and blue from 0 to 1; changed in place
     */
    public void tintFogColor(float[] rgb, float partialTick) {
        if (this.enabled() && this.weather.enabled())
            this.weather.tintFogColor(rgb, partialTick);
    }
}
