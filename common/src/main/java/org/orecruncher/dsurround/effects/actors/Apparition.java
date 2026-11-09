// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.ActorKind;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.List;

/**
 * An apparition: a pale figure in the shape of a villager that wanders a dark forest or a swamp at night, keeping to
 * a few haunts (see {@link ApparitionPath}) and gliding from one to the next along the ground, around what is in the
 * way ({@link ApparitionRoute}). It turns its head to look about, at you most of all. As the wisps do, it won't be
 * caught: come near and it fades away, to show again at its haunt farthest from you, for that player only. Drawn by
 * {@link ApparitionRenderer}; nothing of it is a particle.
 * <p>
 * Where it is at a haunt is the same for every player; the way between them is found by each player's game over the
 * blocks it has, so two players may see it take different ways now and then.
 */
public final class Apparition implements IActor {

    /**
     * Where it is and how it stands, for a frame
     *
     * @param alpha how much of it shows, 0 to 1
     * @param look  who it was
     */
    public record Pose(double x, double y, double z, float bodyYaw, float headYaw, float headPitch, float alpha, ApparitionLook look) {
    }

    // Its height, in blocks, and where it fades over, as a share of that: nothing at the knees, all of it at the waist
    static final float HEIGHT = 1.95F;
    static final float FADE_FROM = 0.14F;
    static final float FADE_TO = 0.5F;
    // How near a player can come before it fades away, in blocks, and how fast it fades and shows again, in ticks
    static final double SHY_RANGE = 9D;
    private static final double SHY_HEIGHT = 4D;
    private static final float GO_OUT_TICKS = 20F;
    private static final float SHOW_TICKS = 50F;
    // How fast it glides, in blocks a tick, and how near a step it must be to go on to the next
    static final double SPEED = 0.055D;
    private static final double REACHED = 0.35D;
    // How near two may come, in blocks (a villager is 0.6 wide), and the most each is pushed apart a tick
    static final double ROOM = 0.8D;
    static final double NUDGE = 0.05D;
    // How quickly it rises or sinks to its footing, a share a tick, so a step up is a glide, not a jump
    private static final double SETTLE = 0.2D;
    // How fast its body and head turn, in degrees a tick, and how far its head turns from its body
    private static final float BODY_TURN = 5F;
    private static final float HEAD_TURN = 6F;
    static final float HEAD_LIMIT = 70F;
    // How far it watches a player from, and other creatures, in blocks; how often it glances elsewhere, in ticks
    private static final double WATCH_RANGE = 20D;
    private static final double CREATURE_RANGE = 12D;
    private static final int MIN_GLANCE_TICKS = 40;
    private static final int MAX_GLANCE_TICKS = 120;
    // Its eyes, above its feet, in blocks
    private static final double EYES = 1.55D;
    // Where its footing at a haunt is looked for, above and below a height, in blocks; how far down from the top
    private static final int FOOTING_ABOVE = 6;
    private static final int FOOTING_BELOW = 12;
    private static final int GROUND_DEPTH = 48;

    private final ApparitionPath path;
    private final ApparitionLook look;
    private final RandomSource random;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private final boolean[] unreachable = new boolean[ApparitionPath.HAUNTS];
    private int age;
    private double x, y, z;
    private double lastX, lastY, lastZ;
    private float bodyYaw, lastBodyYaw;
    private float headYaw, lastHeadYaw;
    private float headPitch, lastHeadPitch;
    // How many haunts on from where it would be it has gone, to keep away from this player or to skip one out of reach
    private int shift;
    // The haunt it is making for, and its way there; null when there
    private int goal;
    private long @Nullable [] route;
    private int step;
    // Going away (counting down) or showing again (counting up) after a player came near; 0 when neither
    private float goingOut;
    private float showing = SHOW_TICKS;
    // Where it glances when there is nothing to watch, from its body's heading, and until when
    private float glanceYaw;
    private float glancePitch;
    private int glanceUntil;

