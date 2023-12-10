package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.BlockLibrary;
import org.orecruncher.dsurround.lib.math.MathStuff;
import org.orecruncher.dsurround.lib.math.ReusableRaycastContext;
import org.orecruncher.dsurround.lib.math.ReusableRaycastIterator;
import org.orecruncher.dsurround.lib.world.WorldUtils;
import org.orecruncher.dsurround.runtime.audio.effects.Effects;
import org.orecruncher.dsurround.runtime.audio.effects.LowPassData;
import org.orecruncher.dsurround.runtime.audio.effects.SourcePropertyFloat;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;

public final class SoundFXUtils {

    /**
     * Maximum number of segments to check when ray tracing for occlusion.
     */
    private static final int OCCLUSION_SEGMENTS = 5;
    /**
     * Number of rays to project when doing reverb calculations.
     */
    private static final int REVERB_RAYS = Client.Config.enhancedSounds.reverbRays;
    /**
     * Number of bounces a sound wave will make when projecting.
     */
    private static final int REVERB_RAY_BOUNCES = Client.Config.enhancedSounds.reverbBounces;
    /**
     * Maximum distance to trace a reverb ray segment before stopping.
     */
    private static final float MAX_REVERB_DISTANCE = Client.Config.enhancedSounds.reverbRayTraceDistance;
    /**
     * Reciprocal of the total number of rays cast.
     */
    private static final float RECIP_TOTAL_RAYS = 1F / (REVERB_RAYS * REVERB_RAY_BOUNCES);
    /**
     * Reciprocal of the total primary rays.
     */
    private static final float RECIP_PRIMARY_RAYS = 1F / REVERB_RAYS;
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
    private static final Vec3d[] REVERB_RAY_NORMALS = new Vec3d[REVERB_RAYS];
    /**
     * Precalculated vectors to determine end targets relative to an origin.
     */
    private static final Vec3d[] REVERB_RAY_PROJECTED = new Vec3d[REVERB_RAYS];
    /**
     * Precaluclated direction surface normals as Vec3d instead of Vec3i
     */
    private static final Vec3d[] SURFACE_DIRECTION_NORMALS = new Vec3d[Direction.values().length];

    static {

        // Would have been cool to have a direction vec as a 3d as well as 3i.
        for (final Direction d : Direction.values()) {
            SURFACE_DIRECTION_NORMALS[d.ordinal()] = Vec3d.of(d.getVector());
        }

        // Pre-calculate the known vectors that will be projected off a sound source when casting about to establish
        // reverb effects.
        for (int i = 0; i < REVERB_RAYS; i++) {
            final double longitude = MathStuff.ANGLE * i;
            final double latitude = Math.asin(((double) i / REVERB_RAYS) * 2.0D - 1.0D);

            REVERB_RAY_NORMALS[i] = new Vec3d(
                    Math.cos(latitude) * Math.cos(longitude),
                    Math.cos(latitude) * Math.sin(longitude),
                    Math.sin(latitude)
            ).normalize();

            REVERB_RAY_PROJECTED[i] = REVERB_RAY_NORMALS[i].multiply(MAX_REVERB_DISTANCE);
        }

    }

    private final SourceContext source;

    public SoundFXUtils(final SourceContext source) {
        this.source = source;
    }

