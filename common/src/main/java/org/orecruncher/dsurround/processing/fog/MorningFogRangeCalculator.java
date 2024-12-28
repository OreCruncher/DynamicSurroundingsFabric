package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

public class MorningFogRangeCalculator extends VanillaFogRangeCalculator {

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
        final int day = this.clock.getDay();
        if (this.fogDay != day) {
            final int morningFogChance = this.fogOptions.morningFogChance; //Config.CLIENT.fog.morningFogChance.get();
            this.fogDay = day;
            final boolean doFog = this.isFogAllowed() && (morningFogChance == 100 || Randomizer.current().nextInt(100) <= morningFogChance);
            this.type = doFog ? getFogType() : FogDensity.NONE;
        }
    }

    private boolean isFogAllowed() {
        return GameUtils.getWorld().map(w ->w.dimensionType().natural()).orElse(false);
    }

    private float getCelestialAngleDegrees() {
        return GameUtils.getWorld().map(w -> w.getTimeOfDay(1F) * 360F).orElseThrow();
    }

    @NotNull
    protected FogDensity getFogType() {
        var clientLevel = GameUtils.getWorld().orElseThrow();
        if (this.seasonInfo.isSpring(clientLevel))
            return FogDensity.HEAVY;
        if (this.seasonInfo.isSummer(clientLevel))
            return FogDensity.NONE;
        if (this.seasonInfo.isAutumn(clientLevel))
            return FogDensity.MEDIUM;
        if (this.seasonInfo.isWinter(clientLevel))
            return FogDensity.LIGHT;
        return FogDensity.NORMAL;
    }
}