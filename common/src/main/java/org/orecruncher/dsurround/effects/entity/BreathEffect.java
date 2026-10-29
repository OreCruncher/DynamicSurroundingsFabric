package org.orecruncher.dsurround.effects.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.effects.particles.FrostBreathParticle;
import org.orecruncher.dsurround.effects.particles.ParticleUtils;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Mixers;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.system.ITickCount;

import java.util.function.BooleanSupplier;

public class BreathEffect extends EntityEffectBase {

    private static final IRandomizer RANDOM = Randomizer.current();
    private static final int DROWNING_BUBBLES = 8;
    // Relative to vanilla's bubble size
    private static final float BREATH_BUBBLE_SCALE = 0.5F;
    private static final float DROWNING_BUBBLE_SCALE = 0.75F;

    private final ITickCount tickCount;
    private final ISeasonalInformation seasonalInformation;
    private int seed;

    public BreathEffect(ITickCount tickCount, ISeasonalInformation seasonalInformation) {
        this.tickCount = tickCount;
        this.seasonalInformation = seasonalInformation;
    }

    @Override
    public void activate(final EntityEffectInfo info) {
        this.seed = Mixers.fmix32(info.getEntityId()) & 0xFFFF;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        var entity = info.getEntity();
        if (!this.canBreathe(entity))
            return;
        final int c = (int) (this.tickCount.getTickCount() + this.seed);
        final Vec3 mouth = this.getMouth(entity);
        final BlockPos headPos = this.getHeadPosition(entity, mouth);
        final BlockState state = entity.level().getBlockState(headPos);

        var breath = breathFor(c, showWaterBubbles(state), entity.getAirSupply(),
                () -> this.isFrostTick(c) && this.showFrostBreath(entity, state, headPos, mouth),
                () -> this.isBreathVisible(entity));

        switch (breath) {
            case BUBBLE -> this.createBubbleParticle(entity, mouth, false);
            case DROWNING -> {
                // Need to generate a bunch of bubbles due to drowning
                for (int i = 0; i < DROWNING_BUBBLES; i++)
                    this.createBubbleParticle(entity, mouth, true);
            }
            case FROST -> this.createFrostParticle(entity, mouth);
            case NONE -> {
            }
        }
    }

    enum Breath {NONE, BUBBLE, DROWNING, FROST}

    /**
     * What breath to show this tick. Under water: a bubble every third tick, or a burst when out of air. In the
     * air: frost for 30 ticks out of every 80, when it's cold. The checks run cheapest first; visibility, a ray
     * cast to the entity, is only asked once there is something to show.
     *
     * @param c          the tick count offset by the entity's seed, so entities don't breathe in step
     * @param underwater whether the entity's head is in a fluid
     * @param air        the entity's air supply
     */
    static Breath breathFor(int c, boolean underwater, int air, BooleanSupplier coldAir, BooleanSupplier visible) {
        Breath breath;
        if (underwater) {
            if (air > 0)
                breath = Math.floorMod(c, 3) == 0 ? Breath.BUBBLE : Breath.NONE;
            else
                breath = air == 0 ? Breath.DROWNING : Breath.NONE;
        } else {
            breath = Math.floorMod(c / 10, 8) < 3 && coldAir.getAsBoolean() ? Breath.FROST : Breath.NONE;
        }
        return breath != Breath.NONE && visible.getAsBoolean() ? breath : Breath.NONE;
    }

    protected boolean isBreathVisible(final LivingEntity entity) {
        final var player = GameUtils.getPlayer().orElseThrow();
        if (entity.getId() == player.getId()) {
            return !(player.isSpectator() || GameUtils.getMC().options.hideGui);
        }
        return !entity.isInvisibleTo(player) && player.hasLineOfSight(entity);
    }

    /**
     * Whether the entity is in a state to breathe where {@link #getMouth} puts it.
     */
    protected boolean canBreathe(final LivingEntity entity) {
        return true;
    }

    /**
     * Whether a frost puff may be breathed out on this tick of the breath, given how often the entity's kind does.
     */
    protected boolean isFrostTick(int c) {
        return true;
    }

    /**
     * Where the breath comes out
     */
    protected Vec3 getMouth(final LivingEntity entity) {
        return ParticleUtils.getBreathOrigin(entity);
    }

    /**
     * The block the entity breathes in: its head's, for telling cold air or water
     */
    protected BlockPos getHeadPosition(final LivingEntity entity, final Vec3 mouth) {
        return BlockPos.containing(entity.getEyePosition());
    }

    /**
     * How fast the frost puff leaves the mouth, in blocks a tick
     */
    protected double getPuffSpeed() {
        return 0.01D;
    }

    /**
     * The frost puff's size: a quarter of a cloud for a grown player
     */
    protected float getPuffSize(final LivingEntity entity) {
        return entity.isBaby() ? 0.125F : 0.25F;
    }

    protected boolean showWaterBubbles(final BlockState headBlock) {
        return !headBlock.getFluidState().isEmpty();
    }

    protected boolean showFrostBreath(final LivingEntity entity, final BlockState headBlock, final BlockPos pos,
                                      final Vec3 mouth) {
        double top = headBlock.isAir() ? 0D
                : headBlock.getCollisionShape(entity.level(), pos).max(Direction.Axis.Y);
        return breathesFreely(top, mouth.y - pos.getY()) && this.seasonalInformation.isColdTemperature(pos);
    }

    /**
     * Whether the mouth is out in the air: above whatever the block it is in has to stand on. A wolf's mouth is
     * half a block up, in the same block as the snow layer it stands in; grass and flowers have nothing to stand on.
     *
     * @param top   the top of the block's collision shape, 0 to 1 (an empty shape's is below 0)
     * @param mouth the mouth's height within the block
     */
    static boolean breathesFreely(double top, double mouth) {
        return mouth >= top;
    }

    /**
     * A bubble from the entity's mouth, drifting out along its look and up. Drowning bubbles spread wider and rise
     * faster. Vanilla's bubble particle pops when it leaves the water.
     */
    protected void createBubbleParticle(LivingEntity entity, Vec3 origin, boolean isDrowning) {
        final Vec3 trajectory = ParticleUtils.getLookTrajectory(entity);
        final double spread = isDrowning ? 0.1D : 0.02D;
        final double rise = isDrowning ? 0.2D : 0.1D;
        var particle = ParticleUtils.createParticle(ParticleTypes.BUBBLE,
                origin.x, origin.y, origin.z,
                trajectory.x * 0.05D + RANDOM.nextGaussian() * spread,
                rise + RANDOM.nextDouble() * 0.05D,
                trajectory.z * 0.05D + RANDOM.nextGaussian() * spread);
        if (particle != null) {
            // Vanilla bubbles are sized for bubble columns and splashes; breath is smaller, and smaller again for babies
            var scale = isDrowning ? DROWNING_BUBBLE_SCALE : BREATH_BUBBLE_SCALE;
            if (entity.isBaby())
                scale *= 0.5F;
            particle.scale(scale);
            this.addParticle(particle);
        }
    }

    protected void createFrostParticle(LivingEntity entity, Vec3 origin) {
        var particle = FrostBreathParticle.create(entity, origin, ParticleUtils.getLookTrajectory(entity),
                this.getPuffSize(entity), this.getPuffSpeed());
        this.addParticle(particle);
    }

}