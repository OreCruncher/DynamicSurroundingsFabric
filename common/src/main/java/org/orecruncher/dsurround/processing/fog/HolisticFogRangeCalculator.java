package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.fog.FogData;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;

import java.util.Optional;
import java.util.stream.Collectors;

public class HolisticFogRangeCalculator implements IFogRangeCalculator {

    protected final IModLog logger;
    protected final Configuration.FogOptions fogOptions;
    protected final ObjectArray<IFogRangeCalculator> calculators = new ObjectArray<>(3);

    public HolisticFogRangeCalculator(IModLog logger, Configuration.FogOptions fogOptions) {
        this.logger = ModLog.createChild(logger, "HolisticFogRangeCalculator");
        this.fogOptions = fogOptions;
        this.calculators.add(ContainerManager.resolve(BiomeFogRangeCalculator.class));
        this.calculators.add(ContainerManager.resolve(MorningFogRangeCalculator.class));
        //this.calculators.add(ContainerManager.resolve(WeatherFogRangeCalculator.class));
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
    public FogData render(@NotNull final FogData data, float renderDistance, float partialTick) {

        if (!this.enabled())
            return data;

        float start = data.environmentalStart;
        float end = data.environmentalEnd;

        for (final IFogRangeCalculator calc : this.calculators) {
            if (calc.enabled()) {
                final FogData result = calc.render(data, renderDistance, partialTick);
                if (result.environmentalStart > result.environmentalEnd || result.environmentalStart < 0 || result.environmentalEnd < 0) {
                    this.logger.warn("Fog calculator '%s' reporting invalid fog range (start %f, end %f); ignored", calc.getName(), result.environmentalStart, result.environmentalEnd);
                } else {
                    start = Math.min(start, result.environmentalStart);
                    end = Math.min(end, result.environmentalEnd);
                }
            }
        }

        data.environmentalStart = start;
        data.environmentalEnd = end;
        return data;
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
