// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.ActorKind;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.particles.VortexDebris;
import org.orecruncher.dsurround.effects.particles.WaterspoutMist;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.tags.BlockEffectTags;
import org.orecruncher.dsurround.tags.FluidTags;

/**
 * A waterspout: a thin funnel of cloud from the water up to the clouds, leaning far behind its foot and swaying (see
 * {@link VortexPath#WATERSPOUT}), with a ring of spray thrown out where it meets the water ({@link WaterspoutMist}),
 * splashes, seagrass and kelp torn up from shallow water ({@link VortexDebris}), and a rush of water following it. It
 * starts over open water; coming ashore, or onto ice, breaks it up.
 */
public final class Waterspout extends VortexActor {

    // Broken up by land or ice in two seconds; a rush of water every two to three and a half seconds
    private static final Traits TRAITS = new Traits(1F / 40F, 40, 30, 1D);
    // Puffs a tick at full strength, in the funnel and in the spray, splashes, and the chance a tick of tearing
    // something up
    private static final float FUNNEL_PUFFS = 10F;
    private static final float SPRAY_PUFFS = 3F;
    private static final float SPLASHES = 2F;
    private static final float DEBRIS_CHANCE = 0.3F;
    // How far from its axis it tears things up, in blocks, and how deep it reaches
    private static final float PICK_UP_REACH = 2F;
    private static final int PICK_UP_DEPTH = 5;
    // How far around it the water has to be open for one to start, in blocks
    private static final int OPEN_WATER = 12;
    // How tall it can be, in blocks, whatever the clouds' height
    static final float MIN_HEIGHT = 40F;
    static final float MAX_HEIGHT = 160F;

    private final ITagLibrary tags;

    /**
     * @param age   how far into its life it is already, in ticks, for one a player comes upon part way through
     * @param sound its rush of water, if the factory is there
     */
    public Waterspout(VortexPath path, int age, ITagLibrary tags, @Nullable ISoundFactory sound, IAudioPlayer audioPlayer) {
        super(TRAITS, path, age, sound, audioPlayer);
        this.tags = tags;
    }

    /**
     * How tall one is with its foot on water at a height, reaching up to the clouds
     */
    public static float height(double footY, int cloudHeight) {
        return Mth.clamp((float) (cloudHeight - footY), MIN_HEIGHT, MAX_HEIGHT);
    }

    /**
     * Whether one can start here: on open water, with open water all around
     */
    public static boolean canStart(Level level, ITagLibrary tags, double x, double z) {
        var probe = new BlockPos.MutableBlockPos();
        int surface = surface(level, probe, Mth.floor(x), Mth.floor(z));
        if (surface == Integer.MIN_VALUE || !isOpenWater(level, tags, probe.set(Mth.floor(x), surface, Mth.floor(z))))
            return false;
        return allAround(x, z, OPEN_WATER, (cx, cz) -> surface(level, probe, cx, cz) == surface && isOpenWater(level, tags, probe.set(cx, surface, cz)));
    }

    // The top block of a column, or MIN_VALUE if its chunk isn't loaded
    private static int surface(Level level, BlockPos.MutableBlockPos probe, int x, int z) {
        if (!level.hasChunkAt(probe.set(x, 0, z)))
            return Integer.MIN_VALUE;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
    }

    private static boolean isOpenWater(Level level, ITagLibrary tags, BlockPos pos) {
        var fluid = level.getFluidState(pos);
        return fluid.isSource() && tags.is(FluidTags.WATER_RIPPLES, fluid);
    }

    @Override
    public ActorKind kind() {
        return ActorKind.WATERSPOUT;
    }

    @Override
    protected double foot(ClientLevel level, int x, int z) {
        int surface = surface(level, this.probe, x, z);
        if (!isOpenWater(level, this.tags, this.probe.set(x, surface, z)))
            this.breakUp();
        // Just under the top of the water block, where the surface is drawn
        return surface + 0.9D;
    }

    @Override
    protected void show(ClientLevel level, RandomSource random, double footY, float strength, float particles) {
        var engine = GameUtils.getParticleManager();
        for (int i = count(FUNNEL_PUFFS * strength * particles, random.nextFloat()); i > 0; i--)
            engine.add(WaterspoutMist.create(level, this.path, this.age, footY, strength, false, random.nextFloat()));
        for (int i = count(SPRAY_PUFFS * strength * particles, random.nextFloat()); i > 0; i--)
            engine.add(WaterspoutMist.create(level, this.path, this.age, footY, strength, true, 0F));
        for (int i = count(SPLASHES * strength * particles, random.nextFloat()); i > 0; i--) {
            var angle = random.nextFloat() * Mth.TWO_PI;
            var r = this.path.radius(0F) + random.nextFloat() * 2F;
            level.addParticle(ParticleTypes.SPLASH, this.x() + Mth.cos(angle) * r, footY + 0.1D, this.z() + Mth.sin(angle) * r, 0D, 0.1D, 0D);
        }
        if (random.nextFloat() < DEBRIS_CHANCE * strength * particles)
            this.tearUp(level, footY, Mth.floor(footY), random.nextFloat(), random.nextFloat());
    }

    /**
     * Tears up seagrass or kelp from shallow water near its foot
     */
    private void tearUp(ClientLevel level, double footY, int surface, float u, float v) {
        int x = Mth.floor(this.x() + (u * 2F - 1F) * PICK_UP_REACH);
        int z = Mth.floor(this.z() + (v * 2F - 1F) * PICK_UP_REACH);
        for (int y = surface; y > surface - PICK_UP_DEPTH; y--) {
            var state = level.getBlockState(this.probe.set(x, y, z));
            if (this.tags.is(BlockEffectTags.WATERSPOUT_DEBRIS, state)) {
                GameUtils.getParticleManager().add(VortexDebris.create(level, this.path, this.age, footY, state, this.probe.immutable(), true));
                return;
            }
            if (state.getFluidState().isEmpty())
                return;
        }
    }
}
