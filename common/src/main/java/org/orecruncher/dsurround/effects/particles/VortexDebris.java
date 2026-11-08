// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.effects.actors.VortexPath;

/**
 * A bit of what a vortex picks up (a fleck of sand, a scrap of dry grass or seagrass), drawn from the block it came from as
 * the game's block particles are: whirled up the inside of the funnel to a height, then thrown out to fall. The world
 * itself is never changed.
 * <p>
 * The game's block particles are flecks, a quarter of a particle's usual size and a quarter of the block's texture
 * across: right for a grain of sand, but of a plant's texture, mostly see-through, that is a pixel or two or nothing. A
 * scrap of a plant is bigger, and shows more of the texture, so a twig or a few blades can be made out.
 */
public class VortexDebris extends TerrainParticle {

    // How far in from the funnel's edge it goes around, as a share of the radius
    private static final float INSIDE = 0.7F;
    // How fast it climbs, in blocks a tick, and the share of the devil's height it can be thrown out from, at most
    private static final float MIN_RISE = 0.08F;
    private static final float MAX_RISE = 0.18F;
    private static final float MAX_RELEASE = 0.7F;
    // And the highest it is thrown from, in blocks: a waterspout reaches the clouds, but what it lifts from the sea
    // falls back long before
    private static final float MAX_RELEASE_BLOCKS = 14F;
    // How fast it leaves the funnel when thrown out, in blocks a tick, and how long it has to fall, in ticks
    private static final double THROW = 0.25D;
    private static final int FALL_TICKS = 60;
    // A plant's scrap: how much bigger than a fleck, and how much of the texture across it shows
    private static final float SCRAP_SCALE = 3.5F;
    private static final float SCRAP_TEXTURE = 0.5F;

    private final VortexPath path;
    private final int devilAge;
    private final double groundY;
    private final float rise;
    private final float release;
    private float height;
    private float angle;
    private boolean thrown;
    // The part of the texture a scrap shows, from its corner; a fleck shows the game's quarter
    private final boolean scrap;
    private final float scrapU;
    private final float scrapV;

    /**
     * A piece of a block, picked up by a dust devil
     *
     * @param devilAge the devil's age now, in ticks
     * @param groundY  the ground under the devil
     * @param state    what it is a piece of
     * @param pos      where that block is, for its tint
     * @param scrap    a scrap of a plant, rather than a fleck of the ground
     */
    public static Particle create(ClientLevel level, VortexPath path, int devilAge, double groundY, BlockState state, BlockPos pos, boolean scrap) {
        return new VortexDebris(level, path, devilAge, groundY, state, pos, scrap);
    }

    protected VortexDebris(ClientLevel level, VortexPath path, int devilAge, double groundY, BlockState state, BlockPos pos, boolean scrap) {
        super(level, path.x(devilAge), groundY, path.z(devilAge), 0D, 0D, 0D, state, pos);
        var random = level.getRandom();
        this.path = path;
        this.devilAge = devilAge;
        this.groundY = groundY;
        this.angle = random.nextFloat() * Mth.TWO_PI;
        this.rise = Mth.lerp(random.nextFloat(), MIN_RISE, MAX_RISE);
        this.release = Math.min(path.height() * MAX_RELEASE, MAX_RELEASE_BLOCKS) * (0.3F + random.nextFloat() * 0.7F);
        this.lifetime = Mth.ceil(this.release / this.rise) + FALL_TICKS;
        this.hasPhysics = false;
        this.gravity = 0F;
        this.friction = 1F;
        this.xd = this.yd = this.zd = 0D;
        this.scrap = scrap;
        this.scrapU = random.nextFloat() * (1F - SCRAP_TEXTURE);
        this.scrapV = random.nextFloat() * (1F - SCRAP_TEXTURE);
        if (scrap)
            this.quadSize *= SCRAP_SCALE;
    }

    @Override
    protected float getU0() {
        return this.scrap ? this.sprite.getU(this.scrapU) : super.getU0();
    }

    @Override
    protected float getU1() {
        return this.scrap ? this.sprite.getU(this.scrapU + SCRAP_TEXTURE) : super.getU1();
    }

    @Override
    protected float getV0() {
        return this.scrap ? this.sprite.getV(this.scrapV) : super.getV0();
    }

    @Override
    protected float getV1() {
        return this.scrap ? this.sprite.getV(this.scrapV + SCRAP_TEXTURE) : super.getV1();
    }

    @Override
    public void tick() {
        if (!this.thrown) {
            this.height += this.rise;
            var radius = this.path.radius(this.height) * INSIDE;
            this.angle += this.path.spin(radius);
            var devil = this.devilAge + this.age + 1;
            var tx = this.path.axisX(this.height, devil) + Mth.cos(this.angle) * radius;
            var tz = this.path.axisZ(this.height, devil) + Mth.sin(this.angle) * radius;
            if (this.height >= this.release) {
                // Out of the funnel the way it was going around, and down
                this.thrown = true;
                this.hasPhysics = true;
                this.gravity = 1F;
                this.friction = 0.98F;
                var tangent = this.angle + Mth.HALF_PI * Math.signum(this.path.spin(radius));
                this.xd = Mth.cos(tangent) * THROW;
                this.zd = Mth.sin(tangent) * THROW;
                this.yd = 0D;
            } else {
                this.xd = tx - this.x;
                this.yd = this.groundY + this.height - this.y;
                this.zd = tz - this.z;
            }
        }
        super.tick();
    }
}
