package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.WeightTable;

import java.util.List;

public class MorningFogRangeCalculator extends VanillaFogRangeCalculator {

    private record FogDensityEntry(int weight, FogDensity density) implements WeightTable.IItem<FogDensity> {
        @Override
        public int getWeight() {
            return this.weight();
        }
        @Override
        public FogDensity getItem() {
            return this.density();
        }
    }

    private static final FogDensityEntry[] SPRING_FOG = {
            new FogDensityEntry(30, FogDensity.NORMAL),
            new FogDensityEntry(20, FogDensity.MEDIUM),
            new FogDensityEntry(10, FogDensity.HEAVY)
    };

    private static final FogDensityEntry[] SUMMER_FOG = {
            new FogDensityEntry(20, FogDensity.LIGHT),
            new FogDensityEntry(10, FogDensity.NONE)
    };

    private static final FogDensityEntry[] AUTUMN_FOG = {
            new FogDensityEntry(10, FogDensity.NORMAL),
            new FogDensityEntry(20, FogDensity.MEDIUM),
            new FogDensityEntry(10, FogDensity.HEAVY)
    };

    private static final FogDensityEntry[] WINTER_FOG = {
            new FogDensityEntry(20, FogDensity.LIGHT),
            new FogDensityEntry(20, FogDensity.NORMAL),
            new FogDensityEntry(10, FogDensity.MEDIUM)
    };

    protected final ISeasonalInformation seasonInfo;
    protected final MinecraftClock clock;
    protected int fogDay = -1;
    protected FogDensity type = FogDensity.NONE;

    public MorningFogRangeCalculator(ISeasonalInformation seasonInfo, Configuration.FogOptions fogOptions) {
        super("MorningFogRangeCalculator", fogOptions);
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
        FogDensityEntry[] selections;
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

        return WeightTable.makeSelection(List.of(selections)).orElseThrow();
    }
}