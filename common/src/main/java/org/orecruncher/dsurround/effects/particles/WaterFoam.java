package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

import java.util.function.Consumer;

/**
 * A patch of foam on the surface of moving water, where flowing water drops a step. It lies flat on the water, rides
 * the surface as the water's height changes, and is carried along by the current, turning slowly as it goes; it fades
 * in quickly and out more slowly. If it drifts off the water it fades out at once.
 * <p>
 * Drawn as an ordinary translucent particle, not soft (see {@link SoftParticles}): it rests just above the water, and
 * the soft shader would fade anything that close to the surface behind it to nothing.
 * <p>
 * Uses the water_foam sprites (textures/particle/water_foam_0 to 5): clusters of small bubbles, one picked at random.
 */
public class WaterFoam extends TextureSheetParticle {

    // How far above the water's surface it rests, so it isn't hidden by it
    private static final double LIFT = 0.02D;
    // The speed the current carries it at, in blocks per tick (about 2.4 blocks a second), and how quickly it turns
    // to follow the current
    private static final double CURRENT_SPEED = 0.12D;
    private static final double STEER = 0.25D;
    // How far it can drop to follow the water down, in blocks: off a step onto the water below
    private static final int MAX_DROP = 2;
    // How quickly it settles to the surface's height as the height changes
    private static final double SETTLE = 0.3D;
    // Fractions of its life spent fading in at the start and out at the end
    private static final float FADE_IN = 0.1F;
    private static final float FADE_OUT = 0.4F;
    // How much it grows as it spreads: 0.4 makes it 1.4 times its starting size by the end
    private static final float GROWTH = 0.4F;
    // How far toward white the biome's water color is shifted: foam is nearly white
    private static final float WHITENESS = 0.85F;

    // Tilting with the water's surface: how quickly it eases to a new slope, and the steepest it follows, as the
    // tangent of the angle (60 degrees), so it never stands on edge
    private static final float TILT_EASE = 0.3F;
    private static final double MAX_SLOPE = Math.tan(Math.toRadians(60D));

    private static final Vector3f UP = new Vector3f(0F, 1F, 0F);

    private final float startSize;
    private final float peakAlpha;
    private final float rollSpeed;