    public void calculate(final WorldContext ctx) {

        assert ctx.player != null;
        assert ctx.world != null;
        assert this.source.getSound() != null;

        if (ctx.isNotValid()
                || !this.source.isEnabled()
                || !SoundInstanceHandler.inRange(ctx.playerEyePosition, this.source.getSound())
                || this.source.getPosition().equals(Vec3d.ZERO)) {
            this.clearSettings();
            return;
        }

        // Need to offset sound toward player if it is in a solid block
        final Vec3d soundPos = offsetPositionIfSolid(ctx.world, this.source.getPosition(), ctx.playerEyePosition);

        final float absorptionCoeff = Effects.GLOBAL_BLOCK_ABSORPTION * 3.0F;
        final float airAbsorptionFactor = calculateWeatherAbsorption(ctx, soundPos, ctx.playerEyePosition);
        final float occlusionAccumulation = calculateOcclusion(ctx, soundPos, ctx.playerEyePosition);
        final float sendCoeff = -occlusionAccumulation * absorptionCoeff;

        float directCutoff = (float) MathStuff.exp(sendCoeff);

        // Handle any dampening effects from the player - like head in water
        directCutoff *= 1F - ctx.auralDampening;

        // Calculate reverb parameters for this sound
        float sendGain0 = 0F;
        float sendGain1 = 0F;
        float sendGain2 = 0F;
        float sendGain3 = 0F;

        float sendCutoff0;
        float sendCutoff1;
        float sendCutoff2;
        float sendCutoff3;

        // Shoot rays around sound
        final float[] bounceRatio = new float[REVERB_RAY_BOUNCES];

        float sharedAirspace = 0F;

        final ReusableRaycastContext traceContext = new ReusableRaycastContext(ctx.world, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY);

        for (int i = 0; i < REVERB_RAYS; i++) {

            Vec3d origin = soundPos;
            Vec3d target = origin.add(REVERB_RAY_PROJECTED[i]);

            var rayHit = traceContext.trace(origin, target);

            if (isMiss(rayHit))
                continue;

            // Additional bounces
            BlockPos lastHitBlock = rayHit.getBlockPos();
            Vec3d lastHitPos = rayHit.getPos();
            Vec3d lastHitNormal = surfaceNormal(rayHit.getSide());
            Vec3d lastRayDir = REVERB_RAY_NORMALS[i];

            double totalRayDistance = origin.distanceTo(rayHit.getPos());

            // Secondary ray bounces
            for (int j = 0; j < REVERB_RAY_BOUNCES; j++) {

                final float blockReflectivity = getReflectivity(ctx.world.getBlockState(lastHitBlock));
                final float energyTowardsPlayer = blockReflectivity * ENERGY_COEFF + ENERGY_CONST;

                final Vec3d newRayDir = MathStuff.reflection(lastRayDir, lastHitNormal);
                origin = MathStuff.addScaled(lastHitPos, newRayDir, 0.01F);
                target = MathStuff.addScaled(origin, newRayDir, MAX_REVERB_DISTANCE);

                rayHit = traceContext.trace(origin, target);

                if (isMiss(rayHit)) {
                    totalRayDistance += lastHitPos.distanceTo(ctx.playerEyePosition);
                } else {

                    bounceRatio[j] += blockReflectivity;
                    totalRayDistance += lastHitPos.distanceTo(rayHit.getPos());

                    lastHitPos = rayHit.getPos();
                    lastHitNormal = surfaceNormal(rayHit.getSide());
                    lastRayDir = newRayDir;
                    lastHitBlock = rayHit.getBlockPos();

                    // Cast a ray back at the player.  If it is a miss there is a path back from the reflection
                    // point to the player meaning they share the same airspace.
                    final Vec3d finalRayStart = MathStuff.addScaled(lastHitPos, lastHitNormal, 0.01F);
                    var finalRayHit = traceContext.trace(finalRayStart, ctx.playerEyePosition);
                    if (isMiss(finalRayHit)) {
                        sharedAirspace += 1.0F;
                    }
                }

                assert totalRayDistance >= 0;
                final float reflectionDelay = (float) totalRayDistance * 0.12F * blockReflectivity;

                final float cross0 = 1.0F - MathStuff.clamp1(Math.abs(reflectionDelay - 0.0F));
                final float cross1 = 1.0F - MathStuff.clamp1(Math.abs(reflectionDelay - 1.0F));
                final float cross2 = 1.0F - MathStuff.clamp1(Math.abs(reflectionDelay - 2.0F));
                final float cross3 = MathStuff.clamp1(reflectionDelay - 2.0F);

                sendGain0 += cross0 * energyTowardsPlayer * 6.4F;
                sendGain1 += cross1 * energyTowardsPlayer * 12.8F;
                sendGain2 += cross2 * energyTowardsPlayer * 12.8F;
                sendGain3 += cross3 * energyTowardsPlayer * 12.8F;

                // Nowhere to bounce off of, stop bouncing!
                if (isMiss(rayHit)) {
                    break;
                }
            }
        }

        bounceRatio[0] = bounceRatio[0] / REVERB_RAYS;
        bounceRatio[1] = bounceRatio[1] / REVERB_RAYS;
        bounceRatio[2] = bounceRatio[2] / REVERB_RAYS;
        bounceRatio[3] = bounceRatio[3] / REVERB_RAYS;

        sharedAirspace *= RECIP_TOTAL_RAYS * 64F;

        final float sharedAirspaceWeight0 = MathStuff.clamp1(sharedAirspace / 20.0F);
        final float sharedAirspaceWeight1 = MathStuff.clamp1(sharedAirspace / 15.0F);
        final float sharedAirspaceWeight2 = MathStuff.clamp1(sharedAirspace / 10.0F);
        final float sharedAirspaceWeight3 = MathStuff.clamp1(sharedAirspace / 10.0F);

        final float exp1 = (float) MathStuff.exp(sendCoeff);
        final float exp2 = (float) MathStuff.exp(sendCoeff * 1.5F);
        sendCutoff0 = exp1 * (1.0F - sharedAirspaceWeight0) + sharedAirspaceWeight0;
        sendCutoff1 = exp1 * (1.0F - sharedAirspaceWeight1) + sharedAirspaceWeight1;
        sendCutoff2 = exp2 * (1.0F - sharedAirspaceWeight2) + sharedAirspaceWeight2;
        sendCutoff3 = exp2 * (1.0F - sharedAirspaceWeight3) + sharedAirspaceWeight3;

        final float averageSharedAirspace = (sharedAirspaceWeight0 + sharedAirspaceWeight1 + sharedAirspaceWeight2
                + sharedAirspaceWeight3) * 0.25F;
        directCutoff = Math.max((float) Math.sqrt(averageSharedAirspace) * 0.2F, directCutoff);

        float directGain = (float) MathStuff.pow(directCutoff, 0.1);

        sendGain1 *= bounceRatio[1];
        sendGain2 *= (float) MathStuff.pow(bounceRatio[2], 3.0);
        sendGain3 *= (float) MathStuff.pow(bounceRatio[3], 4.0);

        sendGain0 = MathStuff.clamp1(sendGain0);
        sendGain1 = MathStuff.clamp1(sendGain1);
        sendGain2 = MathStuff.clamp1(sendGain2 * 1.05F - 0.05F);
        sendGain3 = MathStuff.clamp1(sendGain3 * 1.05F - 0.05F);

        sendGain0 *= (float) MathStuff.pow(sendCutoff0, 0.1);
        sendGain1 *= (float) MathStuff.pow(sendCutoff1, 0.1);
        sendGain2 *= (float) MathStuff.pow(sendCutoff2, 0.1);
        sendGain3 *= (float) MathStuff.pow(sendCutoff3, 0.1);

        if (ctx.player.isSubmergedInWater()) {
            sendCutoff0 *= 0.4F;
            sendCutoff1 *= 0.4F;
            sendCutoff2 *= 0.4F;
            sendCutoff3 *= 0.4F;
        }

        final LowPassData lp0 = this.source.getLowPass0();
        final LowPassData lp1 = this.source.getLowPass1();
        final LowPassData lp2 = this.source.getLowPass2();
        final LowPassData lp3 = this.source.getLowPass3();
        final LowPassData direct = this.source.getDirect();
        final SourcePropertyFloat prop = this.source.getAirAbsorb();

        synchronized (this.source.sync()) {
            lp0.gain = sendGain0;
            lp0.gainHF = sendCutoff0;
            lp0.setProcess(true);

            lp1.gain = sendGain1;
            lp1.gainHF = sendCutoff1;
            lp1.setProcess(true);

            lp2.gain = sendGain2;
            lp2.gainHF = sendCutoff2;
            lp2.setProcess(true);

            lp3.gain = sendGain3;
            lp3.gainHF = sendCutoff3;
            lp3.setProcess(true);

            direct.gain = directGain;
            direct.gainHF = directCutoff;
            direct.setProcess(true);

            prop.setValue(airAbsorptionFactor);
            prop.setProcess(true);
        }
    }

