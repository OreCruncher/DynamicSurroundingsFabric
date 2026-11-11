// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.footprints;

import net.minecraft.server.level.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.FootprintStyle;
import org.orecruncher.dsurround.config.TrackedGround;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.entity.EntityEffectBase;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.tags.EntityEffectTags;

/**
 * Leaves footprints where an entity walks on soft ground (see {@code BlockInfo.takesFootprints}): a print for each foot
 * as it comes down, both on landing. Players' prints are the style chosen in the config; mobs' come from the
 * footprint tags (square, paw, hoof, bird). Their stride, how far apart their feet are and how big their prints are
 * follow their size, so a baby's are smaller.
 * <p>
 * Feet also carry what they walked through ({@link CarriedGround}): snow, mud, sand and soul sand ({@code BlockInfo.tracks}),
 * water, and mud from ground the rain has soaked ({@code BlockInfo.tracksMudWhenWet}). On hard ground each step then
 * leaves a print of it, fainter until it is used up; snow and mud show on soft ground too, in place of the pressed print.
 * Ground soaked by rain shows mud prints itself. What feet walk through replaces what they carry, but snow and mud stay
 * on them until used up, on anything but mud. Water is the exception: wading washes the feet, whatever they carried,
 * and they leave wet prints after.
 * <p>
 * No prints while sneaking, swimming, riding, sleeping or invisible, with Particles on Minimal, or with a shader pack.
 */
public class FootprintEffect extends EntityEffectBase {

    // A player's: 1.8 tall and 0.6 wide, taking a step every 1.25 blocks with feet 0.2 either side of the middle
    private static final float NORMAL_HEIGHT = 1.8F;
    private static final float NORMAL_WIDTH = 0.6F;
    private static final double NORMAL_STRIDE = 1.25D;
    // Below the feet, to find the block they press into
    private static final double PROBE = 1D / 16D;
    // How far the top of that block may be from the feet and still take a print, in blocks: past the edge of a
    // block, the foot is over the one below
    private static final double SURFACE_TOLERANCE = 0.1D;
    // How far below the drawn top of a block feet may sink into it, in blocks: a layer of snow, soul sand, mud
    private static final double MAX_SINK = 0.25D;
    // How much bigger the prints of those in the large footprints tag are, and those in the small and tiny ones
    static final float LARGE = 1.25F;
    static final float SMALL = 0.75F;
    static final float TINY = 0.5F;
    // How wet the ground has to be for feet to pick up mud: 0 to 1, the storm's strength where it is open to the sky
    static final float MUDDY_WETNESS = 0.25F;

    private final Configuration.EntityEffects config;
    private final IBlockLibrary blockLibrary;
    private final ITagLibrary tagLibrary;
    private final Footprints footprints;
    private final StrideTracker strides = new StrideTracker();
    private final CarriedGround carried = new CarriedGround();
    private float carriedStrength;
    // Null for a player, whose style comes from the config
    private @Nullable FootprintStyle style;
    private float sizeBoost = 1F;

    public FootprintEffect(Configuration.EntityEffects config, IBlockLibrary blockLibrary, ITagLibrary tagLibrary, Footprints footprints) {
        this.config = config;
        this.blockLibrary = blockLibrary;
        this.tagLibrary = tagLibrary;
        this.footprints = footprints;
    }

    @Override
    public void activate(final EntityEffectInfo info) {
        var entity = info.getEntity();
        this.style = entity instanceof Player ? null : styleFor(entity);
        var type = entity.getType();
        if (this.tagLibrary.is(EntityEffectTags.FOOTPRINTS_LARGE, type))
            this.sizeBoost = LARGE;
        else if (this.tagLibrary.is(EntityEffectTags.FOOTPRINTS_TINY, type))
            this.sizeBoost = TINY;
        else if (this.tagLibrary.is(EntityEffectTags.FOOTPRINTS_SMALL, type))
            this.sizeBoost = SMALL;
        else
            this.sizeBoost = 1F;
        this.strides.reset();
        this.carried.clear();
    }

