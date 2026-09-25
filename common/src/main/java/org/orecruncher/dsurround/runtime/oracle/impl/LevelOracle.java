package org.orecruncher.dsurround.runtime.oracle.impl;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.CachingSupplier;
import org.orecruncher.dsurround.lib.DayCycle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.compat.LevelCompat;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.runtime.oracle.IDimensionOracle;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;

public final class LevelOracle implements ILevelOracle {

    private final IDimensionOracle dimensionOracle;
    private final CachingSupplier<ClientLevel> level;

    public LevelOracle(final IDimensionOracle dimensionOracle) {
        this.dimensionOracle = dimensionOracle;
        this.level = CachingSupplier.from(() -> GameUtils.getMC().level);

        // Hook the level load event so that we can clear cached information
        ClientState.CLIENT_LEVEL_LOAD_EVENT.register(_ -> this.level.clear(), HandlerPriority.HIGH);
        AssetLibraryEvent.RELOAD.register((_, _) -> this.level.clear(), HandlerPriority.HIGH);
    }

    @Override
    public ClientLevel level() {
        return this.level.get();
    }

    @Override
    public long worldTime() {
        return this.level.get().getGameTime();
    }

    @Override
    public int seaLevel() {
        return this.dimensionOracle.seaLevel();
    }

    @Override
    public Holder<Biome> biomeHolder(BlockPos blockPos) {
        return this.level.get().getBiome(blockPos);
    }

    @Override
    public Biome.Precipitation precipitationAt(BlockPos pos) {
        return this.biome(pos).getPrecipitationAt(pos, this.seaLevel());
    }

    @Override
    public float temperatureAt(BlockPos pos) {
        return this.biome(pos).getTemperature(pos, this.seaLevel());
    }

    @Override
    public float getRainLevel() {
        return this.level.get().getRainLevel(1F);
    }

    @Override
    public float getThunderLevel() {
        return this.level.get().getThunderLevel(1F);
    }

    @Override
    public boolean isRaining() {
        return this.level.get().isRaining();
    }

    @Override
    public boolean isThundering() {
        return this.level.get().isThundering();
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return this.level.get().canSeeSky(pos);
    }

    @Override
    public BlockPos getTopSolidOrLiquidBlock(BlockPos pos) {
        return LevelCompat.getTopSolidOrLiquidBlock(this.level.get(), pos);
    }

    @Override
    public boolean natural() {
        return this.dimensionOracle.natural();
    }

    @Override
    public boolean isSuperFlat() {
        return this.dimensionOracle.isSuperFlat();
    }

    @Override
    public String dimensionName() {
        return this.dimensionIdentifier().getPath();
    }

    @Override
    public Identifier dimensionIdentifier() {
        return this.dimensionOracle.name();
    }

    @Override
    public boolean hasSkyLight() {
        return this.level.get().dimensionType().hasSkyLight();
    }

    @Override
    public DayCycle currentDiurnalState() {
        return DayCycle.getCycle(this.level.get());
    }

    @Override
    public float currentMoonSize() {
        return DayCycle.getMoonSize(this.level.get());
    }

    @Override
    public float currentCelestialAngle() {
        return DayCycle.getCelestialAngle(this.level.get());
    }
}
