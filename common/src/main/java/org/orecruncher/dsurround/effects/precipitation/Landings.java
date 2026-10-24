package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.runtime.weather.StormSounds;
import org.orecruncher.dsurround.runtime.weather.Wind;
import org.orecruncher.dsurround.tags.BlockEffectTags;

import java.util.function.Predicate;

/**
 * What the weather does where it comes down, each tick: rain drops splash, as vanilla's do, puff smoke on something
 * hot, or on soft ground leave a damp spot ({@link DampSpots}); blown against a window, they run down it
 * ({@link WindowStreaks}). Snowflakes leave a fleck that melts away ({@link SnowFlecks}), on the ground or, blown by the
 * wind, on the side of a wall or tree. In a dust storm, the sound of blowing dust plays somewhere it is blowing.
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
    /**
     * How far a snowflake blown in under an overhang or a canopy is followed, in blocks, to what it lands on: a trunk,
     * the ground, the wall behind
     */
    static final double BLOWN_IN_REACH = 8D;
    /**
     * Rain drops and snowflakes blown in that are followed a tick, at most, to what they hit: heavy rain or snow in a
     * strong wind blows many into walls and trees
     */
    static final int MAX_TRACES_PER_TICK = 32;
    /**
     * Windows looked for a tick, at most: in a full storm with a full storm's wind. Each is a column near the camera;
     * the first window in it, on its windward side, gets a drop if the rain reaches it.
     */
    static final float WINDOW_SEED_TRIES = 4F;
    // In still air, a window still gets this share of those: rain falls on it at a slant from gusts and eddies
    static final float WINDOW_SEED_CALM_SHARE = 0.3F;
    // Below this share of a full storm's wind, the air is taken as still: rain reaches any side of a window
    static final float WINDOW_SEED_STILL = 0.05F;
    // How far from the camera windows are looked for, horizontally and up or down
    static final int WINDOW_SEED_RADIUS = 10;
    static final int WINDOW_SEED_HEIGHT = 6;
    // How far down a window a drop can run, in blocks of glass
    static final int MAX_WINDOW_DEPTH = 8;
    // Where a drop stops at the bottom edge of the glass: just above a frame or sill it runs onto; at the edge itself
    // where it drips off into open air
    static final double WINDOW_BOTTOM_MARGIN = 0.06D;
    static final double WINDOW_DRIP_MARGIN = 0.01D;
    // Something this close under the glass's bottom edge takes the drop: a sill, a frame, the wall below
    static final double WINDOW_SILL_REACH = 0.3D;

    private final ITagLibrary tagLibrary;
    private final ISoundLibrary soundLibrary;
    private final Configuration.WeatherOptions options;
    private final BlockPos.MutableBlockPos landingPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
    // Ticks since the dust sound last played, as vanilla counts for its rain sound
    private int dustSoundTime;

    public Landings(ITagLibrary tagLibrary, ISoundLibrary soundLibrary, Configuration.WeatherOptions options) {
        this.tagLibrary = tagLibrary;
        this.soundLibrary = soundLibrary;
        this.options = options;
    }

    /**
     * Where rain drops landed in the field's last tick: a splash, as vanilla's, or a puff of smoke on something hot,
     * or a damp spot on soft ground; one blown against a window runs down it. Where snowflakes landed, a fleck that
     * melts away, except on water, on something hot, or on snow, where it would not show; one blown in from the side
     * lands where it was blowing (see {@link #blownIn}). Follows the particle setting, as vanilla's splashes do.
     */
    public void splash(ClientLevel level, PrecipitationField field, DampSpots dampSpots, SnowFlecks flecks, WindowStreaks streaks,
                       ParticleStatus status, RandomSource random) {
        if (status == ParticleStatus.MINIMAL)
            return;
        int landings = status == ParticleStatus.DECREASED ? field.landings() / 2 : field.landings();
        int maxSpots = PrecipitationField.forArea(MAX_DAMP_SPOTS_PER_TICK, field.radius());
        int spots = 0;
        int traces = 0;

        for (int i = 0; i < landings; i++) {
            float x = field.landingX(i);
            float z = field.landingZ(i);
            if (field.landingBlownIn(i)) {
                if (field.landingIsSnow(i)) {
                    if (traces++ < MAX_TRACES_PER_TICK)
                        this.blownIn(level, field, i, flecks, random);
                } else if (traces++ < MAX_TRACES_PER_TICK) {
                    this.rainBlownIn(level, field, i, streaks, random);
                }
                continue;
            }
            var pos = this.landingPos.set(Mth.floor(x), field.landingGround(i) - 1, Mth.floor(z));
            var state = level.getBlockState(pos);
            var fluid = level.getFluidState(pos);

            // The top of what the drop hit, where it hit it: a slab, a snow layer, the water's surface
            var shapeTop = state.getCollisionShape(level, pos).max(Direction.Axis.Y, x - pos.getX(), z - pos.getZ());
            var y = pos.getY() + surfaceTop(shapeTop, fluid.getHeight(level, pos));

            if (field.landingIsSnow(i)) {
                if (fluid.isEmpty() && !isHot(state) && !state.is(BlockTags.SNOW))
                    flecks.add(x, y, z, field.landingSize(i), field.landingVariant(i), field.landingLight(i), random);
            } else if (fluid.is(FluidTags.LAVA) || isHot(state)) {
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
     * A snowflake carried into a column below its top: followed on from where it was, the way it was moving, to what it
     * hits. Into a wall or a tree beside the open air it was in, it sticks to the side; in under a canopy or an
     * overhang, it goes on to a trunk, or down to the ground beneath. Anything in the way counts as its shape does:
     * leaves, a log, a slab's lower half; not a fence post it passes by. Not on water, something hot, or snow, and not
     * on the underside of anything.
     */
    private void blownIn(ClientLevel level, PrecipitationField field, int i, SnowFlecks flecks, RandomSource random) {
        var hit = trace(level, new Vec3(field.landingX(i), field.landingY(i), field.landingZ(i)),
                field.landingMotionX(i), field.landingMotionY(i), field.landingMotionZ(i));
        if (hit == null)
            return;
        byte face = face(hit.getDirection());
        if (face < 0)
            return;
        // Only on top of things, unless snow is to stick to the sides of them too
        if (face != SnowFlecks.FACE_TOP && !this.options.enableSnowOnSides)
            return;
        var pos = hit.getBlockPos();
        var state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || isHot(state) || state.is(BlockTags.SNOW))
            return;
        // Lit as the spot in front of the face is: in under a canopy, darker than the open air it came from
        int light = LevelRenderer.getLightColor(level, this.landingPos.setWithOffset(pos, hit.getDirection()));
        var at = hit.getLocation();
        flecks.add(at.x, at.y, at.z, face, field.landingSize(i), field.landingVariant(i), light, random);
    }

    /**
     * A rain drop carried into a column below its top, followed on to what it hits: if that is the side of a window,
     * it runs down the glass; if it drifted in under cover down to the ground, it splashes there, or smokes on something
     * hot. Into anything else, it runs off unseen.
     */
    private void rainBlownIn(ClientLevel level, PrecipitationField field, int i, WindowStreaks streaks, RandomSource random) {
        var hit = trace(level, new Vec3(field.landingX(i), field.landingY(i), field.landingZ(i)),
                field.landingMotionX(i), field.landingMotionY(i), field.landingMotionZ(i));
        if (hit == null)
            return;
        if (hit.getDirection() == Direction.UP) {
            var at = hit.getLocation();
            var state = level.getBlockState(hit.getBlockPos());
            if (isHot(state) || state.getFluidState().is(FluidTags.LAVA))
                level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0D, 0D, 0D);
            else if (!state.getFluidState().isEmpty() || random.nextFloat() < splashChance(field.landingSize(i)))
                level.addParticle(ParticleTypes.RAIN, at.x, at.y, at.z, 0D, 0D, 0D);
            return;
        }
        if (!this.options.enableWindowStreaks || !hit.getDirection().getAxis().isHorizontal())
            return;
        var state = level.getBlockState(hit.getBlockPos());
        if (!this.tagLibrary.is(BlockEffectTags.WINDOWS, state))
            return;
        this.startStreak(level, hit.getBlockPos(), hit.getDirection(), hit.getLocation(), field.landingSize(i), streaks, random);
    }

    /**
     * Rain on the windows near the camera, beyond the drops that happen to be blown into them: a few times a tick, more
     * the harder it rains and the stronger the wind, a column near the camera is looked down for a window. A window
     * whose windward side is open to the rain, with nothing in front of it and the sky above, gets a drop somewhere on
     * that side. Windows indoors, under eaves, or facing away from the wind stay dry.
     *
     * @param rainLevel the storm's strength, 0 to 1
     */
    public void seedWindows(ClientLevel level, IColumnSource columns, double cameraX, double cameraY, double cameraZ, float rainLevel,
                            Wind wind, WindowStreaks streaks, RandomSource random) {
        if (!this.options.enableWindowStreaks || rainLevel <= 0F)
            return;
        float windShare = Mth.clamp(wind.speed() / Wind.speedOfPercent(100), 0F, 1F);
        float wanted = WINDOW_SEED_TRIES * Math.min(rainLevel, 1F) * Mth.lerp(windShare, WINDOW_SEED_CALM_SHARE, 1F);
        int tries = AfterRainDrips.tries(wanted, random.nextFloat());
        for (int t = 0; t < tries; t++) {
            int x = Mth.floor(cameraX) + random.nextInt(WINDOW_SEED_RADIUS * 2 + 1) - WINDOW_SEED_RADIUS;
            int z = Mth.floor(cameraZ) + random.nextInt(WINDOW_SEED_RADIUS * 2 + 1) - WINDOW_SEED_RADIUS;
            var window = this.findWindow(level, x, Mth.floor(cameraY), z);
            if (window == null)
                continue;
            var side = windwardFace(wind.directionX(), wind.directionZ(), windShare, random.nextFloat(), random.nextInt(4));
            // In front of that side: open, and open to the sky where it rains
            var front = window.relative(side);
            if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty())
                continue;
            if (columns.groundHeight(front.getX(), front.getZ()) > front.getY())
                continue;
            if (columns.precipitation(front.getX(), front.getY(), front.getZ()) != PrecipitationKind.RAIN)
                continue;
            // Somewhere on that side: straight at the glass from in front of it, so a pane is hit where its glass is
            double across = 0.1D + random.nextDouble() * 0.8D;
            double up = 0.25D + random.nextDouble() * 0.7D;
            var from = new Vec3(
                    side.getStepX() != 0 ? window.getX() + 0.5D + side.getStepX() : window.getX() + across,
                    window.getY() + up,
                    side.getStepZ() != 0 ? window.getZ() + 0.5D + side.getStepZ() : window.getZ() + across);
            var hit = trace(level, from, -side.getStepX(), 0F, -side.getStepZ());
            if (hit == null || hit.getDirection() != side || !hit.getBlockPos().equals(window))
                continue;
            this.startStreak(level, window, side, hit.getLocation(), random.nextFloat(), streaks, random);
        }
    }

    /**
     * The first window block in a column, from the top of the range around the camera down, or null if there is none
     */
    private @Nullable BlockPos findWindow(ClientLevel level, int x, int cameraY, int z) {
        for (int y = cameraY + WINDOW_SEED_HEIGHT; y >= cameraY - WINDOW_SEED_HEIGHT; y--) {
            if (this.tagLibrary.is(BlockEffectTags.WINDOWS, level.getBlockState(this.scanPos.set(x, y, z))))
                return this.scanPos.immutable();
        }
        return null;
    }

    /**
     * The side of a window the rain is blown onto: the one facing into the wind. Which of the two the wind blows across
     * is picked in proportion to how much it blows along each. In still air, any side, as gusts and eddies would.
     *
     * @param windX     the way the wind blows: a unit vector's x
     * @param windZ     and its z
     * @param windShare how strong the wind is, as a share of a full storm's, 0 to 1
     * @param chance    a random number, 0 to 1, to pick between the two sides the wind blows onto
     * @param anySide   a random number, 0 to 3, to pick a side in still air
     */
    static Direction windwardFace(float windX, float windZ, float windShare, float chance, int anySide) {
        if (windShare < WINDOW_SEED_STILL)
            return Direction.from2DDataValue(anySide);
        float alongX = Math.abs(windX), alongZ = Math.abs(windZ);
        // The side looks back the way the wind comes from
        if (chance * (alongX + alongZ) < alongX)
            return windX > 0F ? Direction.WEST : Direction.EAST;
        return windZ > 0F ? Direction.NORTH : Direction.SOUTH;
    }

    /**
     * Starts a drop running down a window where something hit its side
     *
     * @param size how big the drop was, 0 to 1
     */
    private void startStreak(ClientLevel level, BlockPos window, Direction side, Vec3 at, float size, WindowStreaks streaks, RandomSource random) {
        // Down the glass to where it ends: a drop runs down a window of several blocks, then drips off its bottom edge
        // or runs onto what is below
        int bottomY = window.getY() - glassBelow(level, window, side, at, state -> this.tagLibrary.is(BlockEffectTags.WINDOWS, state));
        boolean drips = dripsOff(level, at, side, bottomY);
        double bottom = bottomY + (drips ? WINDOW_DRIP_MARGIN : WINDOW_BOTTOM_MARGIN);
        int light = LevelRenderer.getLightColor(level, this.landingPos.setWithOffset(window, side));
        streaks.add(at.x, at.y, at.z, face(side), bottom, drips, size, light, random);
    }

    /**
     * How many blocks below a window's block the side a drop is on carries on: glass, its face on that side in the
     * same place as where the drop is, and nothing in front of it. Not glass set back or standing out, a pane below a
     * glass block, or glass behind something.
     *
     * @param at       where the drop is, on the glass
     * @param isWindow whether a block is a window
     */
    static int glassBelow(BlockGetter level, BlockPos window, Direction side, Vec3 at, Predicate<BlockState> isWindow) {
        double plane = side.getAxis() == Direction.Axis.X ? at.x : at.z;
        var pos = window.mutable();
        int depth = 0;
        while (depth < MAX_WINDOW_DEPTH) {
            pos.move(Direction.DOWN);
            if (!isWindow.test(level.getBlockState(pos)))
                break;
            // Straight at its side, from a little in front of where the drop runs down: what is met there must be its
            // glass, where the glass above is
            var from = new Vec3(at.x + side.getStepX() * 0.25D, pos.getY() + 0.5D, at.z + side.getStepZ() * 0.25D);
            var hit = trace(level, from, -side.getStepX(), 0F, -side.getStepZ());
            if (hit == null || hit.getDirection() != side || !hit.getBlockPos().equals(pos))
                break;
            double hitPlane = side.getAxis() == Direction.Axis.X ? hit.getLocation().x : hit.getLocation().z;
            if (Math.abs(hitPlane - plane) > 1.0E-3D)
                break;
            depth++;
        }
        return depth;
    }

    /**
     * Whether a drop at the bottom edge of a window drips off it: nothing is just under the edge where it runs down,
     * such as a sill, a frame or the wall below
     *
     * @param at      where the drop is, on the glass
     * @param bottomY the height of the glass's bottom edge
     */
    static boolean dripsOff(BlockGetter level, Vec3 at, Direction side, int bottomY) {
        // A sill standing out under it: straight down from just in front of the glass, at its bottom edge
        var from = new Vec3(at.x + side.getStepX() * 0.01D, bottomY + 0.01D, at.z + side.getStepZ() * 0.01D);
        var below = trace(level, from, 0F, -1F, 0F);
        if (below != null && below.getLocation().y >= bottomY - WINDOW_SILL_REACH)
            return false;
        // The wall below, flush with the glass: straight at it from in front, just under the edge, meeting it no further
        // back than the glass. Set back from the glass, the drop drips off in front of it.
        double plane = side.getAxis() == Direction.Axis.X ? at.x : at.z;
        var front = new Vec3(at.x + side.getStepX() * 0.25D, bottomY - 0.05D, at.z + side.getStepZ() * 0.25D);
        var wall = trace(level, front, -side.getStepX(), 0F, -side.getStepZ());
        if (wall == null || wall.getDirection() != side)
            return true;
        double wallPlane = side.getAxis() == Direction.Axis.X ? wall.getLocation().x : wall.getLocation().z;
        // How far the wall's face is in front of the glass, the way the side looks
        double standsOut = (wallPlane - plane) * (side.getAxis() == Direction.Axis.X ? side.getStepX() : side.getStepZ());
        return standsOut < -1.0E-3D;
    }

    /**
     * What a snowflake blown in hits, following it from where it was the way it was moving, up to
     * {@link #BLOWN_IN_REACH}: the first block whose shape is in the way, or a fluid's surface. Null if nothing is
     * within reach, it started inside something, or it wasn't moving.
     */
    static @Nullable BlockHitResult trace(BlockGetter level, Vec3 from, float dx, float dy, float dz) {
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0F)
            return null;
        var to = from.add(dx / length * BLOWN_IN_REACH, dy / length * BLOWN_IN_REACH, dz / length * BLOWN_IN_REACH);
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK && !hit.isInside() ? hit : null;
    }

    /**
     * The fleck face for the face of a block that was hit, or -1 for its underside, where nothing settles
     */
    static byte face(Direction direction) {
        return switch (direction) {
            case UP -> SnowFlecks.FACE_TOP;
            case WEST -> SnowFlecks.FACE_WEST;
            case EAST -> SnowFlecks.FACE_EAST;
            case NORTH -> SnowFlecks.FACE_NORTH;
            case SOUTH -> SnowFlecks.FACE_SOUTH;
            case DOWN -> -1;
        };
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
        // Over the disc the dust is drawn in, as it is looked up
        int radius = field.radius();
        for (int tries = 0; tries < 4; tries++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float r = radius * Mth.sqrt(random.nextFloat());
            double x = cameraX + Mth.cos(angle) * r;
            double z = cameraZ + Mth.sin(angle) * r;
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
     * Whether a block is hot enough that rain puffs into smoke on it, and snow melts at once
     */
    private static boolean isHot(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(state);
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
