package org.orecruncher.dsurround.effects.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.effects.particles.FrostBreathParticle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.lib.random.MurmurHash3;
import org.orecruncher.dsurround.lib.world.WorldUtils;

public class BreathEffect extends EntityEffectBase {

    private final ITickCount tickCount;
    private int seed;

    public BreathEffect(ITickCount tickCount) {
        this.tickCount = tickCount;
    }

    @Override
    public void activate(final EntityEffectInfo info) {
        this.seed = MurmurHash3.hash(info.getEntity().get().getId()) & 0xFFFF;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        final LivingEntity entity = info.getEntity().get();
        if (isBreathVisible(entity)) {
            final int c = (int) (this.tickCount.getTickCount() + this.seed);
            final BlockPos headPos = getHeadPosition(entity);
            final BlockState state = entity.level().getBlockState(headPos);
            if (showWaterBubbles(state)) {
                final int air = entity.getAirSupply();
                if (air > 0) {
                    final int interval = c % 3;
                    if (interval == 0) {
                        createBubbleParticle(false);
                    }
                } else if (air == 0) {
                    // Need to generate a bunch of bubbles due to drowning
                    for (int i = 0; i < 8; i++) {
                        createBubbleParticle(true);
                    }
                }
            } else {
                final int interval = (c / 10) % 8;
                if (interval < 3 && showFrostBreath(entity, state, headPos)) {
                    createFrostParticle(entity);
                }
            }
        }
    }

    protected boolean isBreathVisible(final LivingEntity entity) {
        final var player = GameUtils.getPlayer().orElseThrow();
        var settings = GameUtils.getGameSettings();
        if (entity == player) {
            return !(player.isSpectator() || settings.hideGui);
        }
        return !entity.isInvisibleTo(player) && player.hasLineOfSight(entity);
    }

    protected BlockPos getHeadPosition(final LivingEntity entity) {
        final double d0 = entity.getEyeY();
        return BlockPos.containing(entity.getX(), d0, entity.getZ());
    }

    protected boolean showWaterBubbles(final BlockState headBlock) {
        return !headBlock.getFluidState().isEmpty();
    }

    protected boolean showFrostBreath(final LivingEntity entity, final BlockState headBlock, final BlockPos pos) {
        if (headBlock.isAir()) {
            final Level world = entity.level();
            return WorldUtils.isColdTemperature(WorldUtils.getTemperatureAt(world, pos));
        }
        return false;
    }

    protected void createBubbleParticle(boolean isDrowning) {
        //final BubbleBreathParticle p = new BubbleBreathParticle(getEntity(), isDrowning);
        //addParticle(p);
    }

    protected void createFrostParticle(LivingEntity entity) {
        var particle = new FrostBreathParticle(entity);
        this.addParticle(particle);
    }

}