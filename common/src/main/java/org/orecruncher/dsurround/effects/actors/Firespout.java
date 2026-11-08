// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
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
public final class Firespout implements IActor {

    // Flames a tick at full strength, the share of them that light their surroundings, smoke puffs, sparks and drops.
    // Each lit flame lights as far as a flame jet's, times its size, so a few alive at once (about 7) light it enough
    private static final float FLAMES = 12F;
    private static final float LIGHT_SHARE = 1F / 48F;
    private static final float SMOKE = 1.5F;
    private static final float SPARKS = 1F;
    private static final float DROPLETS = 0.6F;
    // How far down a column the lava is looked for, in blocks
    static final int SCAN_TOP = 100;
    private static final int SCAN_DEPTH = 96;
    // How far around it the lava has to be open for one to start, in blocks
    private static final int OPEN_LAVA = 8;
    // How fast leaving the lava breaks it up, a share of its strength a tick
    private static final float BREAK_UP = 1F / 30F;
    // Ticks between its sounds, this and up to this many more: each a roar of fire, overlapping the last
    private static final int SOUND_TICKS = 20;
    private static final int SOUND_TICKS_MORE = 15;

    private final VortexPath path;
    @Nullable
    private final ISoundFactory sound;
    private final IAudioPlayer audioPlayer;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private int age;
    private float intact = 1F;
    private boolean breakingUp;
    private int nextSound;
    // The column last looked down, and the lava's surface there, so each column is looked down once
    private int lastX = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;
    private int lastSurface = Integer.MIN_VALUE;

    /**
     * @param age   how far into its life it is already, in ticks, for one a player comes upon part way through
     * @param sound its roar, if the factory is there
     */
    public Firespout(VortexPath path, int age, @Nullable ISoundFactory sound, IAudioPlayer audioPlayer) {
        this.path = path;
        this.age = age;
        this.sound = sound;
        this.audioPlayer = audioPlayer;
        this.nextSound = age;
    }

    /**
     * Whether one can start here: on open lava, with open lava all around
     */
    public static boolean canStart(Level level, double x, double z) {
        var probe = new BlockPos.MutableBlockPos();
        int surface = lavaSurface(level, probe, Mth.floor(x), Mth.floor(z));
        if (surface == Integer.MIN_VALUE)
            return false;
        for (int i = 0; i < 8; i++) {
            var angle = i * Mth.TWO_PI / 8F;
            if (lavaSurface(level, probe, Mth.floor(x + Mth.cos(angle) * OPEN_LAVA), Mth.floor(z + Mth.sin(angle) * OPEN_LAVA)) != surface)
                return false;
        }
        return true;
    }

    /**
     * The height of the first lava with air over it, looking down a column from {@link #SCAN_TOP}, or MIN_VALUE if
     * there is none or its chunk isn't loaded
     */
    static int lavaSurface(Level level, BlockPos.MutableBlockPos probe, int x, int z) {
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
    public double x() {
        return this.path.x(this.age);
    }

    @Override
    public double z() {
        return this.path.z(this.age);
    }

    @Override
    public boolean tick(ClientLevel level, float particles) {
        if (++this.age >= this.path.lifetime() || this.intact <= 0F)
            return false;

        int bx = Mth.floor(this.x()), bz = Mth.floor(this.z());
        if (!level.hasChunkAt(this.probe.set(bx, 0, bz)))
            return true;
        if (bx != this.lastX || bz != this.lastZ) {
            var surface = lavaSurface(level, this.probe, bx, bz);
            // Off the lava, or onto a lake at another height: it is a wall or a fall, either way the end of it
            if (surface == Integer.MIN_VALUE || (this.lastSurface != Integer.MIN_VALUE && surface != this.lastSurface))
                this.breakingUp = true;
            else
                this.lastSurface = surface;
            this.lastX = bx;
            this.lastZ = bz;
        }
        if (this.lastSurface == Integer.MIN_VALUE)
            return false;
        if (this.breakingUp)
            this.intact -= BREAK_UP;
        // The top of the lava block, where its surface is drawn
        double footY = this.lastSurface + 0.9D;

        var strength = this.path.strength(this.age) * Math.max(0F, this.intact);
        if (strength > 0F && this.age >= this.nextSound) {
            this.nextSound = this.age + SOUND_TICKS + level.getRandom().nextInt(SOUND_TICKS_MORE + 1);
            if (this.sound != null)
                this.audioPlayer.play(this.sound.createAtLocation(this.x(), footY + 2D, this.z(), strength));
        }
        if (particles <= 0F || strength <= 0F)
            return true;

        var random = level.getRandom();
        var engine = GameUtils.getParticleManager();
        for (int i = GroundVortex.count(FLAMES * strength * particles, random.nextFloat()); i > 0; i--)
            engine.add(FirespoutFlame.create(level, this.path, this.age, footY, random.nextFloat() < LIGHT_SHARE));
        for (int i = GroundVortex.count(SMOKE * strength * particles, random.nextFloat()); i > 0; i--) {
            // From the top of the flames, spreading as it goes
            var h = this.path.height() * (0.6F + random.nextFloat() * 0.4F);
            var r = this.path.radius(h) * random.nextFloat();
            var angle = random.nextFloat() * Mth.TWO_PI;
            engine.add(SmokePuff.create(level, this.path.axisX(h, this.age) + Mth.cos(angle) * r, footY + h,
                    this.path.axisZ(h, this.age) + Mth.sin(angle) * r, 0.08D, 2F + random.nextFloat()));
        }
        for (int i = GroundVortex.count(SPARKS * strength * particles, random.nextFloat()); i > 0; i--) {
            // Flung off the funnel the way it turns
            var h = random.nextFloat() * this.path.height() * 0.7F;
            var r = this.path.radius(h);
            var angle = random.nextFloat() * Mth.TWO_PI;
            var spin = this.path.spin(r);
            var tangent = angle + Mth.HALF_PI * Math.signum(spin);
            var speed = Math.abs(spin) * r;
            engine.add(FlameSpark.create(level, this.path.axisX(h, this.age) + Mth.cos(angle) * r, footY + h,
                    this.path.axisZ(h, this.age) + Mth.sin(angle) * r, Mth.cos(tangent) * speed, 0.2D, Mth.sin(tangent) * speed));
        }
        for (int i = GroundVortex.count(DROPLETS * strength * particles, random.nextFloat()); i > 0; i--) {
            // Thrown up and out from its foot
            var angle = random.nextFloat() * Mth.TWO_PI;
            var r = this.path.radius(0F);
            var out = 0.1D + random.nextFloat() * 0.15D;
            engine.add(LavaDroplet.create(level, this.x() + Mth.cos(angle) * r, footY + 0.2D, this.z() + Mth.sin(angle) * r,
                    Mth.cos(angle) * out, 0.35D + random.nextFloat() * 0.25D, Mth.sin(angle) * out));
        }
        return true;
    }
}
