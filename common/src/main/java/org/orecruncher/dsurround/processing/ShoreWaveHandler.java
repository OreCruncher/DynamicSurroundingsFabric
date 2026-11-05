// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.particles.ShoreFoam;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.weather.WeatherSimulation;
import org.orecruncher.dsurround.tags.FluidTags;

/**
 * Waves lapping on beaches: still water at the edge of land, with open water behind it, so ponds and rivers have none;
 * a wave is a line of {@link ShoreFoam} along the shore, up onto the land and back. They come regularly, one a pulse for
 * every column of shore, from the game's clock: the pulse crosses the player's surroundings in a couple of seconds, so a
 * stretch of beach comes in nearly at once, with a slight ripple through it. Small ones always, bigger and closer
 * together as a storm builds.
 */
public class ShoreWaveHandler extends AbstractClientHandler {

    // The ticks between pulses, calm and in a full storm, and how many blocks across a tick of the pulse's travel is
    static final int CALM_PULSE_TICKS = 120;
    static final int STORM_PULSE_TICKS = 80;
    private static final int PULSE_BLOCKS = 2;
    // How far from the player, in blocks
    static final int RANGE = 32;
    // How far out the water has to reach, in blocks, looked at every few
    static final int OPEN_WATER = 14;
    private static final int OPEN_WATER_STEP = 3;
    // How far either way along the shore a wave reaches, and how close its patches are, in blocks
    private static final int WAVE_HALF_WIDTH = 1;
    private static final double PATCH_SPACING = 0.5D;
    // How far land can be from the water's height and still be its shore
    private static final int SHORE_RISE = 1;
    private static final int WAVE_TICKS = 70;
    // How far out on the water foam starts from the shore, in blocks, more or less, to roll in
    private static final double START_OUT = 1.5D;
    private static final double START_OUT_MORE = 1D;

    private final WeatherSimulation weather;
    private final ITagLibrary tags;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    public ShoreWaveHandler(WeatherSimulation weather, ITagLibrary tags, Configuration config, IModLog logger) {
        super("Shore Waves", config, logger);
        this.weather = weather;
        this.tags = tags;
    }

    @Override
    public void process(final Player player) {
        if (!this.config.blockEffects.enableBeachWaves)
            return;
        var particles = GameUtils.getGameSettings().particles().get();
        if (particles == ParticleStatus.MINIMAL || !(player.level() instanceof ClientLevel level))
            return;

        float storm = this.weather.rainLevel(level.getRainLevel(1F));
        int period = pulseTicks(storm);
        long tick = level.getGameTime();
        boolean every = particles == ParticleStatus.ALL;
        for (int x = player.getBlockX() - RANGE; x <= player.getBlockX() + RANGE; x++) {
            // Fewer particles: half the columns
            if (!every && (x & 1) != 0)
                continue;
            for (int z = player.getBlockZ() - RANGE; z <= player.getBlockZ() + RANGE; z++)
                if (pulseHere(tick, x, z, period))
                    this.tryWave(level, x, z, storm);
        }
    }

    private void tryWave(ClientLevel level, int x, int z, float storm) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        if (!this.isOpenWater(level, x, y, z))
            return;
        for (var toLand : Direction.Plane.HORIZONTAL) {
            if (!this.isLand(level, x + toLand.getStepX(), y, z + toLand.getStepZ()) || !this.opensOut(level, x, y, z, toLand.getOpposite()))
                continue;
            double surfaceY = y + level.getFluidState(this.probe.set(x, y, z)).getHeight(level, this.probe);
            float reach = waveReach(storm);
            double alongX = -toLand.getStepZ(), alongZ = toLand.getStepX();
            for (double along = -WAVE_HALF_WIDTH; along <= WAVE_HALF_WIDTH; along += PATCH_SPACING) {
                double px = x + 0.5D + alongX * along + toLand.getStepX() * 0.3D + (RANDOM.nextDouble() - 0.5D) * 0.3D;
                double pz = z + 0.5D + alongZ * along + toLand.getStepZ() * 0.3D + (RANDOM.nextDouble() - 0.5D) * 0.3D;
                // Each on a shore of its own: the coast bends, and a straight line runs off a point out over the water
                int blockX = Mth.floor(px), blockZ = Mth.floor(pz);
                if (!this.isOpenWater(level, blockX, y, blockZ) || !this.isLand(level, blockX + toLand.getStepX(), y, blockZ + toLand.getStepZ()))
                    continue;
                double out = START_OUT + RANDOM.nextDouble() * START_OUT_MORE;
                GameUtils.getParticleManager().add(ShoreFoam.create(level, px - toLand.getStepX() * out, surfaceY, pz - toLand.getStepZ() * out,
                        toLand.getStepX(), toLand.getStepZ(), out + reach * (0.8D + RANDOM.nextDouble() * 0.4D), WAVE_TICKS + RANDOM.nextInt(20)));
            }
            return;
        }
    }

    private boolean isOpenWater(Level level, int x, int y, int z) {
        var fluid = level.getFluidState(this.probe.set(x, y, z));
        return fluid.isSource() && this.tags.is(FluidTags.WATER_RIPPLES, fluid);
    }

    private boolean isLand(Level level, int x, int y, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        return Math.abs(top - y) <= SHORE_RISE && level.getFluidState(this.probe.set(x, top, z)).isEmpty();
    }

    /**
     * Whether open water reaches out from here the way given
     */
    private boolean opensOut(Level level, int x, int y, int z, Direction out) {
        for (int i = OPEN_WATER_STEP; i <= OPEN_WATER; i += OPEN_WATER_STEP) {
            int cx = x + out.getStepX() * i, cz = z + out.getStepZ() * i;
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz) - 1 != y || !this.isOpenWater(level, cx, y, cz))
                return false;
        }
        return true;
    }

    /**
     * Whether the pulse reaches a column this tick: once a period for each
     *
     * @param period the ticks between pulses
     */
    static boolean pulseHere(long tick, int x, int z, int period) {
        return Math.floorMod(tick + Math.floorDiv(x + z, PULSE_BLOCKS), period) == 0;
    }

    /**
     * The ticks between pulses: closer together in a storm
     */
    static int pulseTicks(float storm) {
        return (int) Mth.lerp(Mth.clamp(storm, 0F, 1F), CALM_PULSE_TICKS, STORM_PULSE_TICKS);
    }

    /**
     * How far up the shore a wave reaches, in blocks: more in a storm
     */
    static float waveReach(float storm) {
        return Mth.lerp(Mth.clamp(storm, 0F, 1F), 1F, 2.5F);
    }
}
