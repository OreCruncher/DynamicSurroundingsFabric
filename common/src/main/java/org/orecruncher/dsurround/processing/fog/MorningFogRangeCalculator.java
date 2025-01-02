package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

import java.util.List;

public class MorningFogRangeCalculator extends VanillaFogRangeCalculator {

    private static final List<WeightedEntry.Wrapper<FogDensity>> SPRING_FOG = List.of(
            WeightedEntry.wrap(FogDensity.NORMAL, 30),
            WeightedEntry.wrap(FogDensity.MEDIUM, 20),
            WeightedEntry.wrap(FogDensity.HEAVY, 10));

    private static final List<WeightedEntry.Wrapper<FogDensity>> SUMMER_FOG = List.of(
            WeightedEntry.wrap(FogDensity.LIGHT, 20),
            WeightedEntry.wrap(FogDensity.NONE, 10));

    private static final List<WeightedEntry.Wrapper<FogDensity>> AUTUMN_FOG = List.of(
            WeightedEntry.wrap(FogDensity.NORMAL, 10),
            WeightedEntry.wrap(FogDensity.MEDIUM, 20),
            WeightedEntry.wrap(FogDensity.HEAVY, 10));

    private static final List<WeightedEntry.Wrapper<FogDensity>> WINTER_FOG = List.of(
            WeightedEntry.wrap(FogDensity.LIGHT, 20),
            WeightedEntry.wrap(FogDensity.NORMAL, 20),
            WeightedEntry.wrap(FogDensity.MEDIUM, 10));

    protected final ISeasonalInformation seasonInfo;
    protected final MinecraftClock clock;
    protected int fogDay = -1;
    protected FogDensity type = FogDensity.NONE;

    public MorningFogRangeCalculator(ISeasonalInformation seasonInfo, Configuration.FogOptions fogOptions) {
        super("Morning", fogOptions);
        this.seasonInfo = seasonInfo;
        this.clock = new MinecraftClock();
    }

    @Override
    public boolean enabled() {
        return this.fogOptions.enableMorningFog;
    }

    @Override
    @NotNull
    public FogRenderer.FogData render(@NotNull final FogRenderer.FogData data, float renderDistance, float partialTick) {

        if (this.type != FogDensity.NONE) {
            var angle = this.getCelestialAngleDegrees();
            if (this.type.inRange(angle)) {
                final float mid = (this.type.getStartAngle() + this.type.getEndAngle()) / 2F;
                final float factor = (1F - Mth.abs(angle - mid) / (mid - this.type.getStartAngle())) * this.type.getIntensity();
                final float shift = data.start * factor;
                final float newEnd = data.end - shift;
                final float newStart = Mth.clamp(data.start - shift * 2, this.type.getReserve() + 1, newEnd);

                var result = new FogRenderer.FogData(data.mode);
                result.start = newStart;
                result.end = newEnd;
                return result;
            }
        }
        return data;
    }

    @Override
    public void tick() {
        // Determine if fog is going to be done this Minecraft day
        GameUtils.getWorld().ifPresent(this.clock::update);
        final int day = this.clock.getDay();
        if (this.fogDay != day) {
            this.fogDay = day;
            this.type = this.isFogAllowed() ? getFogType() : FogDensity.NONE;
        }
    }

    @Override
    public void disconnect() {
        this.fogDay = -1;
        this.type = FogDensity.NONE;
    }

    private boolean isFogAllowed() {
        return GameUtils.getWorld().map(w -> w.dimensionType().natural()).orElse(false);
    }

    private float getCelestialAngleDegrees() {
        return GameUtils.getWorld().map(w -> w.getTimeOfDay(1F) * 360F).orElseThrow();
    }

    @NotNull
    protected FogDensity getFogType() {
        List<WeightedEntry.Wrapper<FogDensity>> selections;
        var clientLevel = GameUtils.getWorld().orElseThrow();
        if (this.seasonInfo.isSpring(clientLevel))
            selections = SPRING_FOG;
        else if (this.seasonInfo.isSummer(clientLevel))
            selections = SUMMER_FOG;
        else if (this.seasonInfo.isAutumn(clientLevel))
            selections = AUTUMN_FOG;
        else if (this.seasonInfo.isWinter(clientLevel))
            selections = WINTER_FOG;
        else
            // Shouldn't get here, but...
            return FogDensity.NONE;

        var totalWeight = WeightedRandom.getTotalWeight(selections);
        var targetWeight = Randomizer.current().nextInt(totalWeight);
        return WeightedRandom.getWeightedItem(selections, targetWeight).map(WeightedEntry.Wrapper::data).orElseThrow();
    }
}