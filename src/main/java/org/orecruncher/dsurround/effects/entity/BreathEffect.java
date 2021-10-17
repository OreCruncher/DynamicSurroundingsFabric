package org.orecruncher.dsurround.effects.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.particles.FrostBreathParticle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.TickCounter;
import org.orecruncher.dsurround.lib.random.MurmurHash3;
import org.orecruncher.dsurround.lib.world.WorldUtils;

@Environment(EnvType.CLIENT)
public class BreathEffect extends EntityEffectBase {

    private int seed;

    @Override
    public void activate(final EntityEffectInfo info) {
        this.seed = MurmurHash3.hash(info.getEntity().get().getId()) & 0xFFFF;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        final LivingEntity entity = info.getEntity().get();
        if (isBreathVisible(entity)) {
            final int c = (int) (TickCounter.getTickCount() + this.seed);
            final BlockPos headPos = getHeadPosition(entity);
            final BlockState state = entity.getEntityWorld().getBlockState(headPos);
            if (showWaterBubbles(state)) {
                final int air = entity.getAir();
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
        final PlayerEntity player = GameUtils.getPlayer();
        if (entity == player) {
            return !(player.isSpectator() || GameUtils.getGameSettings().hudHidden);
        }
        return !entity.isInvisibleTo(player) && entity.canSee(player);
    }

    protected BlockPos getHeadPosition(final LivingEntity entity) {
        final double d0 = entity.getEyeY();
        return new BlockPos(entity.getX(), d0, entity.getZ());
    }

    protected boolean showWaterBubbles(final BlockState headBlock) {
        return headBlock.getMaterial().isLiquid();
    }

    protected boolean showFrostBreath(final LivingEntity entity, final BlockState headBlock, final BlockPos pos) {
        if (headBlock.getMaterial() == Material.AIR) {
            final World world = entity.getEntityWorld();
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