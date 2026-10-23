package org.orecruncher.dsurround.effects.entity;

import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.particles.FlameSpark;
import org.orecruncher.dsurround.effects.particles.FlameTongue;
import org.orecruncher.dsurround.effects.particles.SmokePuff;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Mixers;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.mixinutils.OptionalHook;

/**
 * A burning entity's fire, drawn as a flame jet's is (tongues of flame, sparks and smoke, the tongues lighting what is
 * around them) in place of vanilla's two flat sheets. Vanilla's are left off for the entity while this draws (see
 * {@link BurningEntities}) and kept wherever it doesn't: past the entity effect range, with Particles on Minimal,
 * with a shader pack, and for the player's own body in first person, where vanilla's overlay on the screen stays.
 * <p>
 * The fire fits the entity: more tongues for a bigger body, and bigger ones for a broader one. Tongues start along the
 * path the entity moved this tick and move with it, so a running mob's fire keeps up instead of strewing gaps behind
 * it. Fewer far off, and burning entities share a budget, so a crowd thins out evenly.
 */
public class BurningEffect extends EntityEffectBase {

    private static final IRandomizer RANDOM = Randomizer.current();

    // Tongues a tick per square block of the entity's side (width times height): about 3 for a zombie
    static final float TONGUES_PER_AREA = 2.5F;
    static final float MIN_TONGUES = 0.5F;
    static final float MAX_TONGUES = 8F;
    // Tongue size for an entity of this width: a zombie's (0.6) gets ZOMBIE_SCALE times a flame jet's
    private static final float NORMAL_WIDTH = 0.6F;
    static final float ZOMBIE_SCALE = 1.35F;
    static final float MIN_SCALE = 0.8F;
    static final float MAX_SCALE = 2.4F;
    // Full fire within this distance of the camera, thinning to FAR_SHARE at the edge of the effect range
    static final float NEAR = 8F;
    static final float FAR_SHARE = 0.4F;
    // Tongues a tick for all burning entities together: they live 10 to 18 ticks, so about 1,700 alive at most
    private static final float BUDGET_PER_TICK = 120F;
    private static final FlameBudget BUDGET = new FlameBudget(BUDGET_PER_TICK);
    // Tongues start in the lower part of the body; rising, they cover the rest
    private static final float SPAWN_HEIGHT = 0.8F;
    // How far a tongue strays from the middle, as a share of the width (a Gaussian's spread), and at most
    private static final float SPREAD = 0.25F;
    private static final float MAX_SPREAD = 0.5F;
    // How much of the entity's movement a tongue takes with it
    private static final double FOLLOW = 0.9D;
    // The chance a tick of a puff of smoke from above, and of a spark, for a fully drawn fire
    private static final float SMOKE_CHANCE = 0.12F;
    private static final float SPARK_CHANCE = 0.06F;

    private final Configuration.EntityEffects config;
    private final ITickCount tickCount;
    private int seed;
    // Whether vanilla's fire is left off for the entity because this draws it
    private boolean drawing;

    public BurningEffect(Configuration.EntityEffects config, ITickCount tickCount) {
        this.config = config;
        this.tickCount = tickCount;
    }

    @Override
    public void activate(final EntityEffectInfo info) {
        this.seed = Mixers.fmix32(info.getEntityId()) & 0xFFFF;
    }

    @Override
    public void deactivate(final EntityEffectInfo info) {
        this.setDrawing(info.getEntityId(), false);
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        this.update(info, 0F);
    }

    @Override
    public void tickDying(final EntityEffectInfo info) {
        // A dying body keeps burning as it tips over; otherwise vanilla's fire would be back for its last second
        this.update(info, tipped(info.getEntity().deathTime));
    }

    /**
     * @param tipped how far over the body has tipped, 0 standing to 1 lying down
     */
    private void update(final EntityEffectInfo info, final float tipped) {
        var entity = info.getEntity();
        if (!(entity.level() instanceof ClientLevel level) || !this.drawsFor(entity)) {
            this.setDrawing(info.getEntityId(), false);
            return;
        }
        this.emit(level, entity, tipped);
        // After the flames are made: should they fail, vanilla's fire isn't left off with nothing in its place
        this.setDrawing(info.getEntityId(), true);
    }

    private void setDrawing(int entityId, boolean drawing) {
        if (drawing == this.drawing)
            return;
        this.drawing = drawing;
        if (drawing)
            BurningEntities.add(entityId);
        else
            BurningEntities.remove(entityId);
    }

    /**
     * Whether this draws the entity's fire now, rather than vanilla
     */
    private boolean drawsFor(LivingEntity entity) {
        if (!entity.displayFireAnimation())
            return false;
        // Without the hook vanilla's fire can't be left off, and both together would be too much
        if (OptionalHook.BURNING_ENTITIES.status() == OptionalHook.Status.MISSING)
            return false;
        if (GameUtils.getGameSettings().particles().get() == ParticleStatus.MINIMAL || IrisCompat.isShaderPackInUse())
            return false;
        var mc = GameUtils.getMC();
        return !(mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == entity);
    }