    private @Nullable FootprintStyle styleFor(LivingEntity entity) {
        var type = entity.getType();
        if (this.tagLibrary.is(EntityEffectTags.FOOTPRINTS_SQUARE, type))
            return FootprintStyle.SQUARE;
        if (this.tagLibrary.is(EntityEffectTags.FOOTPRINTS_PAW, type))
            return FootprintStyle.PAW;
        if (this.tagLibrary.is(EntityEffectTags.FOOTPRINTS_HOOF, type))
            return FootprintStyle.HORSESHOE;
        if (this.tagLibrary.is(EntityEffectTags.FOOTPRINTS_BIRD, type))
            return FootprintStyle.BIRD;
        return null;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        var entity = info.getEntity();
        if (entity.isPassenger() || entity.isSleeping() || entity.isSpectator()) {
            this.strides.reset();
            return;
        }

        // Wading washes the feet: water takes the place of whatever they carried, snow and mud too (not by replaces)
        if (entity.isInWater())
            this.carried.pickUp(TrackedGround.WATER);

        var step = this.strides.update(entity.getX() - entity.xo, entity.getZ() - entity.zo,
                entity.onGround() && !entity.isInWater(), strideFor(entity.getBbHeight()));
        if (step == StrideTracker.Step.NONE || !this.printsShow(entity))
            return;

        var style = entity instanceof Player ? this.config.footprintStyle : this.style;
        if (style == null)
            return;
        if (step == StrideTracker.Step.BOTH) {
            this.press(entity, style, false);
            this.press(entity, style, true);
        } else {
            this.press(entity, style, this.strides.rightFoot());
        }
    }

    private boolean printsShow(LivingEntity entity) {
        if (entity.isCrouching() || entity.isInWater())
            return false;
        var player = GameUtils.getPlayer().orElse(null);
        if (player != null && entity.isInvisibleTo(player))
            return false;
        return GameUtils.getGameSettings().particles().get() != ParticleStatus.MINIMAL && !IrisCompat.isShaderPackInUse();
    }

    /**
     * Presses a foot's print into the ground under it, if the ground takes prints
     */
    private void press(LivingEntity entity, FootprintStyle style, boolean rightFoot) {
        float width = entity.getBbWidth();
        float yaw = entity.yBodyRot;
        double side = spreadFor(width) * (rightFoot ? 1D : -1D);
        double radians = Math.toRadians(yaw);
        // To the right of the way the body faces
        double x = entity.getX() - Math.cos(radians) * side;
        double z = entity.getZ() - Math.sin(radians) * side;
        double feet = entity.getY();

        // The block the feet sink into (a snow layer, soul sand), else the one they stand on
        var level = entity.level();
        var ground = BlockPos.containing(x, feet, z);
        var state = level.getBlockState(ground);
        double top = this.surface(level, ground, state, feet);
        if (Double.isNaN(top)) {
            ground = BlockPos.containing(x, feet - PROBE, z);
            state = level.getBlockState(ground);
            top = this.surface(level, ground, state, feet);
        }

        float scale = scaleFor(width) * this.sizeBoost;
        boolean pressed = !Double.isNaN(top);
        var carrying = this.config.enableTrackedFootprints ? this.step(level, ground, state) : null;
        if (carrying != null && (!pressed || carrying.showsOnSoftGround())) {
            float strength = this.carriedStrength;
            if (!pressed)
                top = hardSurface(level, ground, state, feet);
            if (!Double.isNaN(top))
                this.footprints.addTracked(level, x, top, z, yaw, scale, style, rightFoot, ground, state, carrying, strength);
        } else if (pressed) {
            this.footprints.add(level, x, top, z, yaw, scale, style, rightFoot, ground, state);
        }
    }

