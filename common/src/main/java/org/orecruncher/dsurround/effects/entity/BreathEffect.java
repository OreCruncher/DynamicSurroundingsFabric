package org.orecruncher.dsurround.effects.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.effects.particles.FrostBreathParticle;
import org.orecruncher.dsurround.effects.particles.ParticleUtils;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.lib.random.MurmurHash3;
import org.orecruncher.dsurround.lib.McCompat;

public class BreathEffect extends EntityEffectBase {

    private static final ISeasonalInformation SEASONAL_INFORMATION = ContainerManager.resolve(ISeasonalInformation.class);

    private final ITickCount tickCount;
    private int seed;

    public BreathEffect(ITickCount tickCount) {
        this.tickCount = tickCount;
    }

    @Override
    public void activate(final EntityEffectInfo info) {
        if (info.isRemoved())
            this.seed = 0;
        else
            this.seed = MurmurHash3.hash(info.getEntity().getId()) & 0xFFFF;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        if (info.isRemoved())
            return;

        var entity = info.getEntity();
        if (!this.isBreathVisible(entity))
            return;

        final int c = (int) (this.tickCount.getTickCount() + this.seed);
        final BlockPos headPos = getHeadPosition(entity);
        final BlockState state = entity.level().getBlockState(headPos);
        if (showWaterBubbles(state)) {
            final int air = entity.getAirSupply();
            if (air > 0) {
                final int interval = c % 3;
                if (interval == 0) {
                    createBubbleParticle(entity, false);
                }
            } else if (air == 0) {
                // Need to generate a bunch of bubbles due to drowning
                for (int i = 0; i < 8; i++) {
                    createBubbleParticle(entity, true);
                }
            }
        } else {
            final int interval = (c / 10) % 8;
            if (interval < 3 && showFrostBreath(entity, state, headPos)) {
                createFrostParticle(entity);
            }
        }
    }

    protected boolean isBreathVisible(final LivingEntity entity) {
        final var player = GameUtils.getPlayer().orElseThrow();
        var settings = GameUtils.getGameSettings();
        if (entity.getId() == player.getId()) {
            return !(player.isSpectator() || McCompat.optionsHideGui(settings));
        }
        return !entity.isInvisibleTo(player) && player.hasLineOfSight(entity);
    }

    protected BlockPos getHeadPosition(final LivingEntity entity) {
        return BlockPos.containing(entity.getEyePosition());
    }

    protected boolean showWaterBubbles(final BlockState headBlock) {
        return !headBlock.getFluidState().isEmpty();
    }

    protected boolean showFrostBreath(final LivingEntity entity, final BlockState headBlock, final BlockPos pos) {
        if (headBlock.isAir()) {
            return SEASONAL_INFORMATION.isColdTemperature(pos);
        }
        return false;
    }

    protected void createBubbleParticle(LivingEntity entity, boolean isDrowning) {
        var origin = ParticleUtils.getBreathOrigin(entity);
        var rand = org.orecruncher.dsurround.lib.random.Randomizer.current();
        var spread = isDrowning ? 0.10D : 0.035D;
        var x = origin.x + (rand.nextDouble() - rand.nextDouble()) * spread;
        var y = origin.y + (rand.nextDouble() - rand.nextDouble()) * spread;
        var z = origin.z + (rand.nextDouble() - rand.nextDouble()) * spread;
        var dx = (rand.nextDouble() - rand.nextDouble()) * 0.01D;
        var dy = 0.02D + rand.nextDouble() * (isDrowning ? 0.05D : 0.02D);
        var dz = (rand.nextDouble() - rand.nextDouble()) * 0.01D;
        ParticleUtils.addParticle(ParticleUtils.createParticle(ParticleTypes.BUBBLE, x, y, z, dx, dy, dz));
    }

    protected void createFrostParticle(LivingEntity entity) {
        ParticleUtils.addParticle(FrostBreathParticle.create(entity));
    }

}