    private void clearSettings() {
        synchronized (this.source.sync()) {
            source.getLowPass0().setProcess(false);
            source.getLowPass1().setProcess(false);
            source.getLowPass2().setProcess(false);
            source.getLowPass3().setProcess(false);
            source.getDirect().setProcess(false);
            source.getAirAbsorb().setProcess(false);
        }
    }

    private float calculateOcclusion(final WorldContext ctx, final Vec3d origin, final Vec3d target) {

        // Shortcut if occlusion isn't to happen for this sound
        if (skipOcclusion(this.source.getCategory()))
            return 0F;

        assert ctx.world != null;
        assert ctx.player != null;

        float factor = 0F;

        Vec3d lastHit = origin;
        BlockState lastState = ctx.world.getBlockState(new BlockPos(lastHit.getX(), lastHit.getY(), lastHit.getZ()));
        var traceContext = new ReusableRaycastContext(ctx.world, origin, target, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.ANY);
        var itr = new ReusableRaycastIterator(traceContext);
        for (int i = 0; i < OCCLUSION_SEGMENTS; i++) {
            if (itr.hasNext()) {
                var result = itr.next();
                final float occlusion = getOcclusion(lastState);
                final double distance = lastHit.distanceTo(result.getPos());
                // Occlusion is scaled by the distance travelled through the block.
                factor += occlusion * distance;
                lastHit = result.getPos();
                lastState = ctx.world.getBlockState(result.getBlockPos());
            } else {
                break;
            }
        }

        return factor;
    }