    // The slope of the water's surface it is lying on, as the rise per block east (x) and south (z): now, and as it
    // was last tick, to blend between while drawing
    private float slopeX;
    private float slopeZ;
    private float oSlopeX;
    private float oSlopeZ;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    /**
     * Adds foam patches around falling water landing at {@code pos} (a waterfall, or a step): each starts on the water
     * at the edge of a side of that block, a random side that has water, heading away from it at {@code minSpeed} to
     * {@code maxSpeed} blocks a tick. The current then takes over, or on still water it coasts out across it. Sides
     * without water are skipped, so fewer than {@code count} may be added.
     */
    public static void spawnAround(ClientLevel level, BlockPos pos, int count, double minSpeed, double maxSpeed,
                                   RandomSource random, Consumer<Particle> particles) {
        var neighbor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            var side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            neighbor.setWithOffset(pos, side);
            var fluid = level.getFluidState(neighbor);
            if (fluid.isEmpty())
                continue;

            double across = random.nextDouble() * 0.8D - 0.4D;
            double x = pos.getX() + 0.5D + side.getStepX() * 0.55D + side.getStepZ() * across;
            double z = pos.getZ() + 0.5D + side.getStepZ() * 0.55D + side.getStepX() * across;
            double y = neighbor.getY() + fluid.getHeight(level, neighbor) + LIFT;
            double speed = minSpeed + random.nextDouble() * (maxSpeed - minSpeed);

            var foam = create(level, x, y, z, side.getStepX() * speed, side.getStepZ() * speed);
            if (foam != null)
                particles.accept(foam);
        }
    }

    /**
     * A foam patch on the water at {@code x}, {@code z}, or null if its sprites aren't available. It starts on the
     * surface of the water there.
     *
     * @param xd starting velocity across the water, in blocks per tick; the current then takes over
     */
    @Nullable
    public static Particle create(ClientLevel level, double x, double y, double z, double xd, double zd) {
        var sprites = ParticleUtils.getSpriteProvider(DSurroundParticleTypes.WATER_FOAM);
        if (sprites == null)
            return null;
        return new WaterFoam(level, x, y, z, xd, zd, sprites);
    }

    protected WaterFoam(ClientLevel level, double x, double y, double z, double xd, double zd, SpriteSet sprites) {
        // The constructor without velocity: the one with it adds a random velocity of its own
        super(level, x, y, z);
        this.xd = xd;
        this.yd = 0D;
        this.zd = zd;

        var random = level.random;
        this.lifetime = 20 + random.nextInt(30);
        // Half its width, as quadSize is: 0.16 to 0.36 blocks across
        this.startSize = 0.08F + random.nextFloat() * 0.1F;
        this.quadSize = this.startSize;
        this.peakAlpha = 0.55F + random.nextFloat() * 0.3F;
        this.alpha = 0F;

        // Moved by the current (see tick), not by physics
        this.hasPhysics = false;
        this.gravity = 0F;
        this.friction = 0.96F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.rollSpeed = (random.nextFloat() - 0.5F) * 0.06F;

        this.setSprite(sprites.get(random));

        var biomeColor = level.getBiome(BlockPos.containing(x, y, z)).value().getWaterColor();
        var colorRgb = FastColor.ARGB32.lerp(WHITENESS, biomeColor, ColorPalette.MC_WHITE.getValue());
        this.rCol = ColorPalette.getRed(colorRgb) / 255F;
        this.gCol = ColorPalette.getGreen(colorRgb) / 255F;
        this.bCol = ColorPalette.getBlue(colorRgb) / 255F;
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        // Ages it (removing it at the end of its life) and moves it
        super.tick();
        this.oRoll = this.roll;
        this.roll += this.rollSpeed;
        this.oSlopeX = this.slopeX;
        this.oSlopeZ = this.slopeZ;
        if (this.removed)
            return;

        // The water it is floating on: just under it, as it rests a little above the surface
        var pos = BlockPos.containing(this.x, this.y - LIFT - 0.05D, this.z).mutable();
        var fluid = this.level.getFluidState(pos);

        // Carried over the edge of a step, the water drops away: there is only air under it. Look down through the
        // air for the water below, and settling to its surface (below) takes the foam down with it.
        for (int drop = 0; fluid.isEmpty() && drop < MAX_DROP && this.level.getBlockState(pos).isAir(); drop++) {
            pos.move(Direction.DOWN);
            fluid = this.level.getFluidState(pos);
        }

        if (fluid.isEmpty()) {
            // Drifted off the water, onto the bank or over a drop too deep to follow: fade out over the next few ticks
            this.lifetime = Math.min(this.lifetime, this.age + 5);
            return;
        }

        // Turn toward the current, which carries it downstream (and pours it off the next step). Still water (a pool)
        // has no current: there it coasts on, slowing by drag alone, so foam thrown out from an impact spreads
        // across the pool rather than stopping where it started.
        var flow = fluid.getFlow(this.level, pos);
        if (flow.x * flow.x + flow.z * flow.z > 1.0E-6D) {
            this.xd += (flow.x * CURRENT_SPEED - this.xd) * STEER;
            this.zd += (flow.z * CURRENT_SPEED - this.zd) * STEER;
        }

        // The slope of the surface here, from the surface heights either side, east-west and north-south
        double center = pos.getY() + fluid.getHeight(this.level, pos);
        double slopeX = slope(this.surfaceHeight(pos, -1, 0), center, this.surfaceHeight(pos, 1, 0));
        double slopeZ = slope(this.surfaceHeight(pos, 0, -1), center, this.surfaceHeight(pos, 0, 1));
        double steepness = Math.sqrt(slopeX * slopeX + slopeZ * slopeZ);
        if (steepness > MAX_SLOPE) {
            slopeX *= MAX_SLOPE / steepness;
            slopeZ *= MAX_SLOPE / steepness;
        }

        // Settle to the surface, where it is on the slope rather than the block's height at its middle: flowing
        // water gets shallower as it spreads, and drops away over the edge of a step
        double surface = center + LIFT
                + slopeX * (this.x - (pos.getX() + 0.5D))
                + slopeZ * (this.z - (pos.getZ() + 0.5D));
        this.setPos(this.x, Mth.lerp(SETTLE, this.y, surface), this.z);

        // And tilt to lie along it, easing round rather than snapping
        this.slopeX += ((float) slopeX - this.slopeX) * TILT_EASE;
        this.slopeZ += ((float) slopeZ - this.slopeZ) * TILT_EASE;
    }

    /**
     * The height of the water's surface in the block {@code dx}, {@code dz} from {@code pos}, or NaN if there is no
     * water there to follow. Where that block is air with water below it, as past the edge of a step, it is the
     * surface below: so the slope dips over the edge as the water does.
     */
    private double surfaceHeight(BlockPos pos, int dx, int dz) {
        this.probe.setWithOffset(pos, dx, 0, dz);
        var fluid = this.level.getFluidState(this.probe);
        if (fluid.isEmpty() && this.level.getBlockState(this.probe).isAir()) {
            this.probe.move(Direction.DOWN);
            fluid = this.level.getFluidState(this.probe);
        }
        return fluid.isEmpty() ? Double.NaN : this.probe.getY() + fluid.getHeight(this.level, this.probe);
    }

    /**
     * The rise per block across a block, from the surface heights before it, at it and after it: across both sides
     * where there is water on both, or toward the one side that has some; level if neither does (a bank both sides).
     */
    static double slope(double before, double center, double after) {
        boolean hasBefore = !Double.isNaN(before);
        boolean hasAfter = !Double.isNaN(after);
        if (hasBefore && hasAfter)
            return (after - before) / 2D;
        if (hasAfter)
            return after - center;
        if (hasBefore)
            return center - before;
        return 0D;
    }

    /**
     * How far through its life it is, 0 to 1, between ticks.
     */
    private float lifeFraction(float partialTick) {
        return Mth.clamp((this.age + partialTick) / this.lifetime, 0F, 1F);
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.startSize * (1F + GROWTH * this.lifeFraction(partialTick));
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, @NotNull Camera camera, float partialTick) {
        // Set each frame, so the fades are smooth rather than stepping once a tick
        float life = this.lifeFraction(partialTick);
        float fade = Math.min(life / FADE_IN, (1F - life) / FADE_OUT);
        this.alpha = this.peakAlpha * Mth.clamp(fade, 0F, 1F);

        var rotation = orientation(
                Mth.lerp(partialTick, this.oSlopeX, this.slopeX),
                Mth.lerp(partialTick, this.oSlopeZ, this.slopeZ),
                Mth.lerp(partialTick, this.oRoll, this.roll));
        this.renderRotatedQuad(buffer, camera, rotation, partialTick);
    }

    /**
     * The rotation that lays the quad on a surface with the given slope (rise per block east and south), turned by
     * {@code spin} about the surface's normal. Read right to left: the quad faces +Z, so tip it back onto its face
     * (facing up), turn it about the vertical, then tilt it from facing straight up to facing out of the surface.
     */
    static Quaternionf orientation(float slopeX, float slopeZ, float spin) {
        var surfaceNormal = new Vector3f(-slopeX, 1F, -slopeZ).normalize();
        return new Quaternionf()
                .rotateTo(UP, surfaceNormal)
                .rotateY(spin)
                .rotateX(-Mth.HALF_PI);
    }
}
