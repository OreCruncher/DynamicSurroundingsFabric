package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

import java.util.Optional;
import java.util.stream.Collectors;

public class HolisticFogRangeCalculator implements IFogRangeCalculator {

    protected final IModLog logger;
    protected final Configuration.FogOptions fogOptions;
    protected final ObjectArray<IFogRangeCalculator> calculators = new ObjectArray<>(3);

    public HolisticFogRangeCalculator(IModLog logger, Configuration.FogOptions fogOptions) {
        this.logger = ModLog.createChild(logger, "HolisticFogRangeCalculator");
        this.fogOptions = fogOptions;

        var biomeLibrary = ContainerManager.resolve(IBiomeLibrary.class);
        var seasonInfo = ContainerManager.resolve(ISeasonalInformation.class);

        this.calculators.add(new BiomeFogRangeCalculator(biomeLibrary, this.fogOptions));
        this.calculators.add(new MorningFogRangeCalculator(seasonInfo, this.fogOptions));
        this.calculators.add(new WeatherFogRangeCalculator(this.fogOptions));
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

        float start = data.start;
        float end = data.end;

        for (final IFogRangeCalculator calc : this.calculators) {
            if (calc.enabled()) {
                final FogRenderer.FogData result = calc.render(data, renderDistance, partialTick);
                if (result.start > result.end || result.start < 0 || result.end < 0) {
                    this.logger.warn("Fog calculator '%s' reporting invalid fog range (start %f, end %f); ignored", calc.getName(), result.start, result.end);
                } else {
                    start = Math.min(start, result.start);
                    end = Math.min(end, result.end);
                }
            }
        }

        var result = new FogRenderer.FogData(data.mode);
        result.shape = data.shape;
        result.start = start;
        result.end = end;
        return result;
    }

    @Override
    public void tick() {
        if (GameUtils.isInGame())
            this.calculators.forEach(IFogRangeCalculator::tick);
    }

    @Override
    public void disconnect() {
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
}
