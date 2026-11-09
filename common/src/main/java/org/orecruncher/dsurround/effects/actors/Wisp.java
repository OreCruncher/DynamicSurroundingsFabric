// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.orecruncher.dsurround.config.ActorKind;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.particles.WispGlow;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.tags.BlockEffectTags;

import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * A will-o'-the-wisp: a few pale lights drifting low over a marsh at night, or soul-fire lights over a soul sand valley,
 * keeping to their haunts (see {@link WispPath}). As the old stories have it, they won't be caught: come close and they
 * go out, to show again at another of their haunts, the one farthest from you. That is the one thing about a wisp that
 * isn't the same for every player, as it is only what each player sees: where it would be is still the same for all.
 * <p>
 * Each light is a {@link WispGlow} that lives as long as the wisp, moved by it each tick; it starts where the ground
 * under it is one it haunts ({@link BlockEffectTags#WISP_GROUNDS}: water, mud, soul sand).
 */
public final class Wisp implements IActor {

    /**
     * The color of a kind of wisp's light
     */
    public record Glow(float red, float green, float blue) {
    }

    // A marsh light's cold blue-green, and soul fire's turquoise
    public static final Glow MARSH = new Glow(0.55F, 1F, 0.8F);
    public static final Glow SOUL = new Glow(0.35F, 0.9F, 1F);

    // How near a player can come before it goes out, in blocks, and how fast it goes out and shows again, in ticks
    private static final double SHY_RANGE = 8D;
    private static final float GO_OUT_TICKS = 15F;
    private static final float SHOW_TICKS = 40F;
    // The chance a tick of a light shedding a mote, at full brightness
    private static final float MOTES = 0.12F;
    // How quickly a light follows the ground's height under it, a share a tick, so a step isn't a jump
    private static final float SETTLE = 0.15F;
    // How far down it looks for the ground, in blocks
    private static final int SCAN_DEPTH = 96;

    private final WispPath path;
    private final Glow glow;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private final WispGlow[] lights;
    // The ground's height under each light, as last found and as it is followed; the column it was found for
    private final double[] lightY;
    private final int[] lastX;
    private final int[] lastZ;
    private final int[] lastGround;
    private int age;
    // How many haunts on from where it would be it has gone to keep away from this player
    private int shift;
    // Going out (counting down) or showing again (counting up) after moving away from a player; 0 when neither
    private float goingOut;
    private float showing = SHOW_TICKS;

    /**
     * @param age how far into its life it is already, in ticks, for one a player comes upon part way through
     */
    public Wisp(WispPath path, int age, Glow glow) {
        this.path = path;
        this.age = age;
        this.glow = glow;
        int count = path.lights();
        this.lights = new WispGlow[count];
        this.lightY = new double[count];
        this.lastX = new int[count];
        this.lastZ = new int[count];
        this.lastGround = new int[count];
        Arrays.fill(this.lastX, Integer.MIN_VALUE);
        // No ground yet: under a roof the first is looked for near the player's height
        Arrays.fill(this.lastGround, Integer.MIN_VALUE);
        Arrays.fill(this.lightY, Double.NaN);
    }

    /**
     * Whether one can start here: over ground it haunts
     */
    public static boolean canStart(Level level, ITagLibrary tags, double x, double z) {
        var probe = new BlockPos.MutableBlockPos();
        int ground = ground(level, probe, Mth.floor(x), Mth.floor(z), Integer.MIN_VALUE);
        return ground != Integer.MIN_VALUE && tags.is(BlockEffectTags.WISP_GROUNDS, level.getBlockState(probe.set(Mth.floor(x), ground, Mth.floor(z))));
    }

    /**
     * The height of what a wisp floats over in a column: water, or ground it can't pass through, under any leaves, and
     * under the Nether's roof the floor with room over it nearest the player's height (see {@link RoofedGround});
     * MIN_VALUE if there is none or its chunk isn't loaded
     */
    public static int ground(Level level, int x, int z) {
        return ground(level, new BlockPos.MutableBlockPos(), x, z, Integer.MIN_VALUE);
    }

    /**
     * @param near under a roof, the height to look nearest: a light's last ground, so it doesn't jump to the floor the
     *             player has climbed to; MIN_VALUE for the player's
     */
    private static int ground(Level level, BlockPos.MutableBlockPos probe, int x, int z, int near) {
        if (!level.hasChunkAt(probe.set(x, 0, z)))
            return Integer.MIN_VALUE;
        if (level.dimensionType().hasCeiling()) {
            IntPredicate floorWithRoom = y -> isFloor(level, probe.set(x, y, z)) && !isFloor(level, probe.set(x, y + 1, z));
            var found = near == Integer.MIN_VALUE ? RoofedGround.nearPlayer(level, floorWithRoom) : RoofedGround.near(level, near, floorWithRoom);
            return found == null ? Integer.MIN_VALUE : found;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        int bottom = Math.max(level.getMinY(), top - SCAN_DEPTH);
        for (int y = top; y >= bottom; y--)
            if (isFloor(level, probe.set(x, y, z)))
                return y;
        return Integer.MIN_VALUE;
    }

    // Water, or ground it can't pass through; not leaves
    private static boolean isFloor(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return !state.getFluidState().isEmpty() || (state.isSolid() && !state.is(BlockTags.LEAVES));
    }

    @Override
    public ActorKind kind() {
        return ActorKind.WISP;
    }

    @Override
    public double x() {
        return this.path.groupX(this.haunt(), this.age);
    }

    @Override
    public double z() {
        return this.path.groupZ(this.haunt(), this.age);
    }

    // The haunt it is at for this player
    private int haunt() {
        return Math.floorMod(this.path.hauntAt(this.age) + this.shift, WispPath.HAUNTS);
    }

    @Override
    public boolean tick(ClientLevel level, float particles) {
        if (++this.age >= this.path.lifetime())
            return false;
        if (!level.hasChunkAt(this.probe.set(Mth.floor(this.x()), 0, Mth.floor(this.z()))))
            return true;

        this.keepAway();
        var shy = this.goingOut > 0F ? this.goingOut / GO_OUT_TICKS : this.showing / SHOW_TICKS;
        var random = level.getRandom();
        var engine = GameUtils.getParticleManager();
        int haunt = this.haunt();
        for (int i = 0; i < this.lights.length; i++) {
            double x = this.path.lightX(i, haunt, this.age), z = this.path.lightZ(i, haunt, this.age);
            var ground = this.groundUnder(level, i, Mth.floor(x), Mth.floor(z));
            if (Double.isNaN(ground))
                continue;
            this.lightY[i] = Double.isNaN(this.lightY[i]) ? ground : this.lightY[i] + (ground - this.lightY[i]) * SETTLE;
            var target = new WispGlow.Target(x, this.lightY[i] + this.path.lift(i, this.age), z,
                    particles > 0F ? this.path.brightness(i, this.age) * shy : 0F);
            var light = this.lights[i];
            if (light == null || !light.isAlive()) {
                if (target.brightness() <= 0F)
                    continue;
                light = this.lights[i] = WispGlow.light(level, new WispGlow.Target(x, target.y(), z, 0F), this.glow.red(), this.glow.green(), this.glow.blue());
                engine.add(light);
            }
            light.moveTo(target);
            if (random.nextFloat() < MOTES * target.brightness() * particles)
                engine.add(WispGlow.mote(level, x + (random.nextFloat() - 0.5F) * 0.3F, target.y(), z + (random.nextFloat() - 0.5F) * 0.3F,
                        target.brightness(), this.glow.red(), this.glow.green(), this.glow.blue()));
        }
        return true;
    }

    /**
     * Goes out as a player comes near, then shows again at the haunt farthest from them
     */
    private void keepAway() {
        if (this.goingOut > 0F) {
            if (--this.goingOut <= 0F) {
                this.goingOut = 0F;
                this.showing = 0F;
                GameUtils.getPlayer().ifPresent(player -> this.shift = this.farthestFrom(player.getX(), player.getZ()));
            }
            return;
        }
        this.showing = Math.min(SHOW_TICKS, this.showing + 1F);
        GameUtils.getPlayer().ifPresent(player -> {
            if (Mth.lengthSquared(player.getX() - this.x(), player.getZ() - this.z()) < SHY_RANGE * SHY_RANGE)
                this.goingOut = GO_OUT_TICKS * Math.max(0.1F, this.showing / SHOW_TICKS);
        });
    }

    // The shift that puts it at the haunt farthest from a place
    private int farthestFrom(double x, double z) {
        int now = this.path.hauntAt(this.age), best = this.shift;
        double far = -1D;
        for (int shift = 0; shift < WispPath.HAUNTS; shift++) {
            int haunt = Math.floorMod(now + shift, WispPath.HAUNTS);
            var distance = Mth.lengthSquared(this.path.hauntX(haunt) - x, this.path.hauntZ(haunt) - z);
            if (distance > far) {
                far = distance;
                best = shift;
            }
        }
        return best;
    }

    // The height a light floats from in a column: on top of the ground, or a little under the top of water. Each
    // column is looked down once.
    private double groundUnder(ClientLevel level, int light, int x, int z) {
        if (x != this.lastX[light] || z != this.lastZ[light]) {
            this.lastX[light] = x;
            this.lastZ[light] = z;
            // Under a roof, from the light's last ground: it follows its own floor, not the player's
            this.lastGround[light] = ground(level, this.probe, x, z, this.lastGround[light]);
        }
        var y = this.lastGround[light];
        if (y == Integer.MIN_VALUE)
            return Double.NaN;
        return level.getFluidState(this.probe.set(x, y, z)).isEmpty() ? y + 1D : y + 0.9D;
    }
}
