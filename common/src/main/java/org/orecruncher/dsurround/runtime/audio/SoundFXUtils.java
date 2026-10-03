package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.math.MathStuff;
import org.orecruncher.dsurround.lib.math.ReusableRaycastContext;
import org.orecruncher.dsurround.lib.math.ReusableRaycastIterator;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;

import java.util.Arrays;

/**
 * Calculates the occlusion, reverb and air absorption filters for one playing sound.
 * <p>
 * The expensive part is ray tracing: rays cast around the sound and bounced off surfaces for reverb, and one cast
 * towards the player for occlusion. Its results are reused while the sound and the player stay in the same blocks
 * and the world hasn't changed (see {@link TraceCache}); the cheap per-update factors (weather, being under water,
 * dampened hearing) are applied every time.
 */
public final class SoundFXUtils {

    private static final IBlockLibrary BLOCK_LIBRARY = ContainerManager.resolve(IBlockLibrary.class);
    private static final ISeasonalInformation SEASONAL_INFORMATION = ContainerManager.resolve(ISeasonalInformation.class);
    private static final Configuration.EnhancedSounds CONFIG = ContainerManager.resolve(Configuration.EnhancedSounds.class);

    /**
     * How much rain and snow absorb sound passing through them.
     */
    private static final float RAIN_AIR_ABSORPTION_FACTOR = 2F;
    private static final float SNOW_AIR_ABSORPTION_FACTOR = 5F;

    /**
     * Maximum number of segments to check when ray tracing for occlusion.
     */
    private static final int OCCLUSION_SEGMENTS = 5;
    /**
     * Number of rays to project when doing reverb calculations.
     */
    private static final int REVERB_RAYS = CONFIG.reverbRays;
    /**
     * Number of bounces a sound wave will make when projecting.
     */
    private static final int REVERB_RAY_BOUNCES = CONFIG.reverbBounces;
    /**
     * Maximum distance to trace a reverb ray segment before stopping.
     */
    private static final float MAX_REVERB_DISTANCE = CONFIG.reverbRayTraceDistance;
    /**
     * Reciprocal of the total number of rays cast.
     */
    private static final float RECIP_TOTAL_RAYS = 1F / (REVERB_RAYS * REVERB_RAY_BOUNCES);
    /**
     * Sound reflection energy coefficient
     */
    private static final float ENERGY_COEFF = 0.75F * 0.25F * RECIP_TOTAL_RAYS;
    /**
     * Sound reflection energy constant
     */
    private static final float ENERGY_CONST = 0.25F * 0.25F * RECIP_TOTAL_RAYS;
    /**
     * Normals for the direction of each of the rays to be cast.
     */
    private static final Vec3[] REVERB_RAY_NORMALS = new Vec3[REVERB_RAYS];
    /**
     * Precalculated vectors to determine end targets relative to an origin.
     */
    private static final Vec3[] REVERB_RAY_PROJECTED = new Vec3[REVERB_RAYS];
    /**
     * Precalculated direction surface normals as Vec3 instead of Vec3i
     */
    private static final Vec3[] SURFACE_DIRECTION_NORMALS = new Vec3[Direction.values().length];

    static {

        // Would have been cool to have a direction vec as a 3d as well as 3i.
        for (final Direction d : Direction.values()) {
            SURFACE_DIRECTION_NORMALS[d.ordinal()] = Vec3.atLowerCornerOf(d.getNormal());
        }

        // Pre-calculate the known vectors that will be projected off a sound source when casting about to establish
        // reverb effects.
        for (int i = 0; i < REVERB_RAYS; i++) {
            final double longitude = MathStuff.ANGLE * i;
            final double latitude = Math.asin(((double) i / REVERB_RAYS) * 2.0D - 1.0D);

            REVERB_RAY_NORMALS[i] = new Vec3(
                    Math.cos(latitude) * Math.cos(longitude),
                    Math.cos(latitude) * Math.sin(longitude),
                    Math.sin(latitude)
            ).normalize();

            REVERB_RAY_PROJECTED[i] = REVERB_RAY_NORMALS[i].scale(MAX_REVERB_DISTANCE);
        }

    }

