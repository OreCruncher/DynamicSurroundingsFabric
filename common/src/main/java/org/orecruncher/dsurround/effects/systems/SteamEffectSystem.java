package org.orecruncher.dsurround.effects.systems;

import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.effects.blocks.AbstractParticleEmitterEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.tags.BlockEffectTags;

import java.util.Collection;
import java.util.List;

import static org.orecruncher.dsurround.effects.BlockEffectUtils.*;

/**
 * Steam rising from water next to a heat source.
 * <p>
 * Water is everywhere (oceans, rivers, lakes) while heat sources are rare, so when blocks come into range steam is
 * found from the heat side: each hot block looks for water around it. Water blocks themselves cost only the hot
 * source check. Block updates check the changed block directly as well, since a change can create steam where no
 * hot block is rescanned (a block above the water mined while the heat is diagonally below it).
 */
public class SteamEffectSystem extends AbstractEffectSystem implements IEffectSystem {

    // Reused for walking the neighbors of a hot block
    private final BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();

    public SteamEffectSystem(IModLog logger, Configuration config) {
        super(logger, config,"Steam");
    }

    @Override
    public boolean isEnabled() {
        return this.config.blockEffects.steamColumnEnabled;
    }

    /**
     * A block came into range. Only hot blocks do anything: they add steam to any qualifying water around them.
     * Steam that stops qualifying is removed by the effect's own periodic check.
     */
    @Override
    public void blockScan(Level world, BlockState state, BlockPos pos) {
        if (IS_HOT_SOURCE.test(state))
            this.addSteamAround(world, pos);
    }

    /**
     * A block changed. Check it directly, adding or removing its steam, and if it is a heat source, check the water
     * around it as a scan would.
     */
    @Override
    public void blockUpdated(Level world, BlockState state, BlockPos pos) {
        if (canSteamSpawn(world, state, pos)) {
            if (!this.hasSystemAtPosition(pos))
                this.systems.put(pos.asLong(), createSteamEffect(world, state, pos));
        } else {
            this.blockUnscan(world, state, pos);
        }

        if (IS_HOT_SOURCE.test(state))
            this.addSteamAround(world, pos);
    }

    /**
     * Adds steam to every steam producer with air above in the 3x3x3 cube around a heat source. The heat source
     * itself is the "hot block nearby" that canSteamSpawn() looks for, so that part needn't be checked again.
     */
    private void addSteamAround(Level world, BlockPos hotPos) {
        final var pos = this.neighbor;
        for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0)
                        continue;
                    pos.setWithOffset(hotPos, dx, dy, dz);
                    if (this.hasSystemAtPosition(pos))
                        continue;
                    var state = world.getBlockState(pos);
                    if (TAG_LIBRARY.is(BlockEffectTags.STEAM_PRODUCERS, state) && world.getBlockState(pos.above()).isAir())
                        this.systems.put(pos.asLong(), createSteamEffect(world, state, pos));
                }
    }

    @NotNull
    private static SteamEffect createSteamEffect(Level world, BlockState state, BlockPos pos) {
        var fluidState = state.getFluidState();
        final float spawnHeight;
        if (fluidState.isEmpty()) {
            spawnHeight = pos.getY() + 0.9F;
        } else {
            spawnHeight = pos.getY() + fluidState.getOwnHeight() + 0.1F;
        }

        return new SteamEffect(world, pos.getX() + 0.5D, spawnHeight, pos.getZ() + 0.5D, fluidState.isEmpty());
    }

    /**
     * Steam rises from a steam producer (water, a water cauldron, a bubble column) with air above and a heat source
     * among the surrounding blocks.
     * <p>
     * The checks run cheapest first: the tag check uses the state already in hand and rejects most blocks before any
     * world lookups.
     */
    private static boolean canSteamSpawn(Level world, BlockState state, BlockPos pos) {
        return TAG_LIBRARY.is(BlockEffectTags.STEAM_PRODUCERS, state)
                && world.getBlockState(pos.above()).isAir()
                && blockExistsAround(world, pos, IS_HOT_SOURCE);
    }

    private static class SteamEffect extends AbstractParticleEmitterEffect {

        private static final double VERTICAL_SPEED = 0.08D;
        private static final double JITTER = 0.4D;

        // A solid source (water cauldron) gives smaller, slower puffs than open water. Can't change without the
        // effect being removed, so it is decided once.
        private final boolean isSolid;

        public SteamEffect(Level world, double x, double y, double z, boolean isSolid) {
            super(world, x, y, z);
            this.isSolid = isSolid;
            this.age = RANDOM.nextInt(20);
        }

        @Override
        public boolean shouldRemove() {
            // Throttle checks for going away
            if ((this.age % 10) == 0) {
                var source = this.world.getBlockState(this.getPos());
                return !canSteamSpawn(this.world, source, this.getPos());
            }
            return false;
        }

        @Override
        protected void handleParticles() {
            // Nothing would be shown past the vanilla particle distance
            if (this.cameraDistanceSq() > PARTICLE_RANGE_SQ)
                return;

            // Follow vanilla's handling of the Particles option: none on Minimal, about a third fewer on Decreased
            var status = GameUtils.getGameSettings().particles().get();
            if (status == ParticleStatus.MINIMAL || (status == ParticleStatus.DECREASED && RANDOM.nextInt(3) == 0))
                return;

            super.handleParticles();
        }

        @Override
        protected Collection<Particle> produceParticles() {
            var x = RANDOM.triangle(this.posX, JITTER);
            var z = RANDOM.triangle(this.posZ, JITTER);
            var particle = this.createParticle(ParticleTypes.CLOUD, x, this.posY, z, 0, VERTICAL_SPEED, 0D);
            return particle.map(
                    p -> {
                    p.setLifetime(p.getLifetime() * 2);
                    if (this.isSolid) {
                        p.scale(0.5F);
                        p.setParticleSpeed(0, VERTICAL_SPEED / 2, 0);
                    }
                    return List.of(p);
                })
           .orElse(List.of());
        }
    }
}
