package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import org.orecruncher.dsurround.runtime.oracle.IMinecraftClock;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.weighted.WeightedList;

public class MorningFogRangeCalculator extends VanillaFogRangeCalculator {

    private static final WeightedList<FogDensity> SPRING_FOG = WeightedList.<FogDensity>builder()
            .add(FogDensity.NORMAL, 30)
            .add(FogDensity.MEDIUM, 20)
            .add(FogDensity.HEAVY, 10)
            .build();

    private static final WeightedList<FogDensity> SUMMER_FOG = WeightedList.<FogDensity>builder()
            .add(FogDensity.LIGHT, 20)
            .add(FogDensity.NONE, 10)
            .build();

    private static final WeightedList<FogDensity> AUTUMN_FOG = WeightedList.<FogDensity>builder()
            .add(FogDensity.NORMAL, 10)
            .add(FogDensity.MEDIUM, 20)
            .add(FogDensity.HEAVY, 10)
            .build();

    private static final WeightedList<FogDensity> WINTER_FOG = WeightedList.<FogDensity>builder()
            .add(FogDensity.LIGHT, 20)
            .add(FogDensity.NORMAL, 20)
            .add(FogDensity.MEDIUM, 10)
            .build();

    protected final ISeasonalInformation seasonInfo;
    protected final IMinecraftClock clock;
    protected final ILevelOracle levelOracle;
    protected int fogDay = -1;
    protected FogDensity type = FogDensity.NONE;

    public MorningFogRangeCalculator(ISeasonalInformation seasonInfo, IMinecraftClock clock, ILevelOracle levelOracle, Configuration.FogOptions fogOptions) {
        super("Morning", fogOptions);
        this.seasonInfo = seasonInfo;
        this.clock = clock;
        this.levelOracle = levelOracle;
    }

    @Override
    public boolean enabled() {
        return this.fogOptions.enableMorningFog;
    }

    @Override
    @NotNull
    public FogData render(@NotNull final FogData data, float renderDistance, float partialTick) {

        if (this.type != FogDensity.NONE) {
            var angle = this.getCelestialAngleDegrees();
            if (this.type.inRange(angle)) {
                final float mid = (this.type.getStartAngle() + this.type.getEndAngle()) / 2F;
                final float factor = (1F - Mth.abs(angle - mid) / (mid - this.type.getStartAngle())) * this.type.getIntensity();
                final float shift = data.environmentalStart * factor;
                final float newEnd = data.environmentalEnd - shift;
                final float newStart = Mth.clamp(data.environmentalStart - shift * 2, this.type.getReserve() + 1, newEnd);

                var result = new FogData();
                result.environmentalStart = newStart;
                result.environmentalEnd = newEnd;
                return result;
            }
        }
        return data;
    }

    @Override
    public void tick() {
        // Determine if fog is going to be done this Minecraft day
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
        return this.levelOracle.natural();
    }

    private float getCelestialAngleDegrees() {
        return this.levelOracle.currentCelestialAngle();
    }

    @NotNull
    protected FogDensity getFogType() {
        WeightedList<FogDensity> selections;
        if (this.seasonInfo.isSpring())
            selections = SPRING_FOG;
        else if (this.seasonInfo.isSummer())
            selections = SUMMER_FOG;
        else if (this.seasonInfo.isAutumn())
            selections = AUTUMN_FOG;
        else if (this.seasonInfo.isWinter())
            selections = WINTER_FOG;
        else
            // Shouldn't get here, but...
            return FogDensity.NONE;

        return selections.getRandomValue(Randomizer.current()).orElseThrow();
    }
}