    private final SourceContext source;

    // Ray traced results, reused while the cache says nothing relevant has changed
    private final TraceCache cache = new TraceCache();
    private Vec3 tracedSoundPos = Vec3.ZERO;
    private float tracedOcclusion;
    private final float[] tracedSendGains = new float[AcousticFilters.CHANNELS];
    private final float[] tracedBounceTotals = new float[REVERB_RAY_BOUNCES];
    private float tracedSharedAirspace;
    private int tracedAirspaceChecks = 1;

    public SoundFXUtils(final SourceContext source) {
        this.source = source;
    }

    public void calculate(final @NotNull WorldContext ctx) {

        assert ctx.player != null;
        assert ctx.world != null;
        assert this.source.getSound() != null;

        if (ctx.isNotValid()
                || !this.source.isEnabled()
                || !SoundInstanceHandler.inRange(ctx.playerEyePosition, this.source.getSound())
                || this.source.getPosition().equals(Vec3.ZERO)) {
            this.cache.invalidate();
            this.clearSettings();
            return;
        }

        // Read before tracing, so a change while tracing makes the next calculation trace again
        final long generation = WorldChangeTracker.generation();
        final long soundBlock = BlockPos.containing(this.source.getPosition()).asLong();
        final long eyeBlock = BlockPos.containing(ctx.playerEyePosition).asLong();

        if (!this.cache.matches(ctx.world, soundBlock, eyeBlock, generation)) {
            // Need to offset sound toward player if it is in a solid block or fluid
            this.tracedSoundPos = SoundGeometry.offsetPositionIfNeeded(ctx.world, this.source.getPosition(), ctx.playerEyePosition);
            this.tracedOcclusion = this.calculateOcclusion(ctx, this.tracedSoundPos, ctx.playerEyePosition);
            this.traceReverb(ctx, this.tracedSoundPos);
            this.cache.update(ctx.world, soundBlock, eyeBlock, generation);
        }

        final float airAbsorptionFactor = calculateWeatherAbsorption(ctx, this.tracedSoundPos, ctx.playerEyePosition);
        final var filters = AcousticFilters.compute(
                this.tracedOcclusion,
                this.tracedSendGains,
                AcousticFilters.channelRatios(this.tracedBounceTotals, REVERB_RAYS),
                AcousticFilters.sharedAirspace(this.tracedSharedAirspace, this.tracedAirspaceChecks),
                ctx.submersion);

        this.apply(filters, airAbsorptionFactor);
    }

