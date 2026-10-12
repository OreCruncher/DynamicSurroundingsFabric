package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.runtime.weather.StormSounds;
import org.orecruncher.dsurround.tags.BlockEffectTags;

/**
 * What the weather does where it comes down, each tick: rain drops splash, as vanilla's do, puff smoke on something
 * hot, or on soft ground leave a damp spot ({@link DampSpots}); and in a dust storm, the sound of blowing dust plays
 * somewhere it is blowing.
 */
public final class Landings {

    /**
     * Damp spots a tick at most, at the default range; as many more for the area at a wider one
     */
    static final int MAX_DAMP_SPOTS_PER_TICK = 16;
    /**
     * Drops smaller than this never splash on a hard surface, as fine drizzle doesn't; from there the chance rises to
     * certain at {@link #ALWAYS_SPLASH_FROM}, so there is no hard edge between drops that do and don't
     */
    static final float NO_SPLASH_BELOW = 0.15F;
    static final float ALWAYS_SPLASH_FROM = 0.4F;

    private final ITagLibrary tagLibrary;
    private final ISoundLibrary soundLibrary;
    private final Configuration.WeatherOptions options;
    private final BlockPos.MutableBlockPos landingPos = new BlockPos.MutableBlockPos();
    // Ticks since the dust sound last played, as vanilla counts for its rain sound
    private int dustSoundTime;

    public Landings(ITagLibrary tagLibrary, ISoundLibrary soundLibrary, Configuration.WeatherOptions options) {
        this.tagLibrary = tagLibrary;
        this.soundLibrary = soundLibrary;
        this.options = options;
    }

    /**
     * Where rain drops landed in the field's last tick: a splash, as vanilla's, or a puff of smoke on something hot,
     * or a damp spot on soft ground. Follows the particle setting, as vanilla's splashes do.
     */
    public void splash(ClientLevel level, PrecipitationField field, DampSpots dampSpots, ParticleStatus status, RandomSource random) {
        if (status == ParticleStatus.MINIMAL)
            return;
        int landings = status == ParticleStatus.DECREASED ? field.landings() / 2 : field.landings();
        int maxSpots = PrecipitationField.forArea(MAX_DAMP_SPOTS_PER_TICK, field.radius());
        int spots = 0;

        for (int i = 0; i < landings; i++) {
            float x = field.landingX(i);
            float z = field.landingZ(i);
            var pos = this.landingPos.set(Mth.floor(x), field.landingGround(i) - 1, Mth.floor(z));
            var state = level.getBlockState(pos);
            var fluid = level.getFluidState(pos);

            // The top of what the drop hit, where it hit it: a slab, a snow layer, the water's surface
            var shapeTop = state.getCollisionShape(level, pos).max(Direction.Axis.Y, x - pos.getX(), z - pos.getZ());
            var y = pos.getY() + surfaceTop(shapeTop, fluid.getHeight(level, pos));

            if (fluid.is(FluidTags.LAVA) || state.is(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(state)) {
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0D, 0D, 0D);
            } else if (fluid.isEmpty() && this.tagLibrary.is(BlockEffectTags.RAIN_ABSORBING, state)) {
                if (spots++ < maxSpots)
                    dampSpots.add(x, y, z, field.landingSize(i), random);
            } else if (!fluid.isEmpty() || random.nextFloat() < splashChance(field.landingSize(i))) {
                // On water, the mod's ripple takes the splash's place. Every drop makes one there: drizzle on a
                // pond is a scatter of small rings.
                level.addParticle(ParticleTypes.RAIN, x, y, z, 0D, 0D, 0D);
            }
        }
    }

    /**
     * The sound of blowing dust, as vanilla plays its rain: every few ticks, on the ground somewhere near the player
     * where the storm brings dust. Vanilla's own rain sound stays quiet there, as nothing falls.
     */
    public void dustSound(ClientLevel level, PrecipitationField field, IColumnSource columns, double cameraX, double cameraZ,
                          float rainLevel, RandomSource random) {
        if (!this.options.enableStormSounds || field.dustCount() == 0)
            return;
        if (!StormSounds.isTimeForSound(random.nextInt(StormSounds.SOUND_CHANCE), this.dustSoundTime++))
            return;
        // A few tries for a spot with dust; at the edge of a desert, most of the area may have none
        int radius = field.radius();
        for (int tries = 0; tries < 4; tries++) {
            double x = cameraX + random.nextInt(radius * 2 + 1) - radius;
            double z = cameraZ + random.nextInt(radius * 2 + 1) - radius;
            if (!field.isDustAt(x, z))
                continue;
            this.dustSoundTime = 0;
            double y = columns.groundHeight(Mth.floor(x), Mth.floor(z)) + 0.1D;
            level.playLocalSound(x, y, z, this.soundLibrary.getSound(StormSounds.DUST), SoundSource.WEATHER,
                    StormSounds.dustVolume(rainLevel), StormSounds.dustPitch(random.nextFloat()), false);
            return;
        }
    }

    /**
     * The chance a drop of the given size, 0 to 1, splashes on a hard surface
     */
    static float splashChance(float dropSize) {
        return Mth.clamp((dropSize - NO_SPLASH_BELOW) / (ALWAYS_SPLASH_FROM - NO_SPLASH_BELOW), 0F, 1F);
    }

    /**
     * How far up its block the surface a drop landed on is: the top of the block's shape where the drop hit, or of
     * the fluid in it, whichever is higher. A block with no shape there (water, or something motion blocking that
     * can be walked through) has none to offer, so the fluid's surface decides; with neither, the top of the block.
     * The splash must be inside the water for the water ripple to take its place.
     *
     * @param shapeTop    the top of the collision shape at the point, or negative infinity where it has none
     * @param fluidHeight the fluid's height in the block, 0 for none
     */
    static double surfaceTop(double shapeTop, float fluidHeight) {
        var top = Math.max(shapeTop, fluidHeight);
        return Double.isFinite(top) && top > 0D ? top : 1D;
    }
}
