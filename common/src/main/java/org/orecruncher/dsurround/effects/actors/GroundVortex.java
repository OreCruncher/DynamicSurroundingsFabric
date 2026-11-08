// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.ActorKind;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.particles.VortexDebris;
import org.orecruncher.dsurround.effects.particles.VortexPuff;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.tags.BlockEffectTags;

/**
 * A whirl over the ground, going its way (see {@link VortexPath}): a dust devil over dry ground, a snowspout over snow.
 * Drawn as puffs rising and turning up its funnel ({@link VortexPuff}), a skirt around its foot, and bits of what it
 * picks up ({@link VortexDebris}), with the game's own wind over sand ({@code block.sand.idle}) following it. It starts
 * on ground its {@link Style} says it lifts; crossing water, or running into a building, a cliff or a tree, breaks it
 * up. A dust devil's dust is the ground's: crossing onto other dusty ground (red sand, coarse dirt, terracotta), it
 * takes on that color over a second or two, while over ground that gives no dust it keeps what it carries. A
 * snowspout's is always white, with snowflakes whirling in it.
 */
public final class GroundVortex implements IActor {

    /**
     * What a kind of ground vortex is like
     *
     * @param kind        its kind
     * @param sources     what it starts on and lifts as flecks
     * @param scraps      what it lifts as scraps, standing on the ground (dry plants), or null for nothing
     * @param groundColor whether it takes the color of the ground under it, rather than keeping what it started with
     * @param shade       how much darker what it lifts is in the air, 0 to 1 (1 as it is)
     * @param grey        how much greyer, 0 to 1 (0 as it is)
     * @param snowflakes  the game's snowflakes it whirls a tick, at full strength
     */
    public record Style(ActorKind kind, TagKey<Block> sources, @Nullable TagKey<Block> scraps, boolean groundColor,
                        float shade, float grey, float snowflakes) {
    }

    // Dust is darker in the air than on the ground, and greyer, as it scatters the light
    public static final Style DUST_DEVIL = new Style(ActorKind.DUST_DEVIL, BlockEffectTags.DUST_SOURCES,
            BlockEffectTags.DUST_DEVIL_DEBRIS, true, 0.72F, 0.35F, 0F);
    // Powder snow stays bright
    public static final Style SNOWSPOUT = new Style(ActorKind.SNOWSPOUT, BlockEffectTags.SNOWSPOUT_SOURCES, null,
            false, 0.95F, 0F, 1.5F);
    // White, for a snowspout
    public static final int SNOW_COLOR = 0xFFFFFF;

    // Puffs a tick at full strength, up the funnel and around the foot, and the chance a tick of picking something up
    private static final float FUNNEL_PUFFS = 5F;
    private static final float SKIRT_PUFFS = 2F;
    private static final float DEBRIS_CHANCE = 0.5F;
    // How far from its axis it picks things up, in blocks
    private static final float PICK_UP_REACH = 1.5F;
    // How fast water or a wall breaks it up, a share of its strength a tick
    private static final float BREAK_UP = 1F / 30F;
    // How far the ground can rise under its foot in a step and it still climbs it, in blocks: more is a wall
    private static final int CLIMB = 2;
    // Ticks between its sounds, this and up to this many more: each one a gust of wind, overlapping the last
    private static final int SOUND_TICKS = 30;
    private static final int SOUND_TICKS_MORE = 20;
    // How much of the way to the ground's color its dust goes in a tick
    static final float COLOR_BLEND = 0.04F;

    private final Style style;
    private final VortexPath path;
    private final ITagLibrary tags;
    // The dust's color now, as RGB; taken straight from the ground the first time it is over dusty ground
    private int color;
    private boolean colorFromGround;
    @Nullable
    private final ISoundFactory sound;
    private final IAudioPlayer audioPlayer;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private int age;
    // 1 until it crosses water or meets a wall, then down to 0
    private float intact = 1F;
    private boolean breakingUp;
    // The ground under its foot last tick, to see a wall coming; none yet
    private int lastGroundY = Integer.MIN_VALUE;
    private int nextSound;

    /**
     * @param age   how far into its life it is already, in ticks, for one a player comes upon part way through
     * @param color what it lifts' color to start with, as RGB
     * @param sound its wind, if the factory is there
     */
    public GroundVortex(Style style, VortexPath path, int age, int color, ITagLibrary tags, @Nullable ISoundFactory sound, IAudioPlayer audioPlayer) {
        this.style = style;
        this.path = path;
        this.age = age;
        this.color = color;
        this.tags = tags;
        this.sound = sound;
        this.audioPlayer = audioPlayer;
        this.nextSound = age;
    }

    /**
     * Whether one can start here: on ground it lifts, open to the sky
     */
    public static boolean canStart(Style style, Level level, ITagLibrary tags, double x, double z) {
        var pos = BlockPos.containing(x, 0, z);
        if (!level.hasChunkAt(pos))
            return false;
        var top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        var ground = level.getBlockState(pos.atY(top - 1));
        // A thin layer of snow lies on the ground, above the heightmap, as nothing blocks movement there
        return (ground.getFluidState().isEmpty() && tags.is(style.sources(), ground)) || tags.is(style.sources(), level.getBlockState(pos.atY(top)));
    }

