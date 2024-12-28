package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.lib.GameUtils;

public class BiomeFogRangeCalculator extends VanillaFogRangeCalculator {

    private static final float SCALE_ADJUST = 0.002F;

    private final IBiomeLibrary biomeLibrary;

    private BlockPos lastBlockPos;
    private float activeScale;
    private float targetScale;

    public BiomeFogRangeCalculator(IBiomeLibrary biomeLibrary, Configuration.FogOptions fogOptions) {
        super("BiomeFogRangeCalculator", fogOptions);
        this.biomeLibrary = biomeLibrary;
        this.activeScale = this.targetScale = 0F;
        this.lastBlockPos = BlockPos.ZERO;
    }

    @Override
    public boolean enabled() {
        return this.fogOptions.enableBiomeFog;
    }

    @Override
    @NotNull
    public FogRenderer.FogData render(@NotNull final FogRenderer.FogData data, float renderDistance, float partialTick) {

        // Adjust the scale in the right direction
        if (Float.compare(this.activeScale, this.targetScale) != 0) {
            if (this.targetScale < this.activeScale) {
                this.activeScale -= SCALE_ADJUST;
                if (this.activeScale < this.targetScale)
                    this.activeScale = this.targetScale;
            } else if(this.targetScale > this.activeScale) {
                this.activeScale += SCALE_ADJUST;
                if (this.activeScale > this.targetScale)
                    this.activeScale = this.targetScale;
            }
        }

        if (Float.compare(this.activeScale, 0F) == 0)
            return data;

        var scale = 1F - this.activeScale;
        var result = new FogRenderer.FogData(data.mode);
        result.end = data.end * scale;
        result.start = data.start * scale * scale;
        return result;
    }

    @Override
    public void tick() {
        // Only need to sample if the player moves position
        var currentPosition = GameUtils.getPlayer().map(Entity::getOnPos).orElseThrow();
        if (this.lastBlockPos.equals(currentPosition))
            return;
        this.lastBlockPos = currentPosition;
        this.targetScale = this.sampleArea(currentPosition, 6);
    }

    @Override
    public void disconnect() {
        this.activeScale = this.targetScale = 0F;
        this.lastBlockPos = BlockPos.ZERO;
    }

    private float sampleArea(BlockPos pos, int range) {
        final BiomeManager biomeManager = GameUtils.getWorld().map(Level::getBiomeManager).orElseThrow();
        var iterator =BlockPos.withinManhattan(pos, range, range, range).iterator();
        float intensityAccum = 0F;
        float intensityCount = 0;
        while(iterator.hasNext()) {
            var p = iterator.next();
            final Biome b = biomeManager.getNoiseBiomeAtPosition(p).value();
            final BiomeInfo info = this.biomeLibrary.getBiomeInfo(b);
            intensityAccum += info.getFogDensity().getIntensity();
            intensityCount++;
        }

        return intensityAccum / intensityCount;
    }
}
