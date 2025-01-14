package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.random.SimpleWeightedRandomList;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

public class MorningFogRangeCalculator extends VanillaFogRangeCalculator {

    private static final SimpleWeightedRandomList<FogDensity> SPRING_FOG = new SimpleWeightedRandomList.Builder<FogDensity>()
            .add(FogDensity.NORMAL, 30)
            .add(FogDensity.MEDIUM, 20)
            .add(FogDensity.HEAVY, 10)
            .build();

    private static final SimpleWeightedRandomList<FogDensity> SUMMER_FOG = new SimpleWeightedRandomList.Builder<FogDensity>()
            .add(FogDensity.LIGHT, 20)
            .add(FogDensity.NONE, 10)
            .build();

    private static final SimpleWeightedRandomList<FogDensity> AUTUMN_FOG = new SimpleWeightedRandomList.Builder<FogDensity>()
            .add(FogDensity.NORMAL, 10)
            .add(FogDensity.MEDIUM, 20)
            .add(FogDensity.HEAVY, 10)
            .build();

    private static final SimpleWeightedRandomList<FogDensity> WINTER_FOG = new SimpleWeightedRandomList.Builder<FogDensity>()
            .add(FogDensity.LIGHT, 20)
            .add(FogDensity.NORMAL, 20)
            .add(FogDensity.MEDIUM, 10)
            .build();

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
        SimpleWeightedRandomList<FogDensity> selections;
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