    @Override
    public ActorKind kind() {
        return this.style.kind();
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
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        if (!level.getFluidState(this.probe.set(bx, groundY - 1, bz)).isEmpty()
                || (this.lastGroundY != Integer.MIN_VALUE && groundY - this.lastGroundY > CLIMB))
            this.breakingUp = true;
        // Against a wall it stays at the foot of it rather than climbing onto the roof
        if (!this.breakingUp || this.lastGroundY == Integer.MIN_VALUE)
            this.lastGroundY = groundY;
        else
            groundY = Math.min(groundY, this.lastGroundY);
        if (this.breakingUp)
            this.intact -= BREAK_UP;
        var ground = level.getBlockState(this.probe.set(bx, groundY - 1, bz));
        if (this.style.groundColor() && this.tags.is(this.style.sources(), ground)) {
            var dust = ground.getMapColor(level, this.probe).col;
            this.color = this.colorFromGround ? blend(this.color, dust, COLOR_BLEND) : dust;
            this.colorFromGround = true;
        }

        var strength = this.path.strength(this.age) * Math.max(0F, this.intact);
        if (strength > 0F && this.age >= this.nextSound) {
            this.nextSound = this.age + SOUND_TICKS + level.getRandom().nextInt(SOUND_TICKS_MORE + 1);
            if (this.sound != null)
                this.audioPlayer.play(this.sound.createAtLocation(this.x(), groundY + 1D, this.z(), strength));
        }
        if (particles <= 0F || strength <= 0F)
            return true;

        var random = level.getRandom();
        var engine = GameUtils.getParticleManager();
        for (int i = count(FUNNEL_PUFFS * strength * particles, random.nextFloat()); i > 0; i--)
            engine.add(VortexPuff.create(level, this.path, this.age, groundY, this.color, this.style.shade(), this.style.grey(), strength, false));
        for (int i = count(SKIRT_PUFFS * strength * particles, random.nextFloat()); i > 0; i--)
            engine.add(VortexPuff.create(level, this.path, this.age, groundY, this.color, this.style.shade(), this.style.grey(), strength, true));
        for (int i = count(this.style.snowflakes() * strength * particles, random.nextFloat()); i > 0; i--) {
            // Somewhere up the funnel, flung around it
            var h = random.nextFloat() * this.path.height();
            var angle = random.nextFloat() * Mth.TWO_PI;
            var r = this.path.radius(h);
            var tangent = angle + Mth.HALF_PI * Math.signum(this.path.spin(r));
            var speed = this.path.spin(r) * r;
            level.addParticle(ParticleTypes.SNOWFLAKE, this.path.axisX(h, this.age) + Mth.cos(angle) * r, groundY + h,
                    this.path.axisZ(h, this.age) + Mth.sin(angle) * r, Mth.cos(tangent) * Math.abs(speed), 0.05D, Mth.sin(tangent) * Math.abs(speed));
        }
        if (random.nextFloat() < DEBRIS_CHANCE * strength * particles)
            this.pickUp(level, random.nextFloat(), random.nextFloat());
        return true;
    }

    /**
     * Picks up a bit of a plant, or of the ground it lifts, near its foot
     */
    private void pickUp(ClientLevel level, float u, float v) {
        int x = Mth.floor(this.x() + (u * 2F - 1F) * PICK_UP_REACH);
        int z = Mth.floor(this.z() + (v * 2F - 1F) * PICK_UP_REACH);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        // A plant stands on the ground, which the heightmap stops under, as nothing blocks movement there
        var plant = level.getBlockState(this.probe.set(x, y, z));
        boolean scrap = this.style.scraps() != null && this.tags.is(this.style.scraps(), plant);
        // Or a thin layer of snow lying on the ground
        if (!scrap && !this.tags.is(this.style.sources(), plant)) {
            this.probe.setY(y - 1);
            plant = level.getBlockState(this.probe);
            if (!this.tags.is(this.style.sources(), plant))
                return;
        }
        GameUtils.getParticleManager().add(VortexDebris.create(level, this.path, this.age, y, plant, this.probe.immutable(), scrap));
    }

    /**
     * A color part of the way to another, both RGB
     */
    static int blend(int from, int to, float share) {
        int r = Math.round(Mth.lerp(share, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(share, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(share, from & 0xFF, to & 0xFF));
        // Rounding could leave it a step short for good: one step on, then
        if (r == ((from >> 16) & 0xFF)) r += Integer.signum(((to >> 16) & 0xFF) - r);
        if (g == ((from >> 8) & 0xFF)) g += Integer.signum(((to >> 8) & 0xFF) - g);
        if (b == (from & 0xFF)) b += Integer.signum((to & 0xFF) - b);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * A whole number of things for a fractional count: the whole part, and one more as often as the fraction says
     */
    static int count(float expected, float roll) {
        int whole = (int) expected;
        return whole + (roll < expected - whole ? 1 : 0);
    }
}
