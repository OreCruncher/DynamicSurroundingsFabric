package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
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
        return averageOverCells(pos, range, (qx, qy, qz) -> {
            final Biome b = level.getNoiseBiome(qx, qy, qz).value();
            final BiomeInfo info = this.biomeLibrary.getBiomeInfo(b);
            return info.getFogDensity().getIntensity();
        });
    }

    /**
     * A value of the biome cell (4 blocks each way) at quart position (qx, qy, qz)
     */
    @FunctionalInterface
    interface CellValue {
        float at(int qx, int qy, int qz);
    }

    /**
     * The average over each block within {@code range} of {@code pos} each way of its biome cell's value. Biomes are
     * stored a cell at a time, so each cell is read once and counted by how many of the blocks are in it: a few dozen
     * reads rather than one a block, thousands. Vanilla's per-block blending of biomes at their borders is left out,
     * which an average over this many blocks wouldn't show.
     */
    static float averageOverCells(BlockPos pos, int range, CellValue cells) {
        float sum = 0F;
        int fromQx = QuartPos.fromBlock(pos.getX() - range), toQx = QuartPos.fromBlock(pos.getX() + range);
        int fromQy = QuartPos.fromBlock(pos.getY() - range), toQy = QuartPos.fromBlock(pos.getY() + range);
        int fromQz = QuartPos.fromBlock(pos.getZ() - range), toQz = QuartPos.fromBlock(pos.getZ() + range);
        for (int qy = fromQy; qy <= toQy; qy++) {
            int wy = overlap(qy, pos.getY(), range);
            for (int qz = fromQz; qz <= toQz; qz++) {
                int wz = overlap(qz, pos.getZ(), range);
                for (int qx = fromQx; qx <= toQx; qx++)
                    sum += cells.at(qx, qy, qz) * (overlap(qx, pos.getX(), range) * wy * wz);
            }
        }
        int side = 2 * range + 1;
        return sum / (side * side * side);
    }

    // How many blocks of the cell q, along one axis, are within range of centre
    private static int overlap(int q, int centre, int range) {
        int from = Math.max(QuartPos.toBlock(q), centre - range);
        int to = Math.min(QuartPos.toBlock(q) + 3, centre + range);
        return to - from + 1;
    }
}
