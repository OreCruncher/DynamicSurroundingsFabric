package org.orecruncher.dsurround.effects.particles;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;

/**
 * A slow, wandering flight, for small flying things like fireflies. The heading drifts by a turn rate that itself
 * wanders (and is damped, so the flier curves rather than spins), speed swells and eases a little, there is a gentle
 * bob, and the velocity eases toward all that each tick so turns are smooth. Looking a few ticks ahead, it turns
 * aside and lifts when something is in the way, rather than colliding.
 * <p>
 * How much it climbs or sinks is up to the flier, passed to each {@link #tick}: a firefly rises as it flashes.
 * Holds the velocity it steers; the flier moves by {@link #xd()}, {@link #yd()} and {@link #zd()}.
 */
public final class WanderingFlight {

    /**
     * How a flight behaves.
     *
     * @param minSpeed    slowest cruising speed, blocks per tick (each flight picks its own, between these)
     * @param maxSpeed    fastest cruising speed
     * @param turnWander  how much the turn rate wanders each tick, radians: more makes a more erratic flight
     * @param turnDamping how much of the turn rate carries over to the next tick: less makes straighter flight
     * @param steer       how much of the way to its target the velocity moves each tick, 0 to 1
     * @param lift        climb speed, blocks per tick, at a climb of 1
     * @param bob         size of the bob, blocks per tick
     * @param bobRate     how quickly it bobs, radians per tick
     * @param lookAhead   how many ticks ahead to look for something in the way
     */
    public record Settings(float minSpeed, float maxSpeed, float turnWander, float turnDamping, float steer,
                           float lift, float bob, float bobRate, int lookAhead) {
    }

    /**
     * Whether something is in the way at a point.
     */
    @FunctionalInterface
    public interface Obstacles {
        boolean isBlocked(double x, double y, double z);
    }

    /**
     * Blocks with a collision shape are in the way: the ground, logs, leaves; not grass or flowers.
     */
    public static Obstacles blocksIn(BlockGetter level) {
        var pos = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            pos.set(x, y, z);
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        };
    }

    // How much speed swells and eases, and how quickly
    private static final float SPEED_SWELL = 0.25F;
    private static final float SPEED_SWELL_RATE = 0.11F;

    private final Settings settings;
    private final RandomSource random;
    private final float cruiseSpeed;
    private final float phase;
    private float heading;
    private float turnRate;
    private double xd;
    private double yd;
    private double zd;

    public WanderingFlight(Settings settings, RandomSource random) {
        this.settings = settings;
        this.random = random;
        this.cruiseSpeed = settings.minSpeed() + random.nextFloat() * (settings.maxSpeed() - settings.minSpeed());
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.heading = random.nextFloat() * Mth.TWO_PI;
        this.turnRate = (float) random.nextGaussian() * settings.turnWander();

        // Already under way
        this.xd = Mth.cos(this.heading) * this.cruiseSpeed;
        this.zd = Mth.sin(this.heading) * this.cruiseSpeed;
    }

    /**
     * Steers for the next tick.
     *
     * @param x         where the flier is
     * @param age       its age in ticks, for the bob and speed swell
     * @param climb     how much it wants to climb (positive) or sink (negative), as a fraction of the lift
     * @param obstacles what is in the way
     */
    public void tick(double x, double y, double z, int age, float climb, Obstacles obstacles) {
        var s = this.settings;

        // Wander: the turn rate drifts, and is damped so the flier curves rather than circles
        this.turnRate = this.turnRate * s.turnDamping() + (float) this.random.nextGaussian() * s.turnWander();
        this.heading += this.turnRate;

        var speed = this.cruiseSpeed * (1F - SPEED_SWELL + SPEED_SWELL * Mth.sin(age * SPEED_SWELL_RATE + this.phase));
        var targetX = Mth.cos(this.heading) * speed;
        var targetZ = Mth.sin(this.heading) * speed;
        var targetY = s.lift() * climb + s.bob() * Mth.sin(age * s.bobRate() + this.phase);

        // Something in the way: turn aside and lift over it
        var ahead = s.lookAhead();
        if (obstacles.isBlocked(x + targetX * ahead, y + targetY * ahead, z + targetZ * ahead)) {
            this.heading += Mth.HALF_PI + this.random.nextFloat() * Mth.PI * 0.5F;
            this.turnRate = 0F;
            targetX = Mth.cos(this.heading) * speed;
            targetZ = Mth.sin(this.heading) * speed;
            targetY = Math.abs(targetY) + s.lift() * 0.5F;
        }

        this.xd += (targetX - this.xd) * s.steer();
        this.yd += (targetY - this.yd) * s.steer();
        this.zd += (targetZ - this.zd) * s.steer();
    }

    public double xd() {
        return this.xd;
    }

    public double yd() {
        return this.yd;
    }

    public double zd() {
        return this.zd;
    }
}
