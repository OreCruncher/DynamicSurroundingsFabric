package org.orecruncher.dsurround.effects.systems;

import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.blocks.AbstractParticleEmitterEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.tags.FluidTags;

import java.util.Collection;

/**
 * A waterfall, where falling water lands: splashes thrown up from the impact, with mist and foam (see
 * {@link WaterfallSpray}). Its strength is the height of the drop. It checks every half second that it is still a
 * waterfall and that the drop is the same height; {@link WaterfallEffectSystem} replaces one whose drop changed.
 */
final class WaterfallEffect extends AbstractParticleEmitterEffect {

    static final Configuration.WaterfallOptions OPTIONS = ContainerManager.resolve(Configuration.WaterfallOptions.class);

    // Beyond this distance, splashes are halved (and mist and foam). Beyond PARTICLE_RANGE_SQ there are none.
    static final double PARTICLE_FULL_DISTANCE_SQ = 16 * 16;

    // A still water surface is drawn at 8/9 of the block's height
    private static final double WATER_SURFACE_HEIGHT = 8D / 9D;

    protected final double deltaY;
    // The visible surface of the pool where the waterfall lands. Not the fluid height: water with water above
    // it reports a full block (1.0), which is above the surface that is drawn.
    protected final double waterSurfaceY;
    protected int particleLimit;
    private double cameraDistanceSq;
    private boolean strengthStale = false;

    private WaterfallEffect(final int strength, final Level world, final BlockPos loc, final double dY) {
        super(strength, world, loc.getX() + 0.5D, loc.getY() + 0.5D, loc.getZ() + 0.5D, 4);
        this.deltaY = loc.getY() + dY;
        this.waterSurfaceY = loc.getY() + WATER_SURFACE_HEIGHT;
        // 5 splashes, and 2.5 more per block of drop (the same counts as before strength measured the drop)
        this.setSpawnCount(5 + (int) (strength * 2.5F));
    }

    /**
     * Whether a waterfall forms at {@code pos}: falling water lands there (see {@link WaterfallColumn#isLandingSite}),
     * and it is a fluid that makes waterfalls.
     */
    static boolean canForm(Level world, BlockState state, BlockPos pos) {
        return AbstractEffectSystem.TAG_LIBRARY.is(FluidTags.WATERFALL_SOURCE, state.getFluidState())
                && WaterfallColumn.isLandingSite(world, pos);
    }

    /**
     * A waterfall at {@code pos}, as strong as its drop is high.
     */
    static WaterfallEffect create(Level world, BlockState state, BlockPos pos) {
        final float height = state.getFluidState().getHeight(world, pos) + 0.1F;
        return new WaterfallEffect(WaterfallColumn.strength(world, pos), world, pos, height);
    }

    public void setSpawnCount(final int limit) {
        this.particleLimit = Mth.clamp(limit, 5, 20);
    }

    @Override
    public boolean shouldRemove() {
        // Check every half second: is it still a waterfall, and is the column still the same height?
        if ((this.age % 10) != 0)
            return false;
        if (!canForm(this.world, this.world.getBlockState(this.position), this.position))
            return true;
        this.strengthStale = WaterfallColumn.strength(this.world, this.position) != this.strength;
        return false;
    }

    /**
     * True if the column height changed at the last check, so this effect should be replaced.
     */
    boolean isStrengthStale() {
        return this.strengthStale && !this.isDone();
    }

    /**
     * A new effect for the same spot at the current strength, or null if it is no longer a waterfall.
     */
    @Nullable
    WaterfallEffect rebuild() {
        var state = this.world.getBlockState(this.position);
        return canForm(this.world, state, this.position) ? create(this.world, state, this.position) : null;
    }

    private int getSplashParticleSpawnCount(final ParticleStatus status) {
        var count = switch (status) {
            case MINIMAL -> 0;
            case ALL -> this.particleLimit;
            default -> this.particleLimit / 2;
        };

        if (count < 4)
            return count;

        var x = count / 2;
        return RANDOM.nextInt(count - x) + x;
    }

    /**
     * Squared distance from this waterfall to {@code point}.
     */
    double distanceSqTo(final Vec3 point) {
        return point.distanceToSqr(this.posX, this.posY, this.posZ);
    }

    @Override
    protected void handleParticles() {
        if (!OPTIONS.enableParticles)
            return;

        // Nothing is produced past the vanilla particle distance, so skip the work entirely
        this.cameraDistanceSq = this.cameraDistanceSq();
        if (this.cameraDistanceSq > PARTICLE_RANGE_SQ)
            return;

        super.handleParticles();
    }

    @Override
    protected Collection<Particle> produceParticles() {

        var particles = new ObjectArray<Particle>();

        // Minimal particles means no waterfall particles at all, including mist and foam
        final ParticleStatus status = GameUtils.getGameSettings().particles().get();
        if (status == ParticleStatus.MINIMAL)
            return particles;

        final boolean far = this.cameraDistanceSq > PARTICLE_FULL_DISTANCE_SQ;
        var particleCount = this.getSplashParticleSpawnCount(status);
        if (far)
            particleCount /= 2;
        for (int i = 0; i < particleCount; i++) {
            final double xOffset = RANDOM.nextFloat(-1.0F, 1.0F);
            final double zOffset = RANDOM.nextFloat(-1.0F, 1.0F);

            // How far splashes fly: grows with the drop, from 0.2 for the smallest waterfall
            final double motionStr = (this.strength + 3) / 20D;
            final double motionX = xOffset * motionStr;
            final double motionZ = zOffset * motionStr;
            final double motionY = 0.1D + RANDOM.nextFloat() * motionStr;

            var posX = this.posX + xOffset;
            var posZ = this.posZ + zOffset;

            var particle = this.createParticle(ParticleTypes.SPLASH, posX, this.deltaY, posZ, motionX, motionY, motionZ);

            particle.ifPresent(p -> {
                p.setParticleSpeed(motionX, motionY, motionZ);
                p.setLifetime(p.getLifetime() * 2);
                particles.add(p);
            });
        }

        // The effect's own world, not whatever the client has loaded now (they differ briefly during a
        // dimension change). Effects only exist client side, so it is always a ClientLevel.
        if (this.world instanceof ClientLevel clientLevel) {
            if (OPTIONS.enableMist)
                WaterfallSpray.addMist(clientLevel, this.posX, this.waterSurfaceY, this.posZ, this.strength, status, far, RANDOM, particles::add);
            if (OPTIONS.enableFroth)
                WaterfallSpray.addFoam(clientLevel, this.position, this.strength, status, far, RANDOM, particles::add);
        }

        return particles;
    }
}
