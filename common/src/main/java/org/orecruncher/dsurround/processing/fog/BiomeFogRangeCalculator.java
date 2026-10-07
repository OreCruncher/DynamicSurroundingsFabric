package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.GameUtils;

public class BiomeFogRangeCalculator extends VanillaFogRangeCalculator {

    // Per tick; a full fade takes about 8 seconds
    private static final float SCALE_ADJUST = 0.006F;

    private final IBiomeLibrary biomeLibrary;

    private final ScaleTransition scale = new ScaleTransition(SCALE_ADJUST);
    private BlockPos lastBlockPos;

    public BiomeFogRangeCalculator(IBiomeLibrary biomeLibrary, Configuration.FogOptions fogOptions) {
        super("Biome", fogOptions);
        this.biomeLibrary = biomeLibrary;
        this.lastBlockPos = BlockPos.ZERO;
    }

    @Override
    public boolean enabled() {
        return this.fogOptions.enableBiomeFog;
    }

    @Override
    @NotNull
    public FogRenderer.FogData render(@NotNull final FogRenderer.FogData data, float renderDistance, float partialTick) {
        return thicken(data, this.scale.get(partialTick), 0F);
    }

    @Override
    public void tick() {
        // Only need to sample if the player moves position
        var currentPosition = GameUtils.getPlayer().map(Entity::getOnPos).orElseThrow();
        if (!this.lastBlockPos.equals(currentPosition)) {
            this.lastBlockPos = currentPosition;
            this.scale.setTarget(this.sampleArea(currentPosition, 6));
        }
        this.scale.tick();
    }

    @Override
    public void disconnect() {
        this.scale.reset();
        this.lastBlockPos = BlockPos.ZERO;
    }

    private float sampleArea(BlockPos pos, int range) {
        var level = GameUtils.getWorld().orElseThrow();
        var iterator = BlockPos.withinManhattan(pos, range, range, range).iterator();
        float intensityAccum = 0F;
        float intensityCount = 0;
        while(iterator.hasNext()) {
            var p = iterator.next();
            final Biome b = level.getBiome(p).value();
            final BiomeInfo info = this.biomeLibrary.getBiomeInfo(b);
            intensityAccum += info.getFogDensity().getIntensity();
            intensityCount++;
        }

        return intensityAccum / intensityCount;
    }
}