    private void emit(ClientLevel level, LivingEntity entity, float tipped) {
        final float width = entity.getBbWidth();
        // Lying down, the body is about as tall as it is wide
        final float height = Mth.lerp(tipped, entity.getBbHeight(), width);

        // In lava, flames only from what is above the surface
        final double submerged = entity.isInLava() ? entity.getFluidHeight(FluidTags.LAVA) : 0D;
        if (submerged >= height)
            return;

        final float share = GameUtils.getGameSettings().particles().get() == ParticleStatus.ALL ? 1F : 0.5F;
        final var camera = GameUtils.getMC().gameRenderer.getMainCamera().getPosition();
        final double distanceSq = camera.distanceToSqr(entity.getX(), entity.getY() + height * 0.5D, entity.getZ());
        final float near = distanceShare(distanceSq, this.config.entityEffectRange);

        final float wanted = tonguesFor(width, height) * share * near;
        final float allowed = BUDGET.share(this.tickCount.getTickCount(), wanted);
        final int tongues = roundRandomly(allowed, RANDOM.nextFloat());
        // How fully it is drawn, for the smoke and sparks
        final float fullness = share * near * (wanted > 0F ? allowed / wanted : 1F);

        // Where it was last tick, and how far it moved since
        final double dx = entity.getX() - entity.xo;
        final double dy = entity.getY() - entity.yo;
        final double dz = entity.getZ() - entity.zo;
        final float scale = scaleFor(width);
        final double spread = width * SPREAD;
        final double maxSpread = width * MAX_SPREAD;
        // As a flame jet's, one tongue in every other tick's batch carries the light
        final boolean light = ((this.tickCount.getTickCount() + this.seed) & 1) == 0
                && !FeatureConflict.BURNING_MOB_LIGHT.isBlocked();

        for (int i = 0; i < tongues; i++) {
            double along = RANDOM.nextDouble();
            double x = entity.xo + dx * along + Mth.clamp(RANDOM.nextGaussian() * spread, -maxSpread, maxSpread);
            double z = entity.zo + dz * along + Mth.clamp(RANDOM.nextGaussian() * spread, -maxSpread, maxSpread);
            double low = entity.yo + dy * along + submerged;
            double y = low + RANDOM.nextDouble() * Math.max(0D, height * SPAWN_HEIGHT - submerged);
            double rise = (0.03D + RANDOM.nextDouble() * 0.03D) * scale;
            this.addParticle(FlameTongue.create(level, x, y, z,
                    dx * FOLLOW + (RANDOM.nextDouble() - 0.5D) * 0.01D,
                    dy * FOLLOW + rise,
                    dz * FOLLOW + (RANDOM.nextDouble() - 0.5D) * 0.01D,
                    scale, light && i == 0));
        }

        if (RANDOM.nextFloat() < SMOKE_CHANCE * fullness) {
            double x = entity.getX() + RANDOM.nextGaussian() * spread;
            double z = entity.getZ() + RANDOM.nextGaussian() * spread;
            this.addParticle(SmokePuff.create(level, x, entity.getY() + height + 0.2D, z, 0.03D + Math.max(0D, dy), scale));
        }

        if (RANDOM.nextFloat() < SPARK_CHANCE * fullness) {
            double x = entity.getX() + RANDOM.nextGaussian() * spread;
            double z = entity.getZ() + RANDOM.nextGaussian() * spread;
            double y = entity.getY() + submerged + RANDOM.nextDouble() * (height - submerged);
            double scatter = 0.06D;
            this.addParticle(FlameSpark.create(level, x, y, z,
                    dx + (RANDOM.nextDouble() - 0.5D) * scatter,
                    0.12D + RANDOM.nextDouble() * 0.1D,
                    dz + (RANDOM.nextDouble() - 0.5D) * scatter));
        }
    }

    /**
     * Tongues a tick, before thinning, for an entity of this size
     */
    static float tonguesFor(float width, float height) {
        return Mth.clamp(TONGUES_PER_AREA * width * height, MIN_TONGUES, MAX_TONGUES);
    }

    /**
     * Tongue size for an entity of this width, {@link #ZOMBIE_SCALE} for a zombie
     */
    static float scaleFor(float width) {
        return Mth.clamp(ZOMBIE_SCALE * Mth.sqrt(width / NORMAL_WIDTH), MIN_SCALE, MAX_SCALE);
    }

    /**
     * How far over a dying body has tipped, 0 to 1, as vanilla's renderer turns it ({@code LivingEntityRenderer}
     * setupRotations): fast at first, flat after about 13 ticks
     */
    static float tipped(int deathTime) {
        return Math.min(1F, Mth.sqrt(Math.max(0F, (deathTime - 1) / 20F * 1.6F)));
    }

    /**
     * The share of its fire drawn at this squared distance from the camera: all of it within {@link #NEAR}, down to
     * {@link #FAR_SHARE} at {@code range}
     */
    static float distanceShare(double distanceSq, int range) {
        double distance = Math.sqrt(distanceSq);
        if (distance <= NEAR || range <= NEAR)
            return 1F;
        float t = (float) Math.min(1D, (distance - NEAR) / (range - NEAR));
        return Mth.lerp(t, 1F, FAR_SHARE);
    }

    /**
     * {@code count} as a whole number, rounded up with a chance equal to its fraction, so that fractions add up over
     * ticks
     *
     * @param chance a random number from 0 to 1
     */
    static int roundRandomly(float count, float chance) {
        int whole = (int) count;
        return whole + (chance < count - whole ? 1 : 0);
    }
}