    private Apparition(ApparitionPath path, ApparitionLook look, int age, int haunt, BlockPos footing) {
        this.path = path;
        this.look = look;
        this.age = age;
        this.random = Randomizer.create(path.seed() ^ 0x67686F7374L);
        this.goal = haunt;
        this.shift = Math.floorMod(haunt - path.hauntAt(age), ApparitionPath.HAUNTS);
        this.x = this.lastX = footing.getX() + 0.5D;
        this.y = this.lastY = footing.getY();
        this.z = this.lastZ = footing.getZ() + 0.5D;
        this.bodyYaw = this.lastBodyYaw = this.random.nextFloat() * 360F;
    }

    /**
     * Makes one at the haunt it would be with at its age, or the next it can stand at; null if it can stand at none.
     * It looks as a villager of the biome it started in would.
     */
    public static @Nullable Apparition create(Level level, ApparitionPath path, int age, Biome biome) {
        int first = path.hauntAt(age);
        for (int i = 0; i < ApparitionPath.HAUNTS; i++) {
            int haunt = Math.floorMod(first + i, ApparitionPath.HAUNTS);
            var footing = ground(level, Mth.floor(path.hauntX(haunt)), Mth.floor(path.hauntZ(haunt)));
            if (footing != null)
                return new Apparition(path, ApparitionLook.of(level, biome, path.seed()), age, haunt, footing);
        }
        return null;
    }

    /**
     * Whether one can start here: on ground it can stand on
     */
    public static boolean canStart(Level level, double x, double z) {
        return ground(level, Mth.floor(x), Mth.floor(z)) != null;
    }

    /**
     * Where its feet would be on the ground in a column: the first place it can stand, looking down from the top, under
     * any leaves; under a roof (the Nether's), the floor nearest the player's height. Null if there is none or its chunk
     * isn't loaded.
     */
    public static @Nullable BlockPos ground(Level level, int x, int z) {
        var probe = new BlockPos.MutableBlockPos(x, 0, z);
        if (!level.hasChunkAt(probe))
            return null;
        if (level.dimensionType().hasCeiling())
            return RoofedGround.nearPlayer(level, height -> standable(level, probe, x, height, z)) instanceof Integer y ? new BlockPos(x, y, z) : null;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        for (int y = top; y >= Math.max(level.getMinY() + 1, top - GROUND_DEPTH); y--)
            if (standable(level, probe, x, y, z))
                return new BlockPos(x, y, z);
        return null;
    }

    /**
     * Whether it can stand with its feet in a block: something under it to stand on (not leaves: it keeps to the
     * ground, not the canopy), or water to glide over, and room for it, two blocks
     */
    static boolean standable(Level level, BlockPos.MutableBlockPos probe, int x, int y, int z) {
        if (!level.hasChunkAt(probe.set(x, y, z)))
            return false;
        var below = level.getBlockState(probe.set(x, y - 1, z));
        boolean floor = !below.getFluidState().isEmpty()
                || (!below.getCollisionShape(level, probe).isEmpty() && !below.is(BlockTags.LEAVES));
        return floor && clear(level, probe, x, y, z) && clear(level, probe, x, y + 1, z);
    }

