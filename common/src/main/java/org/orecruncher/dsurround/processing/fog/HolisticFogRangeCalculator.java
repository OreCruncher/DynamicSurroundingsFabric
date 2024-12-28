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
        return true;
    }

    @NotNull
    public FogRenderer.FogData render(@NotNull final FogRenderer.FogData data, float renderDistance, float partialTick) {

        if (!this.fogOptions.enableFogEffects)
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
}
