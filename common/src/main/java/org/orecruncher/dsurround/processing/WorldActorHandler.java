// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.ActorKind;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.actors.ActorPath;
import org.orecruncher.dsurround.effects.actors.Apparition;
import org.orecruncher.dsurround.effects.actors.ApparitionPath;
import org.orecruncher.dsurround.effects.actors.ApparitionRenderer;
import org.orecruncher.dsurround.effects.actors.ActorSpawns;
import org.orecruncher.dsurround.effects.actors.Firespout;
import org.orecruncher.dsurround.effects.actors.GroundVortex;
import org.orecruncher.dsurround.effects.actors.IActor;
import org.orecruncher.dsurround.effects.actors.VortexPath;
import org.orecruncher.dsurround.effects.actors.Waterspout;
import org.orecruncher.dsurround.effects.actors.Wisp;
import org.orecruncher.dsurround.effects.actors.WispPath;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IWorldRender;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.oracle.IDimensionOracle;
import org.orecruncher.dsurround.runtime.weather.DustStorms;
import org.orecruncher.dsurround.runtime.weather.UpperWind;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * World actors: things that move through the world on their own for a while, such as dust devils, which every player
 * nearby sees alike. Where and when one may start comes from the world's seed and the game time ({@link ActorSpawns});
 * whether it does, from the biome there (its {@code actors} in {@code biomes.json}: a chance against the plan's roll,
 * and conditions) and from the ground. Each chance is looked at once, as it comes in range, and one already part way
 * through its life is started there.
 * <p>
 * The conditions are evaluated where the player is, as the biome's sounds are: weather and time of day are the same
 * for everyone, but a script on the player's own state would make players disagree.
 * <p>
 * What each kind is (its path, sound, config switch, where its ground is and how one is made) is a row of
 * {@link #types}: a new kind is a row there and its class.
 */
public class WorldActorHandler extends AbstractClientHandler {

    // How far from the player actors are started, and kept, in blocks, unless their kind says nearer
    static final int RANGE = 160;
    private static final int KEEP_RANGE = RANGE + 32;
    // An apparition is only met near: in a dark forest one farther off would be behind the trees, and as there is only
    // one at a time, it would keep a nearer one from starting. One left behind comes back when the player is near again.
    static final int APPARITION_RANGE = 48;
    private static final int APPARITION_KEEP_RANGE = 64;
    // How often chances are looked at, in ticks
    private static final int CHECK_TICKS = 20;
    // Where a summoned one starts, ahead of the player, in blocks
    private static final double SUMMON_AHEAD = 16D;

    /**
     * How a summons went
     */
    public enum Summoned {
        STARTED,
        // The kind is turned off, in the config or in this dimension
        OFF,
        // Nowhere for one ahead of the player: a waterspout needs open water, a firespout open lava
        NO_PLACE
    }

    /**
     * Whether one can start at a place
     */
    @FunctionalInterface
    private interface Placement {
        boolean canStart(ClientLevel level, double x, double z);
    }

    /**
     * The height of the ground one stands on in a column, for the biome there
     */
    @FunctionalInterface
    private interface Ground {
        int y(ClientLevel level, int x, int z);
    }

    /**
     * One's whole life, from where it starts, its seed and the day's wind
     */
    @FunctionalInterface
    private interface PathMaker<P extends ActorPath> {
        P make(double x, double z, long seed, float windHeading);
    }

    /**
     * Makes one, where it can start; null if it can't after all
     */
    @FunctionalInterface
    private interface Maker<P extends ActorPath> {
        @Nullable IActor make(ClientLevel level, P path, int age, Biome biome, @Nullable ISoundFactory sound);
    }

    /**
     * What a kind of actor is
     *
     * @param maxLifetime     the longest one lives, in ticks
     * @param path            how its life is worked out
     * @param sound           the sound factory that follows it, if any
     * @param enabled         its switch in the config
     * @param summonsAnywhere whether {@code /dsactor} starts one wherever the player looks, for testing, rather than
     *                        only where one could start
     * @param range           how far from the player one is started, in blocks
     * @param keepRange       how far from the player one is kept, in blocks; past it, it is dropped, to start again
     *                        when the player comes back within range
     */
    private record Type<P extends ActorPath>(int maxLifetime, PathMaker<P> path, @Nullable Identifier sound,
                                             Predicate<Configuration> enabled, boolean summonsAnywhere,
                                             Placement placement, Ground ground, Maker<P> maker, int range, int keepRange) {
        Type(int maxLifetime, PathMaker<P> path, @Nullable Identifier sound, Predicate<Configuration> enabled, boolean summonsAnywhere,
             Placement placement, Ground ground, Maker<P> maker) {
            this(maxLifetime, path, sound, enabled, summonsAnywhere, placement, ground, maker, RANGE, KEEP_RANGE);
        }
    }

    private final IBiomeLibrary biomeLibrary;
    private final ITagLibrary tags;
    private final IDimensionOracle dimension;
    private final ISoundLibrary soundLibrary;
    private final IAudioPlayer audioPlayer;
    private final Map<ActorKind, Type<?>> types = new EnumMap<>(ActorKind.class);
    // The longest any actor lives, in slots, so chances from slots before it can still be running
    private final int lifetimeSlots;
    private final ObjectArray<IActor> actors = new ObjectArray<>();
    // The chance each running actor started from, by actor; summoned ones have none
    private final Map<IActor, Long> chances = new IdentityHashMap<>();
    // The chances looked at, by key, with the slot they were in, to forget them once past
    private final Long2LongOpenHashMap seen = new Long2LongOpenHashMap();
    @Nullable
    private ClientLevel level;

    public WorldActorHandler(IBiomeLibrary biomeLibrary, ITagLibrary tags, IDimensionOracle dimension, ISoundLibrary soundLibrary, IAudioPlayer audioPlayer, Configuration config, IModLog logger) {
        super("World Actors", config, logger);
        this.biomeLibrary = biomeLibrary;
        this.tags = tags;
        this.dimension = dimension;
        this.soundLibrary = soundLibrary;
        this.audioPlayer = audioPlayer;

        // A dust devil's dust is the biome's
        this.types.put(ActorKind.DUST_DEVIL, new Type<>(VortexPath.DUST_DEVIL.maxLifetime(), vortex(VortexPath.DUST_DEVIL), Constants.asId("actor/dust_devil"), c -> c.worldActorOptions.enableDustDevils, true,
                (level, x, z) -> GroundVortex.canStart(GroundVortex.DUST_DEVIL, level, tags, x, z), WorldActorHandler::heightmap,
                (level, path, age, biome, sound) -> new GroundVortex(GroundVortex.DUST_DEVIL, path, age, DustStorms.dustColor(biome, biomeLibrary), tags, sound, audioPlayer)));
        this.types.put(ActorKind.SNOWSPOUT, new Type<>(VortexPath.SNOWSPOUT.maxLifetime(), vortex(VortexPath.SNOWSPOUT), Constants.asId("actor/snowspout"), c -> c.worldActorOptions.enableSnowspouts, true,
                (level, x, z) -> GroundVortex.canStart(GroundVortex.SNOWSPOUT, level, tags, x, z), WorldActorHandler::heightmap,
                (level, path, age, biome, sound) -> new GroundVortex(GroundVortex.SNOWSPOUT, path, age, GroundVortex.SNOW_COLOR, tags, sound, audioPlayer)));
        // As tall as from the water to the clouds
        this.types.put(ActorKind.WATERSPOUT, new Type<>(VortexPath.WATERSPOUT.maxLifetime(), vortex(VortexPath.WATERSPOUT), Constants.asId("actor/waterspout"), c -> c.worldActorOptions.enableWaterspouts, false,
                (level, x, z) -> Waterspout.canStart(level, tags, x, z), WorldActorHandler::heightmap,
                (level, path, age, biome, sound) -> {
                    var surface = heightmap(level, Mth.floor(path.x(age)), Mth.floor(path.z(age)));
                    return new Waterspout(path.withHeight(Waterspout.height(surface, dimension.getCloudHeight())), age, tags, sound, audioPlayer);
                }));
        // Under the Nether's roof, its biome is the lava's, not the roof's
        this.types.put(ActorKind.FIRESPOUT, new Type<>(VortexPath.FIRESPOUT.maxLifetime(), vortex(VortexPath.FIRESPOUT), Constants.asId("actor/firespout"), c -> c.worldActorOptions.enableFirespouts, false,
                Firespout::canStart,
                (level, x, z) -> {
                    var surface = Firespout.lavaSurface(level, x, z);
                    return surface == Integer.MIN_VALUE ? heightmap(level, x, z) : surface;
                },
                (level, path, age, biome, sound) -> new Firespout(path, age, sound, audioPlayer)));
        // Silent; soul fire under the Nether's roof, a marsh light anywhere else
        this.types.put(ActorKind.WISP, new Type<>(WispPath.MAX_LIFETIME, (x, z, seed, wind) -> WispPath.of(x, z, seed), null, c -> c.worldActorOptions.enableWisps, true,
                (level, x, z) -> Wisp.canStart(level, tags, x, z),
                (level, x, z) -> {
                    var ground = Wisp.ground(level, x, z);
                    return ground == Integer.MIN_VALUE ? heightmap(level, x, z) : ground;
                },
                (level, path, age, biome, sound) -> new Wisp(path, age, level.dimensionType().hasCeiling() ? Wisp.SOUL : Wisp.MARSH)));
        // Silent, scary content, and one at a time: drawn by ApparitionRenderer, so not under a shader pack
        this.types.put(ActorKind.APPARITION, new Type<>(ApparitionPath.MAX_LIFETIME, (x, z, seed, wind) -> ApparitionPath.of(x, z, seed), null,
                c -> c.worldActorOptions.enableApparitions && c.scaryContent.allowScaryContent && ApparitionRenderer.isUsable(), true,
                (level, x, z) -> this.count(ActorKind.APPARITION) == 0 && Apparition.canStart(level, x, z),
                (level, x, z) -> {
                    var ground = Apparition.ground(level, x, z);
                    return ground == null ? heightmap(level, x, z) : ground.getY();
                },
                (level, path, age, biome, sound) -> this.count(ActorKind.APPARITION) == 0 ? Apparition.create(level, path, age) : null,
                APPARITION_RANGE, APPARITION_KEEP_RANGE));

        IWorldRender.EVENT.register(this::render);

        if (this.types.size() != ActorKind.values().length)
            throw new IllegalStateException("A kind of world actor has no type");
        this.lifetimeSlots = Math.ceilDiv(this.types.values().stream().mapToInt(Type::maxLifetime).max().orElse(0), ActorSpawns.SLOT_TICKS);
    }

    @Override
    public void process(final Player player) {
        if (!(player.level() instanceof ClientLevel clientLevel) || !this.enabled()) {
            this.clear();
            return;
        }
        this.follow(clientLevel);

        var particles = particleShare(GameUtils.getGameSettings().particles().get());
        this.actors.removeIf(actor -> {
            boolean over = !actor.tick(clientLevel, particles);
            if (!over && this.inRange(player, actor.x(), actor.z(), this.types.get(actor.kind()).keepRange()) && this.kindEnabled(actor.kind()))
                return false;
            var chance = this.chances.remove(actor);
            // Left behind or switched off, not over: its chance is looked at again, so coming back (or switching it
            // back on) finds it part way through its life, where everyone else sees it. One that is over, or broke
            // up early, stays looked at, or it would start again.
            if (!over && chance != null)
                this.seen.remove((long) chance);
            return true;
        });

        long time = clientLevel.getGameTime();
        if (time % CHECK_TICKS == 0)
            this.lookForNew(clientLevel, player, time);
    }

    private void lookForNew(ClientLevel level, Player player, long time) {
        long slot = ActorSpawns.slotOf(time);
        this.seen.long2LongEntrySet().removeIf(entry -> entry.getLongValue() < slot - this.lifetimeSlots);

        long worldSeed = level.getBiomeManager().biomeZoomSeed;
        int minX = ActorSpawns.cellOf(player.getX() - RANGE), maxX = ActorSpawns.cellOf(player.getX() + RANGE);
        int minZ = ActorSpawns.cellOf(player.getZ() - RANGE), maxZ = ActorSpawns.cellOf(player.getZ() + RANGE);
        for (var kind : ActorKind.values()) {
            if (!this.kindEnabled(kind))
                continue;
            for (long s = slot - this.lifetimeSlots; s <= slot; s++)
                for (int cx = minX; cx <= maxX; cx++)
                    for (int cz = minZ; cz <= maxZ; cz++)
                        this.consider(level, player, time, s, ActorSpawns.plan(worldSeed, cx, cz, s, kind));
        }
    }

    private void consider(ClientLevel level, Player player, long time, long slot, ActorSpawns.Plan plan) {
        if (time < plan.startTick() || this.seen.containsKey(plan.key()))
            return;
        this.consider(this.types.get(plan.kind()), level, player, time, slot, plan);
    }

    private <P extends ActorPath> void consider(Type<P> type, ClientLevel level, Player player, long time, long slot, ActorSpawns.Plan plan) {
        var path = type.path().make(plan.x(), plan.z(), plan.seed(), windHeading(plan.startTick()));
        int age = (int) (time - plan.startTick());
        if (age >= path.lifetime()) {
            this.seen.put(plan.key(), slot);
            return;
        }
        // Not yet in reach, or its ground not yet loaded: looked at again later
        double x = path.x(age), z = path.z(age);
        if (!this.inRange(player, x, z, type.range()) || !level.hasChunkAt(BlockPos.containing(x, 0, z)))
            return;

        this.seen.put(plan.key(), slot);
        var biome = biomeAt(type, level, x, z);
        var entry = this.biomeLibrary.getBiomeInfo(biome).getActor(plan.kind());
        if (entry == null || plan.roll() >= entry.chance() || !entry.matches() || !type.placement().canStart(level, x, z))
            return;
        this.start(type, level, path, age, biome, plan.key());
    }

    /**
     * Starts one ahead of the player, whatever the biome and weather, for testing
     *
     * @return how it went
     */
    public Summoned summon(Player player, ActorKind kind) {
        if (!(player.level() instanceof ClientLevel clientLevel) || !this.enabled() || !this.kindEnabled(kind))
            return Summoned.OFF;
        this.follow(clientLevel);
        // Only one apparition at a time: a summoned one takes the place of the one there is
        if (kind == ActorKind.APPARITION)
            this.actors.removeIf(actor -> {
                if (actor.kind() != ActorKind.APPARITION)
                    return false;
                this.chances.remove(actor);
                return true;
            });
        var look = player.getLookAngle();
        var length = Math.max(1e-3D, Math.sqrt(look.x * look.x + look.z * look.z));
        double x = player.getX() + look.x / length * SUMMON_AHEAD;
        double z = player.getZ() + look.z / length * SUMMON_AHEAD;
        return this.summon(this.types.get(kind), clientLevel, x, z);
    }

    private <P extends ActorPath> Summoned summon(Type<P> type, ClientLevel level, double x, double z) {
        if (!level.hasChunkAt(BlockPos.containing(x, 0, z)) || (!type.summonsAnywhere() && !type.placement().canStart(level, x, z)))
            return Summoned.NO_PLACE;
        var path = type.path().make(x, z, RANDOM.nextLong(), windHeading(level.getGameTime()));
        return this.start(type, level, path, 0, biomeAt(type, level, x, z), null) ? Summoned.STARTED : Summoned.NO_PLACE;
    }

    private <P extends ActorPath> boolean start(Type<P> type, ClientLevel level, P path, int age, Biome biome, @Nullable Long chance) {
        var sound = type.sound() == null ? null : this.soundLibrary.getSoundFactory(type.sound()).orElse(null);
        var actor = type.maker().make(level, path, age, biome, sound);
        if (actor == null)
            return false;
        this.actors.add(actor);
        if (chance != null)
            this.chances.put(actor, chance);
        return true;
    }

    private static Biome biomeAt(Type<?> type, ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        return level.getBiome(new BlockPos(bx, type.ground().y(level, bx, bz), bz)).value();
    }

    private static PathMaker<VortexPath> vortex(VortexPath.Shape shape) {
        return (x, z, seed, wind) -> VortexPath.of(shape, x, z, seed, wind);
    }

    private static int heightmap(ClientLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    // The way the wind blows on the day one starts
    private static float windHeading(long startTick) {
        return UpperWind.heading(UpperWind.dayOf(startTick));
    }

    /**
     * Draws what isn't made of particles (apparitions), as each is at this frame. Render thread.
     */
    private void render() {
        if (this.actors.isEmpty())
            return;
        var minecraft = GameUtils.getMC();
        var partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var poses = new java.util.ArrayList<Apparition.Pose>(1);
        for (var actor : this.actors)
            if (actor instanceof Apparition apparition)
                poses.add(apparition.pose(partialTick));
        if (!poses.isEmpty())
            ApparitionRenderer.draw(poses, minecraft.gameRenderer.mainCamera(), minecraft.gameRenderer.mainRenderTarget());
    }

    /**
     * How many there are now, of a kind
     */
    public long count(ActorKind kind) {
        return this.actors.stream().filter(actor -> actor.kind() == kind).count();
    }

    private boolean enabled() {
        return this.config.worldActorOptions.enableWorldActors && GameUtils.isInGame() && this.dimension.worldActors();
    }

    private boolean kindEnabled(ActorKind kind) {
        return this.types.get(kind).enabled().test(this.config);
    }

    private boolean inRange(Player player, double x, double z, int range) {
        return Mth.lengthSquared(x - player.getX(), z - player.getZ()) <= (double) range * range;
    }

    /**
     * The share of an actor's particles the game's Particles option allows
     */
    static float particleShare(ParticleStatus status) {
        return switch (status) {
            case ALL -> 1F;
            case DECREASED -> 0.4F;
            case MINIMAL -> 0F;
        };
    }

    // A new level is a fresh start
    private void follow(ClientLevel level) {
        if (level != this.level) {
            this.clear();
            this.level = level;
        }
    }

    private void clear() {
        this.actors.clear();
        this.chances.clear();
        this.seen.clear();
        this.level = null;
    }

    @Override
    public void onDisconnect() {
        this.clear();
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        if (!this.actors.isEmpty())
            event.add(CollectDiagnosticsEvent.Section.Systems, "World actors: %d (%s)".formatted(this.actors.size(),
                    Arrays.stream(ActorKind.values()).map(kind -> kind.getName() + " " + this.count(kind)).collect(Collectors.joining(", "))));
    }
}