    /**
     * Casts rays around the sound, bouncing them off what they hit, and accumulates the reflected energy per reverb
     * channel, the reflectivity per bounce, and how often a reflection point can see the player (shared airspace).
     * <p>
     * Shared airspace is checked from every reflection, or with {@code simplifiedSharedAirspace} only from each
     * ray's last one (up to a quarter as many rays with 4 bounces).
     */
    private void traceReverb(final WorldContext ctx, final Vec3 soundPos) {
        final float[] sendGains = this.tracedSendGains;
        final float[] bounceTotals = this.tracedBounceTotals;
        Arrays.fill(sendGains, 0F);
        Arrays.fill(bounceTotals, 0F);
        float sharedAirspace = 0F;
        final boolean checkLastReflectionOnly = CONFIG.simplifiedSharedAirspace;

        final ReusableRaycastContext traceContext = new ReusableRaycastContext(ctx.world, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY);

        for (int i = 0; i < REVERB_RAYS; i++) {

            Vec3 origin = soundPos;
            Vec3 target = origin.add(REVERB_RAY_PROJECTED[i]);

            var rayHit = traceContext.trace(origin, target);

            if (isMiss(rayHit))
                continue;

            // Additional bounces
            BlockPos lastHitBlock = rayHit.getBlockPos();
            Vec3 lastHitPos = rayHit.getLocation();
            Vec3 lastHitNormal = surfaceNormal(rayHit.getDirection());
            Vec3 lastRayDir = REVERB_RAY_NORMALS[i];

            double totalRayDistance = origin.distanceTo(rayHit.getLocation());

            // Secondary ray bounces
            for (int j = 0; j < REVERB_RAY_BOUNCES; j++) {

                final float blockReflectivity = getReflectivity(ctx.world.getBlockState(lastHitBlock));
                final float energyTowardsPlayer = blockReflectivity * ENERGY_COEFF + ENERGY_CONST;

                final Vec3 newRayDir = MathStuff.reflection(lastRayDir, lastHitNormal);
                origin = MathStuff.addScaled(lastHitPos, newRayDir, 0.01F);
                target = MathStuff.addScaled(origin, newRayDir, MAX_REVERB_DISTANCE);

                rayHit = traceContext.trace(origin, target);
                final boolean missed = isMiss(rayHit);

                if (missed) {
                    totalRayDistance += lastHitPos.distanceTo(ctx.playerEyePosition);
                } else {
                    bounceTotals[j] += blockReflectivity;
                    totalRayDistance += lastHitPos.distanceTo(rayHit.getLocation());

                    lastHitPos = rayHit.getLocation();
                    lastHitNormal = surfaceNormal(rayHit.getDirection());
                    lastRayDir = newRayDir;
                    lastHitBlock = rayHit.getBlockPos();

                    // Cast a ray back at the player.  If it is a miss there is a path back from the reflection
                    // point to the player meaning they share the same airspace.
                    if (!checkLastReflectionOnly && canReachPlayer(traceContext, lastHitPos, lastHitNormal, ctx.playerEyePosition)) {
                        sharedAirspace += 1.0F;
                    }
                }

                assert totalRayDistance >= 0;
                final float reflectionDelay = (float) totalRayDistance * 0.12F * blockReflectivity;

                // Spread the energy over the channels by delay: channel n peaks at a delay of n
                final float cross0 = 1.0F - MathStuff.clamp1(Math.abs(reflectionDelay - 0.0F));
                final float cross1 = 1.0F - MathStuff.clamp1(Math.abs(reflectionDelay - 1.0F));
                final float cross2 = 1.0F - MathStuff.clamp1(Math.abs(reflectionDelay - 2.0F));
                final float cross3 = MathStuff.clamp1(reflectionDelay - 2.0F);

                sendGains[0] += cross0 * energyTowardsPlayer * 6.4F;
                sendGains[1] += cross1 * energyTowardsPlayer * 12.8F;
                sendGains[2] += cross2 * energyTowardsPlayer * 12.8F;
                sendGains[3] += cross3 * energyTowardsPlayer * 12.8F;

                // Nowhere to bounce off of, stop bouncing!
                if (missed) {
                    break;
                }
            }

            // Simplified: one check per ray, from the last surface it reflected off
            if (checkLastReflectionOnly && canReachPlayer(traceContext, lastHitPos, lastHitNormal, ctx.playerEyePosition)) {
                sharedAirspace += 1.0F;
            }
        }

        this.tracedSharedAirspace = sharedAirspace;
        this.tracedAirspaceChecks = checkLastReflectionOnly ? REVERB_RAYS : REVERB_RAYS * REVERB_RAY_BOUNCES;
    }

    /**
     * Whether a straight path leads from a reflection point (just off the surface) to the player.
     */
    private static boolean canReachPlayer(final ReusableRaycastContext traceContext, final Vec3 hitPos, final Vec3 hitNormal, final Vec3 playerEye) {
        final Vec3 start = MathStuff.addScaled(hitPos, hitNormal, 0.01F);
        return isMiss(traceContext.trace(start, playerEye));
    }

    private void apply(final AcousticFilters.Result filters, final float airAbsorptionFactor) {
        synchronized (this.source.sync()) {
            for (int c = 0; c < AcousticFilters.CHANNELS; c++)
                this.source.getSend(c).set(filters.sendGain()[c], filters.sendCutoff()[c]);
            this.source.getDirect().set(filters.directGain(), filters.directCutoff());

            final var airAbsorb = this.source.getAirAbsorb();
            airAbsorb.setValue(airAbsorptionFactor);
            airAbsorb.setProcess(true);
        }
    }

