// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.ISoundFactory;

/**
 * What every whirling actor does alike, going its way along its {@link VortexPath}: living out its lifetime, finding
 * what is under its foot each tick, breaking up over a second or two once it can't go on, and playing its sound now and
 * then, overlapping the last. What it stands on and what it shows are each kind's own.
 */
public abstract class VortexActor implements IActor {

    /**
     * How a kind breaks up and sounds
     *
     * @param breakUp        how fast it breaks up once it can't go on, a share of its strength a tick
     * @param soundTicks     ticks between its sounds, this and up to {@code soundTicksMore} more
     * @param soundTicksMore see {@code soundTicks}
     * @param soundHeight    how far above its foot its sound comes from, in blocks
     */
    protected record Traits(float breakUp, int soundTicks, int soundTicksMore, double soundHeight) {
    }

    // The ways out from its foot that are looked along to see whether it has room to start
    private static final int AROUND = 8;

    protected final VortexPath path;
    protected final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private final Traits traits;
    @Nullable
    private final ISoundFactory sound;
    private final IAudioPlayer audioPlayer;
    protected int age;
    // 1 until it can't go on, then down to 0
    private float intact = 1F;
    private boolean breakingUp;
    private int nextSound;

    /**
     * @param age   how far into its life it is already, in ticks, for one a player comes upon part way through
     * @param sound its sound, if the factory is there
     */
    protected VortexActor(Traits traits, VortexPath path, int age, @Nullable ISoundFactory sound, IAudioPlayer audioPlayer) {
        this.traits = traits;
        this.path = path;
        this.age = age;
        this.sound = sound;
        this.audioPlayer = audioPlayer;
        this.nextSound = age;
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
    public final boolean tick(ClientLevel level, float particles) {
        if (++this.age >= this.path.lifetime() || this.intact <= 0F)
            return false;

        int bx = Mth.floor(this.x()), bz = Mth.floor(this.z());
        if (!level.hasChunkAt(this.probe.set(bx, 0, bz)))
            return true;
        double footY = this.foot(level, bx, bz);
        if (Double.isNaN(footY))
            return false;
        if (this.breakingUp)
            this.intact -= this.traits.breakUp();

        var strength = this.path.strength(this.age) * Math.max(0F, this.intact);
        if (strength > 0F && this.age >= this.nextSound) {
            this.nextSound = this.age + this.traits.soundTicks() + level.getRandom().nextInt(this.traits.soundTicksMore() + 1);
            if (this.sound != null)
                this.audioPlayer.play(this.sound.createAtLocation(this.x(), footY + this.traits.soundHeight(), this.z(), strength));
        }
        if (particles > 0F && strength > 0F)
            this.show(level, level.getRandom(), footY, strength, particles);
        return true;
    }

    /**
     * Finds what is under its foot this tick, its chunk loaded, calling {@link #breakUp()} where it can't go on
     *
     * @return the height of its foot, or NaN to end it at once
     */
    protected abstract double foot(ClientLevel level, int x, int z);

    /**
     * Makes what it shows this tick
     *
     * @param strength  how strong it is now, 0 to 1
     * @param particles how many particles it may make, 0 to 1, from the game's Particles option
     */
    protected abstract void show(ClientLevel level, RandomSource random, double footY, float strength, float particles);

    /**
     * It can't go on: it dies away over the next ticks
     */
    protected void breakUp() {
        this.breakingUp = true;
    }

    protected boolean isBreakingUp() {
        return this.breakingUp;
    }

    @Override
    public boolean isEnding() {
        return this.breakingUp;
    }

    /**
     * A column, by its block coordinates
     */
    @FunctionalInterface
    protected interface ColumnTest {
        boolean test(int x, int z);
    }

    /**
     * Whether every column a distance out from a place, in each of several ways, passes a test: room for one to start
     */
    protected static boolean allAround(double x, double z, int reach, ColumnTest test) {
        for (int i = 0; i < AROUND; i++) {
            var angle = i * Mth.TWO_PI / AROUND;
            if (!test.test(Mth.floor(x + Mth.cos(angle) * reach), Mth.floor(z + Mth.sin(angle) * reach)))
                return false;
        }
        return true;
    }

    /**
     * A whole number of things for a fractional count: the whole part, and one more as often as the fraction says
     */
    static int count(float expected, float roll) {
        int whole = (int) expected;
        return whole + (roll < expected - whole ? 1 : 0);
    }
}