    private static boolean clear(Level level, BlockPos.MutableBlockPos probe, int x, int y, int z) {
        var state = level.getBlockState(probe.set(x, y, z));
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, probe).isEmpty();
    }

    @Override
    public ActorKind kind() {
        return ActorKind.APPARITION;
    }

    @Override
    public double x() {
        return this.x;
    }

    @Override
    public double z() {
        return this.z;
    }

    /**
     * Where it is and how it stands, between the last tick and this one
     */
    public Pose pose(float partialTick) {
        float shy = this.goingOut > 0F ? this.goingOut / GO_OUT_TICKS : this.showing / SHOW_TICKS;
        float age = this.age + partialTick;
        return new Pose(
                Mth.lerp(partialTick, this.lastX, this.x),
                Mth.lerp(partialTick, this.lastY, this.y) + this.path.bob(age),
                Mth.lerp(partialTick, this.lastZ, this.z),
                Mth.rotLerp(partialTick, this.lastBodyYaw, this.bodyYaw),
                Mth.lerp(partialTick, this.lastHeadYaw, this.headYaw),
                Mth.lerp(partialTick, this.lastHeadPitch, this.headPitch),
                this.path.presence(age) * Mth.clamp(shy, 0F, 1F),
                this.look);
    }

    @Override
    public boolean tick(ClientLevel level, float particles) {
        if (++this.age >= this.path.lifetime())
            return false;
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.lastBodyYaw = this.bodyYaw;
        this.lastHeadYaw = this.headYaw;
        this.lastHeadPitch = this.headPitch;
        if (!level.hasChunkAt(this.probe.set(Mth.floor(this.x), 0, Mth.floor(this.z))))
            return true;

        var player = GameUtils.getPlayer().orElse(null);
        this.keepAway(level, player);
        int haunt = this.haunt();
        if (haunt != this.goal)
            this.setOff(level, haunt);
        boolean moving = this.glide();
        this.look(level, player, moving);
        return true;
    }

    // The haunt it is with now, for this player
    private int haunt() {
        return Math.floorMod(this.path.hauntAt(this.age) + this.shift, ApparitionPath.HAUNTS);
    }

    /**
     * Finds its way to a haunt; one it can't reach is skipped for the next, and if none can be reached it stays
     */
    private void setOff(ClientLevel level, int haunt) {
        ApparitionRoute.Footing footing = (x, y, z) -> standable(level, this.probe, x, y, z);
        var from = BlockPos.containing(this.x, this.y + 0.01D, this.z);
        for (int tries = 0; tries < ApparitionPath.HAUNTS; tries++) {
            if (!this.unreachable[haunt]) {
                var to = this.footingNear(level, haunt, from.getY());
                var way = to == null ? null : ApparitionRoute.find(footing, from, to);
                if (way != null) {
                    this.goal = haunt;
                    this.route = way;
                    this.step = 0;
                    return;
                }
                this.unreachable[haunt] = true;
            }
            this.shift = Math.floorMod(this.shift + 1, ApparitionPath.HAUNTS);
            haunt = this.haunt();
        }
        // Nowhere to go: it stays, and tries them all again when it is time to move on
        java.util.Arrays.fill(this.unreachable, false);
        this.goal = haunt;
        this.route = null;
    }

    // Where it would stand at a haunt, near a height first: the ground there may be under an overhang
    private @Nullable BlockPos footingNear(ClientLevel level, int haunt, int nearY) {
        int x = Mth.floor(this.path.hauntX(haunt)), z = Mth.floor(this.path.hauntZ(haunt));
        if (!level.hasChunkAt(this.probe.set(x, 0, z)))
            return null;
        for (int d = 0; d <= FOOTING_BELOW; d++) {
            if (d <= FOOTING_ABOVE && standable(level, this.probe, x, nearY + d, z))
                return new BlockPos(x, nearY + d, z);
            if (d > 0 && standable(level, this.probe, x, nearY - d, z))
                return new BlockPos(x, nearY - d, z);
        }
        return ground(level, x, z);
    }

    /**
     * Glides on along its way, facing where it goes
     *
     * @return whether it is on its way
     */
    private boolean glide() {
        var way = this.route;
        if (way == null)
            return false;
        while (this.step < way.length) {
            long next = way[this.step];
            double tx = BlockPos.getX(next) + 0.5D, tz = BlockPos.getZ(next) + 0.5D;
            double dx = tx - this.x, dz = tz - this.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < REACHED && this.step < way.length - 1) {
                this.step++;
                continue;
            }
            double move = Math.min(SPEED, distance);
            if (distance > 1e-4D) {
                this.x += dx / distance * move;
                this.z += dz / distance * move;
                this.bodyYaw = turnToward(this.bodyYaw, yawOf(dx, dz), BODY_TURN);
            }
            this.y += (BlockPos.getY(next) - this.y) * SETTLE;
            if (this.step == way.length - 1 && distance <= move + 1e-4D)
                this.route = null;
            return true;
        }
        this.route = null;
        return false;
    }

    /**
     * Turns its head toward what it watches: a player near enough, else a creature now and then, else wherever it
     * glances. When what it watches is past where its head can turn, and it is standing, its body turns as well.
     */
    private void look(ClientLevel level, @Nullable Player player, boolean moving) {
        double eyeY = this.y + EYES;
        float targetYaw, targetPitch;
        if (player != null && Mth.lengthSquared(player.getX() - this.x, player.getEyeY() - eyeY, player.getZ() - this.z) < WATCH_RANGE * WATCH_RANGE) {
            targetYaw = yawOf(player.getX() - this.x, player.getZ() - this.z);
            targetPitch = pitchOf(player.getX() - this.x, player.getEyeY() - eyeY, player.getZ() - this.z);
        } else {
            if (this.age >= this.glanceUntil)
                this.glance(level);
            targetYaw = this.bodyYaw + this.glanceYaw;
            targetPitch = this.glancePitch;
        }

        float relative = Mth.wrapDegrees(targetYaw - this.bodyYaw);
        if (!moving && Math.abs(relative) > HEAD_LIMIT)
            this.bodyYaw = turnToward(this.bodyYaw, targetYaw, BODY_TURN * 0.5F);
        relative = Mth.clamp(Mth.wrapDegrees(targetYaw - this.bodyYaw), -HEAD_LIMIT, HEAD_LIMIT);
        this.headYaw = approach(this.headYaw, relative, HEAD_TURN);
        this.headPitch = approach(this.headPitch, Mth.clamp(targetPitch, -40F, 40F), HEAD_TURN * 0.5F);
    }

    // Picks somewhere new to glance: a creature nearby if there is one, else a look about
    private void glance(ClientLevel level) {
        this.glanceUntil = this.age + MIN_GLANCE_TICKS + this.random.nextInt(MAX_GLANCE_TICKS - MIN_GLANCE_TICKS + 1);
        var near = new AABB(this.x - CREATURE_RANGE, this.y - 4D, this.z - CREATURE_RANGE, this.x + CREATURE_RANGE, this.y + 6D, this.z + CREATURE_RANGE);
        var creatures = level.getEntitiesOfClass(LivingEntity.class, near, entity -> !(entity instanceof Player) && entity.isAlive());
        if (!creatures.isEmpty() && this.random.nextFloat() < 0.6F) {
            var creature = creatures.get(this.random.nextInt(creatures.size()));
            double dx = creature.getX() - this.x, dy = creature.getEyeY() - (this.y + EYES), dz = creature.getZ() - this.z;
            this.glanceYaw = Mth.wrapDegrees(yawOf(dx, dz) - this.bodyYaw);
            this.glancePitch = pitchOf(dx, dy, dz);
            return;
        }
        this.glanceYaw = (this.random.nextFloat() * 2F - 1F) * 80F;
        this.glancePitch = this.random.nextFloat() * 30F - 12F;
    }

    /**
     * Fades away as a player comes near, then shows again at its haunt farthest from them
     */
    private void keepAway(ClientLevel level, @Nullable Player player) {
        if (this.goingOut > 0F) {
            if (--this.goingOut <= 0F) {
                this.goingOut = 0F;
                this.showing = 0F;
                if (player != null)
                    this.reappearFarFrom(level, player.getX(), player.getZ());
            }
            return;
        }
        this.showing = Math.min(SHOW_TICKS, this.showing + 1F);
        if (player != null && Math.abs(player.getY() - this.y) < SHY_HEIGHT
                && Mth.lengthSquared(player.getX() - this.x, player.getZ() - this.z) < SHY_RANGE * SHY_RANGE)
            this.goingOut = GO_OUT_TICKS * Math.max(0.1F, this.showing / SHOW_TICKS);
    }

    // Shows again at its haunt farthest from a place, standing there
    private void reappearFarFrom(ClientLevel level, double px, double pz) {
        int now = this.path.hauntAt(this.age);
        int bestShift = this.shift;
        BlockPos best = null;
        double far = -1D;
        for (int shift = 0; shift < ApparitionPath.HAUNTS; shift++) {
            int haunt = Math.floorMod(now + shift, ApparitionPath.HAUNTS);
            var distance = Mth.lengthSquared(this.path.hauntX(haunt) - px, this.path.hauntZ(haunt) - pz);
            if (distance <= far)
                continue;
            var footing = ground(level, Mth.floor(this.path.hauntX(haunt)), Mth.floor(this.path.hauntZ(haunt)));
            if (footing == null)
                continue;
            far = distance;
            bestShift = shift;
            best = footing;
        }
        if (best == null)
            return;
        this.shift = bestShift;
        this.goal = this.haunt();
        this.route = null;
        this.x = this.lastX = best.getX() + 0.5D;
        this.y = this.lastY = best.getY();
        this.z = this.lastZ = best.getZ() + 0.5D;
        // Facing back the way the player is, to watch for them
        this.bodyYaw = this.lastBodyYaw = yawOf(px - this.x, pz - this.z);
        this.headYaw = this.lastHeadYaw = 0F;
    }

    /**
     * Keeps apparitions out of each other's way, as mobs push one another: two nearer than {@link #ROOM} are nudged
     * apart a little each tick, each only where it could stand, so none is pushed into a wall or off a ledge
     */
    public static void makeRoom(ClientLevel level, List<Apparition> apparitions) {
        for (int i = 0; i < apparitions.size(); i++) {
            var a = apparitions.get(i);
            for (int j = i + 1; j < apparitions.size(); j++) {
                var b = apparitions.get(j);
                if (Math.abs(a.y - b.y) >= HEIGHT)
                    continue;
                var push = separation(b.x - a.x, b.z - a.z, a.path.seed() ^ b.path.seed());
                if (push == null)
                    continue;
                a.nudge(level, -push[0], -push[1]);
                b.nudge(level, push[0], push[1]);
            }
        }
    }

    /**
     * How far the second of two is pushed from the first, across the ground, this tick (the first the other way); null
     * if they have room. Two in the very same spot go apart a way drawn from their seeds, the same for every player.
     *
     * @param dx       from the first to the second, in blocks
     * @param dz       from the first to the second, in blocks
     * @param tieBreak for the way apart when they are in the same spot
     */
    static double @Nullable [] separation(double dx, double dz, long tieBreak) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance >= ROOM)
            return null;
        double ux, uz;
        if (distance < 1e-4D) {
            double angle = Randomizer.create(tieBreak).nextDouble() * Math.PI * 2D;
            ux = Math.cos(angle);
            uz = Math.sin(angle);
        } else {
            ux = dx / distance;
            uz = dz / distance;
        }
        double push = Math.min(NUDGE, (ROOM - distance) * 0.5D);
        return new double[]{ux * push, uz * push};
    }

    // Moves it a little across the ground, if it can stand there
    private void nudge(ClientLevel level, double dx, double dz) {
        double nx = this.x + dx, nz = this.z + dz;
        int bx = Mth.floor(nx), by = Mth.floor(this.y + 0.01D), bz = Mth.floor(nz);
        if ((bx != Mth.floor(this.x) || bz != Mth.floor(this.z)) && !standable(level, this.probe, bx, by, bz))
            return;
        this.x = nx;
        this.z = nz;
    }

    /**
     * How much of it shows at a height above its feet, 0 to 1, before how see-through it is at most: nothing at the
     * knees, all of it from the waist up, smoothly between
     *
     * @param height above its feet, in blocks
     */
    static float fade(float height) {
        float t = Mth.clamp((height / HEIGHT - FADE_FROM) / (FADE_TO - FADE_FROM), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /**
     * The game's yaw, in degrees, of a direction across the ground
     */
    static float yawOf(double dx, double dz) {
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
    }

    // The game's pitch, in degrees, of a direction: up is negative
    private static float pitchOf(double dx, double dy, double dz) {
        return (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
    }

    static float turnToward(float from, float to, float most) {
        return from + Mth.clamp(Mth.wrapDegrees(to - from), -most, most);
    }

    private static float approach(float from, float to, float most) {
        return from + Mth.clamp(to - from, -most, most);
    }
}