    private void clearSettings() {
        synchronized (this.source.sync()) {
            for (int c = 0; c < AcousticFilters.CHANNELS; c++)
                this.source.getSend(c).disable();
            this.source.getDirect().disable();
            this.source.getAirAbsorb().setProcess(false);
        }
    }

    private float calculateOcclusion(final WorldContext ctx, final Vec3 origin, final Vec3 target) {

        // Shortcut if occlusion isn't to happen for this sound
        if (skipOcclusion(this.source.getCategory()))
            return 0F;

        assert ctx.world != null;
        assert ctx.player != null;

        float factor = 0F;

        Vec3 lastHit = origin;
        BlockState lastState = ctx.world.getBlockState(BlockPos.containing(lastHit.x(), lastHit.y(), lastHit.z()));
        var traceContext = new ReusableRaycastContext(ctx.world, origin, target, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY);
        var itr = new ReusableRaycastIterator(traceContext);
        for (int i = 0; i < OCCLUSION_SEGMENTS && itr.hasNext(); i++) {
            var result = itr.next();
            final float occlusion = getOcclusion(lastState);
            final double distance = lastHit.distanceTo(result.getLocation());
            // Occlusion is scaled by the distance traveled through the block.
            factor += (float) (occlusion * distance);
            lastHit = result.getLocation();
            lastState = ctx.world.getBlockState(result.getBlockPos());
        }

        return factor;
    }

    private static float calculateWeatherAbsorption(final WorldContext ctx, final Vec3 pt1, final Vec3 pt2) {
        assert ctx.world != null;

        if (!ctx.isPrecipitating)
            return 1F;

        final BlockPos low = BlockPos.containing(pt1);
        // Halfway between the two points (not addScaled, which would give pt1 + pt2 / 2)
        final BlockPos mid = BlockPos.containing(pt1.lerp(pt2, 0.5D));
        final BlockPos high = BlockPos.containing(pt2);

        // Determine the precipitation type at each point
        final Biome.Precipitation rt1 = SEASONAL_INFORMATION.getActivePrecipitation(low);
        final Biome.Precipitation rt2 = SEASONAL_INFORMATION.getActivePrecipitation(mid);
        final Biome.Precipitation rt3 = SEASONAL_INFORMATION.getActivePrecipitation(high);

        // Calculate the impact of weather on dampening
        float factor = calcFactor(rt1, 0.25F);
        factor += calcFactor(rt2, 0.5F);
        factor += calcFactor(rt3, 0.25F);
        factor *= ctx.precipitationStrength;

        return factor;
    }

    private static float getReflectivity(BlockState state) {
        // Use the weak form because the BlockInfo may not be filled out when
        // the FX system needs to evaluate. The info object should only
        // be filled out by the render thread.
        return BLOCK_LIBRARY.getBlockInfoWeak(state).getSoundReflectivity();
    }

    private static float getOcclusion(BlockState state) {
        // Use the weak form because the BlockInfo may not be filled out when
        // the FX system needs to evaluate. The info object should only
        // be filled out by the render thread.
        return BLOCK_LIBRARY.getBlockInfoWeak(state).getSoundOcclusion();
    }

    private static Vec3 surfaceNormal(final Direction d) {
        return SURFACE_DIRECTION_NORMALS[d.ordinal()];
    }

    private static float calcFactor(final Biome.Precipitation type, final float base) {
        return type == Biome.Precipitation.NONE ? base : base * (type == Biome.Precipitation.SNOW ? SNOW_AIR_ABSORPTION_FACTOR : RAIN_AIR_ABSORPTION_FACTOR);
    }

    private static boolean isMiss(final BlockHitResult result) {
        return result.getType() == HitResult.Type.MISS;
    }

    private static boolean skipOcclusion(SoundSource category) {
        return !CONFIG.enableOcclusionProcessing
                || category == SoundSource.MASTER
                || category == SoundSource.MUSIC;
    }

}