    private static float calculateWeatherAbsorption(final WorldContext ctx, final Vec3d pt1, final Vec3d pt2) {
        assert ctx.world != null;

        if (!ctx.isPrecipitating)
            return 1F;

        final BlockPos low = new BlockPos(pt1);
        final BlockPos mid = new BlockPos(MathStuff.addScaled(pt1, pt2, 0.5F));
        final BlockPos high = new BlockPos(pt2);

        // Determine the precipitation type at each point
        final Biome.Precipitation rt1 = WorldUtils.getCurrentPrecipitationAt(ctx.world, low);
        final Biome.Precipitation rt2 = WorldUtils.getCurrentPrecipitationAt(ctx.world, mid);
        final Biome.Precipitation rt3 = WorldUtils.getCurrentPrecipitationAt(ctx.world, high);

        // Calculate the impact of weather on dampening
        float factor = calcFactor(rt1, 0.25F);
        factor += calcFactor(rt2, 0.5F);
        factor += calcFactor(rt3, 0.25F);
        factor *= ctx.precipitationStrength;

        return factor;
    }

    private static float getReflectivity(BlockState state) {
        var info = BlockLibrary.getBlockInfo(state);
        return info.getSoundReflectivity();
    }

    private static float getOcclusion(BlockState state) {
        var info = BlockLibrary.getBlockInfo(state);
        return info.getSoundOcclusion();
    }

    private static Vec3d surfaceNormal(final Direction d) {
        return SURFACE_DIRECTION_NORMALS[d.ordinal()];
    }

    private static Vec3d offsetPositionIfSolid(final World world, final Vec3d origin, final Vec3d target) {
        if (world.getBlockState(new BlockPos(origin)) != Blocks.AIR.getDefaultState()) {
            var normal = origin.relativize(target).normalize();
            return MathStuff.addScaled(origin, normal, 0.876F);
        }
        return origin;
    }

    private static float calcFactor(final Biome.Precipitation type, final float base) {
        return type == Biome.Precipitation.NONE ? base : base * (type == Biome.Precipitation.SNOW ? Effects.SNOW_AIR_ABSORPTION_FACTOR : Effects.RAIN_AIR_ABSORPTION_FACTOR);
    }

    private static boolean isMiss(@Nullable final BlockHitResult result) {
        return result == null || result.getType() == HitResult.Type.MISS;
    }

    private static boolean skipOcclusion(SoundCategory category) {
        return !Client.Config.enhancedSounds.enableOcclusionProcessing
                || category == SoundCategory.MASTER
                || category == SoundCategory.MUSIC;
    }

}