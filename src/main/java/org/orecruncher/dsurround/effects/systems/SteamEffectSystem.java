package org.orecruncher.dsurround.effects.systems;

import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.effects.blocks.AbstractParticleEmitterEffect;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.tags.BlockEffectTags;

import java.util.Optional;

import static org.orecruncher.dsurround.effects.BlockEffectUtils.*;

public class SteamEffectSystem extends AbstractEffectSystem implements IEffectSystem {

    public SteamEffectSystem(IModLog logger, Configuration config) {
        super(logger, config,"Steam");
    }

    @Override
    public boolean isEnabled() {
        return this.config.blockEffects.steamColumnEnabled;
    }

    @Override
    public void blockScan(Level world, BlockState state, BlockPos pos) {
        if (!this.isEnabled())
            return;

        // Steam jet can form if the blockState in question is a fluid block, there is an air block
        // above, and there is a hot block adjacent.
        if(canSteamSpawn(world, state, pos)) {
            // Ignore if steam is already present.  This scan is due to a block update of some
            // sort.
            if (this.hasSystemAtPosition(pos))
                return;

            // We are going for spawn! The location of where the steam column starts
            // is based on whether we have a fluid or a solid water block like a
            // water cauldron.
            var effect = getSteamEffect(world, state, pos);
            this.systems.put(pos.asLong(), effect);

        } else {
            // This could have been the result of a block update.  Remove any existing
            // effect.
            this.blockUnscan(world, state, pos);
        }
    }

    @NotNull
    private static SteamEffect getSteamEffect(Level world, BlockState state, BlockPos pos) {
        var fluidState = state.getFluidState();
        final float spawnHeight;
        if (fluidState.isEmpty()) {
            spawnHeight = pos.getY() + 0.9F;
        } else {
            spawnHeight = pos.getY() + fluidState.getOwnHeight() + 0.1F;
        }

        return new SteamEffect(world, pos.getX() + 0.5D, spawnHeight, pos.getZ() + 0.5D);
    }

    private static boolean canSteamSpawn(Level world, BlockState state, BlockPos pos) {
        return world.getBlockState(pos.above()).isAir()
                && TAG_LIBRARY.is(BlockEffectTags.STEAM_PRODUCERS, state)
                && blockExistsAround(world, pos, IS_HOT_SOURCE);
    }

    private static class SteamEffect extends AbstractParticleEmitterEffect {

        public SteamEffect(Level world, double x, double y, double z) {
            super(world, x, y, z);
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
        protected Optional<Particle> produceParticle() {
            final double VERTICAL_SPEED = 0.08D;
            final double JITTER = 0.4D;
            var isSolid = this.world.getBlockState(this.getPos()).getFluidState().isEmpty();
            var x = RANDOM.triangle(this.posX, JITTER);
            var z = RANDOM.triangle(this.posZ, JITTER);
            var particle = this.createParticle(ParticleTypes.CLOUD, x, this.posY, z, 0, VERTICAL_SPEED, 0D);
            particle.ifPresent(p -> {
                p.setLifetime(p.getLifetime() * 2);
                if (isSolid) {
                    p.scale(0.5F);
                    p.setParticleSpeed(0, VERTICAL_SPEED / 2, 0);
                }
            });
            return particle;
        }
    }
}
