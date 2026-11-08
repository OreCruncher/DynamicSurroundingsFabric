// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.ActorKind;
import org.orecruncher.dsurround.effects.particles.FirespoutFlame;
import org.orecruncher.dsurround.effects.particles.FlameSpark;
import org.orecruncher.dsurround.effects.particles.LavaDroplet;
import org.orecruncher.dsurround.effects.particles.SmokePuff;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.ISoundFactory;

/**
 * A firespout: a whirl of fire over a lava lake (see {@link VortexPath#FIRESPOUT}), flames whirled up its funnel
 * ({@link FirespoutFlame}), cooling from white-yellow to red as they climb, some lighting what is around, with smoke
 * spreading from its top, sparks flung out ({@link FlameSpark}) and lava thrown up from its foot ({@link LavaDroplet}),
 * and the roar of fire following it. It starts over open lava; going onto anything else breaks it up.
 * <p>
 * The Nether's bedrock roof tops every column's heightmap, so the lava is found by looking down each column from a
 * fixed height for the first lava with air over it: the same for every player, wherever each stands.
 */
public final class Firespout extends VortexActor {

    // Broken up off the lava in a second and a half; a roar of fire every one to one and three quarter seconds, from
    // a little above the lava
    private static final Traits TRAITS = new Traits(1F / 30F, 20, 15, 2D);
    // Flames a tick at full strength, the share of them that light their surroundings, smoke puffs, sparks and drops.
    // Each lit flame lights as far as a flame jet's, times its size, so a few alive at once (about 7) light it enough
    private static final float FLAMES = 12F;
    private static final float LIGHT_SHARE = 1F / 48F;
    private static final float SMOKE = 1.5F;
    private static final float SPARKS = 1F;
    private static final float DROPLETS = 0.6F;
    // How far down a column the lava is looked for, in blocks
    private static final int SCAN_TOP = 100;
    private static final int SCAN_DEPTH = 96;
    // How far around it the lava has to be open for one to start, in blocks
    private static final int OPEN_LAVA = 8;

    // The column last looked down, and the lava's surface there, so each column is looked down once
    private int lastX = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;
    private int lastSurface = Integer.MIN_VALUE;

    /**
     * @param age   how far into its life it is already, in ticks, for one a player comes upon part way through
     * @param sound its roar, if the factory is there
     */
    public Firespout(VortexPath path, int age, @Nullable ISoundFactory sound, IAudioPlayer audioPlayer) {
        super(TRAITS, path, age, sound, audioPlayer);
    }

    /**
     * Whether one can start here: on open lava, with open lava all around
     */
    public static boolean canStart(Level level, double x, double z) {
        var probe = new BlockPos.MutableBlockPos();
        int surface = lavaSurface(level, probe, Mth.floor(x), Mth.floor(z));
        return surface != Integer.MIN_VALUE && allAround(x, z, OPEN_LAVA, (cx, cz) -> lavaSurface(level, probe, cx, cz) == surface);
    }

    /**
     * The height of the lava's surface under a place, as {@link #lavaSurface} finds it
     */
    public static int lavaSurface(Level level, int x, int z) {
        return lavaSurface(level, new BlockPos.MutableBlockPos(), x, z);
    }

    /**
     * The height of the first lava with air over it, looking down a column from {@link #SCAN_TOP}, or MIN_VALUE if
     * there is none or its chunk isn't loaded
     */
    private static int lavaSurface(Level level, BlockPos.MutableBlockPos probe, int x, int z) {
        if (!level.hasChunkAt(probe.set(x, 0, z)))
            return Integer.MIN_VALUE;
        boolean airAbove = false;
        int bottom = Math.max(level.getMinY(), SCAN_TOP - SCAN_DEPTH);
        for (int y = SCAN_TOP; y >= bottom; y--) {
            var state = level.getBlockState(probe.set(x, y, z));
            if (airAbove && state.getFluidState().is(FluidTags.LAVA) && state.getFluidState().isSource())
                return y;
            airAbove = state.isAir();
        }
        return Integer.MIN_VALUE;
    }

    @Override
    public ActorKind kind() {
        return ActorKind.FIRESPOUT;
    }

    @Override
    protected double foot(ClientLevel level, int x, int z) {
        if (x != this.lastX || z != this.lastZ) {
            var surface = lavaSurface(level, this.probe, x, z);
            // Off the lava, or onto a lake at another height: it is a wall or a fall, either way the end of it
            if (surface == Integer.MIN_VALUE || (this.lastSurface != Integer.MIN_VALUE && surface != this.lastSurface))
                this.breakUp();
            else
                this.lastSurface = surface;
            this.lastX = x;
            this.lastZ = z;
        }
        // The top of the lava block, where its surface is drawn; never on lava at all, it ends at once
        return this.lastSurface == Integer.MIN_VALUE ? Double.NaN : this.lastSurface + 0.9D;
    }

    @Override
    protected void show(ClientLevel level, RandomSource random, double footY, float strength, float particles) {
        var engine = GameUtils.getParticleManager();
        for (int i = count(FLAMES * strength * particles, random.nextFloat()); i > 0; i--)
            engine.add(FirespoutFlame.create(level, this.path, this.age, footY, random.nextFloat() < LIGHT_SHARE));
        for (int i = count(SMOKE * strength * particles, random.nextFloat()); i > 0; i--) {
            // From the top of the flames, spreading as it goes
            var h = this.path.height() * (0.6F + random.nextFloat() * 0.4F);
            var r = this.path.radius(h) * random.nextFloat();
            var angle = random.nextFloat() * Mth.TWO_PI;
            engine.add(SmokePuff.create(level, this.path.axisX(h, this.age) + Mth.cos(angle) * r, footY + h,
                    this.path.axisZ(h, this.age) + Mth.sin(angle) * r, 0.08D, 2F + random.nextFloat()));
        }
        for (int i = count(SPARKS * strength * particles, random.nextFloat()); i > 0; i--) {
            // Flung off the funnel the way it turns
            var h = random.nextFloat() * this.path.height() * 0.7F;
            var r = this.path.radius(h);
            var angle = random.nextFloat() * Mth.TWO_PI;
            var tangent = this.path.tangent(angle, r);
            var speed = Math.abs(this.path.spin(r)) * r;
            engine.add(FlameSpark.create(level, this.path.axisX(h, this.age) + Mth.cos(angle) * r, footY + h,
                    this.path.axisZ(h, this.age) + Mth.sin(angle) * r, Mth.cos(tangent) * speed, 0.2D, Mth.sin(tangent) * speed));
        }
        for (int i = count(DROPLETS * strength * particles, random.nextFloat()); i > 0; i--) {
            // Thrown up and out from its foot
            var angle = random.nextFloat() * Mth.TWO_PI;
            var r = this.path.radius(0F);
            var out = 0.1D + random.nextFloat() * 0.15D;
            engine.add(LavaDroplet.create(level, this.x() + Mth.cos(angle) * r, footY + 0.2D, this.z() + Mth.sin(angle) * r,
                    Mth.cos(angle) * out, 0.35D + random.nextFloat() * 0.25D, Mth.sin(angle) * out));
        }
    }
}