    /**
     * A step on a block: picks up what it gives, or spends a step of what is carried.
     *
     * @return what this step tracks onto it, null if nothing ({@link #carriedStrength} says how much)
     */
    private @Nullable TrackedGround step(Level level, BlockPos pos, BlockState state) {
        var carrying = this.carried.ground();
        var picked = this.pickedUp(level, pos, state);
        if (picked != null && replaces(picked, carrying)) {
            this.carried.pickUp(picked);
            // Soaked ground is mud to look at too; mud itself takes a print as any soft ground does
            if (picked == TrackedGround.MUD && this.blockLibrary.getBlockInfo(state).tracks() == null) {
                this.carriedStrength = 1F;
                return picked;
            }
            return null;
        }
        this.carriedStrength = this.carried.spend();
        return carrying;
    }

    /**
     * Whether ground walked on takes the place of what feet carry: nothing sticks like snow and mud, which stay until
     * they are used up, unless it is mud they walk through or more of the same. Not asked of water, which washes them
     * off (see {@link #tick})
     */
    static boolean replaces(TrackedGround picked, @Nullable TrackedGround carrying) {
        return carrying == null || !carrying.showsOnSoftGround() || picked == carrying || picked == TrackedGround.MUD;
    }

    /**
     * What feet pick up from a block, if anything
     */
    private @Nullable TrackedGround pickedUp(Level level, BlockPos pos, BlockState state) {
        var info = this.blockLibrary.getBlockInfo(state);
        if (info.tracks() != null)
            return info.tracks();
        if (info.tracksMudWhenWet() && this.footprints.wetness(level, pos.above()) >= MUDDY_WETNESS)
            return TrackedGround.MUD;
        return null;
    }

    /**
     * Where a tracked print goes on a block the feet stand on, flat on top; NaN if it isn't, or they aren't on it
     */
    private static double hardSurface(Level level, BlockPos pos, BlockState state, double feet) {
        if (!state.isFaceSturdy(level, pos, Direction.UP))
            return Double.NaN;
        double top = pos.getY() + state.getCollisionShape(level, pos).max(Direction.Axis.Y);
        return Math.abs(top - feet) <= SURFACE_TOLERANCE ? top : Double.NaN;
    }

    /**
     * Where a print goes on a block the feet are on or in, if it takes prints; NaN if it doesn't or they aren't
     */
    private double surface(Level level, BlockPos pos, BlockState state, double feet) {
        if (!this.blockLibrary.getBlockInfo(state).takesFootprints())
            return Double.NaN;
        var collision = state.getCollisionShape(level, pos);
        var drawn = state.getShape(level, pos);
        double y = pos.getY();
        double standsAt = collision.isEmpty() ? y : y + collision.max(Direction.Axis.Y);
        double drawnTop = drawn.isEmpty() ? standsAt : y + drawn.max(Direction.Axis.Y);
        return printHeight(feet, standsAt, drawnTop);
    }

    /**
     * The height of a print, on top of the block as it is drawn: snow layers, soul sand and mud are drawn higher than
     * feet rest in them, and a print where the feet rest would be hidden inside. NaN if the feet aren't on or in it.
     *
     * @param feet     the height of the feet
     * @param standsAt the top of the block as feet collide with it (its bottom, if they don't)
     * @param drawnTop the top of the block as it is drawn
     */
    static double printHeight(double feet, double standsAt, double drawnTop) {
        if (standsAt > feet + SURFACE_TOLERANCE)
            return Double.NaN;
        if (drawnTop < feet - SURFACE_TOLERANCE || drawnTop > feet + MAX_SINK)
            return Double.NaN;
        return drawnTop;
    }

    /**
     * How far it travels between one foot coming down and the other, for a body this tall
     */
    static double strideFor(float height) {
        return Mth.clamp(NORMAL_STRIDE * height / NORMAL_HEIGHT, 0.5D, 2D);
    }

    /**
     * How far each foot is from the middle of a body this wide
     */
    static double spreadFor(float width) {
        return Mth.clamp(width / 3D, 0.06D, 0.5D);
    }

    /**
     * The size of a print from a body this wide, 1 for a player's
     */
    static float scaleFor(float width) {
        return Mth.clamp(Mth.sqrt(width / NORMAL_WIDTH), 0.5F, 1.3F);
    }
}
