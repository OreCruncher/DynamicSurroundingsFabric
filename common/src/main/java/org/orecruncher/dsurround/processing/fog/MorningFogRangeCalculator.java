package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.mixinutils.IClientWorld;

public class MorningFogRangeCalculator extends VanillaFogRangeCalculator {

    private static final FogDensity[] SPRING_FOG = {FogDensity.MEDIUM, FogDensity.HEAVY, FogDensity.NORMAL, FogDensity.NORMAL};
    private static final FogDensity[] SUMMER_FOG = {FogDensity.LIGHT, FogDensity.NONE, FogDensity.LIGHT, FogDensity.NONE};
    private static final FogDensity[] AUTUMN_FOG = {FogDensity.NORMAL, FogDensity.MEDIUM, FogDensity.HEAVY, FogDensity.MEDIUM};
    private static final FogDensity[] WINTER_FOG = {FogDensity.MEDIUM, FogDensity.LIGHT, FogDensity.NORMAL, FogDensity.NORMAL};

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
            this.type = this.isFogAllowed() ? getFogType(this.fogDay) : FogDensity.NONE;
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
    protected FogDensity getFogType(int day) {
        var clientLevel = GameUtils.getWorld().orElseThrow();
        var idx = this.generateIndex(clientLevel, day);
        if (this.seasonInfo.isSpring(clientLevel))
            return SPRING_FOG[idx];
        if (this.seasonInfo.isSummer(clientLevel))
            return SUMMER_FOG[idx];
        if (this.seasonInfo.isAutumn(clientLevel))
            return AUTUMN_FOG[idx];
        if (this.seasonInfo.isWinter(clientLevel))
            return WINTER_FOG[idx];
        // Shouldn't get here, but...
        return FogDensity.NORMAL;
    }

    private int generateIndex(ClientLevel clientLevel, int day) {
        // Generate an index using the world seed and day rather than
        // a random.
        var worldSeed = ((IClientWorld) clientLevel).dsurround_getWorldSeed();
        return (int) ((worldSeed + day * 134775813) & 3);
    }
}