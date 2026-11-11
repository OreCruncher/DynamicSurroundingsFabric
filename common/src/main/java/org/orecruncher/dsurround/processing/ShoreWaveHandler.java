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
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.tags.FluidTags;

import java.util.Arrays;

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
    // How many ticks early or late a wave can reach a stretch of shore, by the long and short ripples along it, and how
    // many blocks a ripple is across: two sizes, neither a whole number of the other, so the pattern doesn't show
    static final float LONG_RIPPLE_TICKS = 12F;
    static final float SHORT_RIPPLE_TICKS = 5F;
    private static final float LONG_RIPPLE_X = Mth.TWO_PI / 23F;
    private static final float LONG_RIPPLE_Z = Mth.TWO_PI / 31F;
    private static final float SHORT_RIPPLE_X = Mth.TWO_PI / 11F;
    private static final float SHORT_RIPPLE_Z = Mth.TWO_PI / 13F;
    // How far from the player, in blocks
    static final int RANGE = 32;
    private static final int SIDE = RANGE * 2 + 1;
    // How far out the water has to reach, in blocks, looked at every few
    static final int OPEN_WATER = 14;
    private static final int OPEN_WATER_STEP = 3;
    // How far either way along the shore a wave reaches, and how close its patches are, in blocks, calm and in a full
    // storm: closer, as a storm's waves are to look thicker, not the same foam spread further up the shore
    private static final int WAVE_HALF_WIDTH = 1;
    private static final double CALM_PATCH_SPACING = 0.5D;
    private static final double STORM_PATCH_SPACING = 0.2D;
    // How much bigger a full storm's patches are
    private static final float STORM_PATCH_SCALE = 1.3F;
    // The share of patches that run the wave's full reach; the rest stay at the water's edge, thickening the foam there
    static final float STRAY_SHARE = 0.2F;
    // How far land can be from the water's height and still be its shore
    private static final int SHORE_RISE = 1;
    private static final int WAVE_TICKS = 70;
    // How far out on the water foam starts from the shore, in blocks, more or less, to roll in
    private static final double START_OUT = 1.5D;
    private static final double START_OUT_MORE = 1D;

    private final IWeatherSimulation weather;
    private final ITagLibrary tags;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    // Each column's pulse delay, by slot, with the column it is for (see delay)
    private final int[] delays = new int[SIDE * SIDE];
    private final int[] delayX = new int[SIDE * SIDE];
    private final int[] delayZ = new int[SIDE * SIDE];

    public ShoreWaveHandler(IWeatherSimulation weather, ITagLibrary tags, Configuration config, IModLog logger) {
        super("Shore Waves", config, logger);
        this.weather = weather;
        this.tags = tags;
        // No column is at MIN_VALUE, so every slot is worked out the first time it is asked for
        Arrays.fill(this.delayX, Integer.MIN_VALUE);
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
                if (pulses(tick, this.delay(x, z), period))
                    this.tryWave(level, x, z, storm);
        }
    }

    /**
     * A column's {@link #pulseDelay}, kept: it never changes, and every column in range is asked every tick. Slots
     * wrap with the columns, so as the player moves only those coming into range are worked out.
     */
    private int delay(int x, int z) {
        int i = Math.floorMod(x, SIDE) * SIDE + Math.floorMod(z, SIDE);
        if (this.delayX[i] != x || this.delayZ[i] != z) {
            this.delayX[i] = x;
            this.delayZ[i] = z;
            this.delays[i] = pulseDelay(x, z);
        }
        return this.delays[i];
    }

    private void tryWave(ClientLevel level, int x, int z, float storm) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        if (!this.isOpenWater(level, x, y, z))
            return;
        for (var toLand : Direction.Plane.HORIZONTAL) {
            if (!this.isLand(level, x + toLand.getStepX(), y, z + toLand.getStepZ()) || !this.opensOut(level, x, y, z, toLand.getOpposite()))
                continue;
            double surfaceY = y + level.getFluidState(this.probe.set(x, y, z)).getHeight(level, this.probe);
            double spacing = patchSpacing(storm);
            float scale = patchScale(storm);
            double alongX = -toLand.getStepZ(), alongZ = toLand.getStepX();
            for (double along = -WAVE_HALF_WIDTH; along <= WAVE_HALF_WIDTH; along += spacing) {
                double px = x + 0.5D + alongX * along + toLand.getStepX() * 0.3D + (RANDOM.nextDouble() - 0.5D) * 0.3D;
                double pz = z + 0.5D + alongZ * along + toLand.getStepZ() * 0.3D + (RANDOM.nextDouble() - 0.5D) * 0.3D;
                // Each on a shore of its own: the coast bends, and a straight line runs off a point out over the water
                int blockX = Mth.floor(px), blockZ = Mth.floor(pz);
                if (!this.isOpenWater(level, blockX, y, blockZ) || !this.isLand(level, blockX + toLand.getStepX(), y, blockZ + toLand.getStepZ()))
                    continue;
                double out = START_OUT + RANDOM.nextDouble() * START_OUT_MORE;
                float reach = patchReach(storm, RANDOM.nextFloat());
                GameUtils.getParticleManager().add(ShoreFoam.create(level, px - toLand.getStepX() * out, surfaceY, pz - toLand.getStepZ() * out,
                        toLand.getStepX(), toLand.getStepZ(), out + reach * (0.8D + RANDOM.nextDouble() * 0.4D), WAVE_TICKS + RANDOM.nextInt(20), scale));
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
        return pulses(tick, pulseDelay(x, z), period);
    }

    private static boolean pulses(long tick, int delay, int period) {
        return Math.floorMod(tick + delay, period) == 0;
    }

    /**
     * Where a column falls in the pulse: a sweep along the coast, rippled so that a long straight shore, which the sweep
     * alone reaches all at once if it runs across it, doesn't take the wave in one line. Fixed for a column, so each
     * takes one wave a period.
     */
    static int pulseDelay(int x, int z) {
        float ripple = LONG_RIPPLE_TICKS * Mth.sin(x * LONG_RIPPLE_X + z * LONG_RIPPLE_Z)
                + SHORT_RIPPLE_TICKS * Mth.sin(x * SHORT_RIPPLE_X - z * SHORT_RIPPLE_Z);
        return Math.floorDiv(x + z, PULSE_BLOCKS) + Math.round(ripple);
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

    /**
     * How far a patch runs: a few the wave's full reach, the others only as far as a calm day's
     *
     * @param roll 0 to 1, at random
     */
    static float patchReach(float storm, float roll) {
        return waveReach(roll < STRAY_SHARE ? storm : 0F);
    }

    /**
     * How close a wave's patches are along the shore, in blocks
     */
    static double patchSpacing(float storm) {
        return Mth.lerp(Mth.clamp(storm, 0F, 1F), CALM_PATCH_SPACING, STORM_PATCH_SPACING);
    }

    static float patchScale(float storm) {
        return Mth.lerp(Mth.clamp(storm, 0F, 1F), 1F, STORM_PATCH_SCALE);
    }
}
