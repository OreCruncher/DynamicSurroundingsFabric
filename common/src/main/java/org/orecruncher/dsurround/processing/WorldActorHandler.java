// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.ActorKind;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.actors.ActorSpawns;
import org.orecruncher.dsurround.effects.actors.DustDevil;
import org.orecruncher.dsurround.effects.actors.DustDevilPath;
import org.orecruncher.dsurround.effects.actors.IActor;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.oracle.IDimensionOracle;
import org.orecruncher.dsurround.runtime.weather.DustStorms;
import org.orecruncher.dsurround.runtime.weather.UpperWind;
import org.orecruncher.dsurround.sound.IAudioPlayer;

/**
 * World actors: things that move through the world on their own for a while, such as dust devils, which every player
 * nearby sees alike. Where and when one may start comes from the world's seed and the game time ({@link ActorSpawns});
 * whether it does, from the biome there (its {@code actors} in {@code biomes.json}: a chance against the plan's roll,
 * and conditions) and from the ground. Each chance is looked at once, as it comes in range, and one already part way
 * through its life is started there.
 * <p>
 * The conditions are evaluated where the player is, as the biome's sounds are: weather and time of day are the same
 * for everyone, but a script on the player's own state would make players disagree.
 */
public class WorldActorHandler extends AbstractClientHandler {

    // How far from the player actors are started, and kept, in blocks
    static final int RANGE = 160;
    private static final int KEEP_RANGE = RANGE + 32;
    // How often chances are looked at, in ticks
    private static final int CHECK_TICKS = 20;
    // The longest any actor lives, in slots, so chances from slots before it can still be running
    private static final int LIFETIME_SLOTS = Math.ceilDiv(DustDevilPath.MAX_LIFETIME, ActorSpawns.SLOT_TICKS);
    // A dust devil's wind
    private static final Identifier DUST_DEVIL_SOUND = Constants.asId("actor/dust_devil");
    // Where a summoned one starts, ahead of the player, in blocks
    private static final double SUMMON_AHEAD = 16D;

    private final IBiomeLibrary biomeLibrary;
    private final ITagLibrary tags;
    private final IDimensionOracle dimension;
    private final ISoundLibrary soundLibrary;
    private final IAudioPlayer audioPlayer;
    private final ObjectArray<IActor> actors = new ObjectArray<>();
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
    }

    @Override
    public void process(final Player player) {
        if (!(player.level() instanceof ClientLevel clientLevel) || !this.enabled()) {
            this.clear();
            return;
        }
        if (clientLevel != this.level) {
            this.clear();
            this.level = clientLevel;
        }

        var particles = particleShare(GameUtils.getGameSettings().particles().get());
        this.actors.removeIf(actor -> !actor.tick(clientLevel, particles) || !this.inRange(player, actor.x(), actor.z(), KEEP_RANGE) || !this.kindEnabled(actor.kind()));

        long time = clientLevel.getGameTime();
        if (time % CHECK_TICKS == 0)
            this.lookForNew(clientLevel, player, time);
    }

    private void lookForNew(ClientLevel level, Player player, long time) {
        long slot = ActorSpawns.slotOf(time);
        this.seen.long2LongEntrySet().removeIf(entry -> entry.getLongValue() < slot - LIFETIME_SLOTS);

        long worldSeed = level.getBiomeManager().biomeZoomSeed;
        int minX = ActorSpawns.cellOf(player.getX() - RANGE), maxX = ActorSpawns.cellOf(player.getX() + RANGE);
        int minZ = ActorSpawns.cellOf(player.getZ() - RANGE), maxZ = ActorSpawns.cellOf(player.getZ() + RANGE);
        for (var kind : ActorKind.values()) {
            if (!this.kindEnabled(kind))
                continue;
            for (long s = slot - LIFETIME_SLOTS; s <= slot; s++)
                for (int cx = minX; cx <= maxX; cx++)
                    for (int cz = minZ; cz <= maxZ; cz++)
                        this.consider(level, player, time, s, ActorSpawns.plan(worldSeed, cx, cz, s, kind));
        }
    }

    private void consider(ClientLevel level, Player player, long time, long slot, ActorSpawns.Plan plan) {
        if (time < plan.startTick() || this.seen.containsKey(plan.key()))
            return;
        var path = DustDevilPath.of(plan.x(), plan.z(), plan.seed(), UpperWind.heading(UpperWind.dayOf(plan.startTick())));
        int age = (int) (time - plan.startTick());
        if (age >= path.lifetime()) {
            this.seen.put(plan.key(), slot);
            return;
        }
        // Not yet in reach, or its ground not yet loaded: looked at again later
        double x = path.x(age), z = path.z(age);
        var pos = BlockPos.containing(x, 0, z);
        if (!this.inRange(player, x, z, RANGE) || !level.hasChunkAt(pos))
            return;

        this.seen.put(plan.key(), slot);
        var biome = level.getBiome(pos.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()))).value();
        var entry = this.biomeLibrary.getBiomeInfo(biome).getActor(plan.kind());
        if (entry == null || plan.roll() >= entry.chance() || !entry.matches())
            return;
        if (DustDevil.canStart(level, this.tags, x, z))
            this.actors.add(this.dustDevil(path, age, biome));
    }

    /**
     * Starts one ahead of the player, whatever the biome and weather, for testing
     *
     * @return whether it could: false if the kind is turned off, in the config or in this dimension
     */
    public boolean summon(Player player, ActorKind kind) {
        if (!(player.level() instanceof ClientLevel clientLevel) || !this.enabled() || !this.kindEnabled(kind))
            return false;
        var look = player.getLookAngle();
        var length = Math.max(1e-3D, Math.sqrt(look.x * look.x + look.z * look.z));
        double x = player.getX() + look.x / length * SUMMON_AHEAD;
        double z = player.getZ() + look.z / length * SUMMON_AHEAD;
        if (clientLevel != this.level) {
            this.clear();
            this.level = clientLevel;
        }
        var path = DustDevilPath.of(x, z, RANDOM.nextLong(), UpperWind.heading(UpperWind.dayOf(clientLevel.getGameTime())));
        var biome = clientLevel.getBiome(BlockPos.containing(x, player.getY(), z)).value();
        this.actors.add(this.dustDevil(path, 0, biome));
        return true;
    }

    private DustDevil dustDevil(DustDevilPath path, int age, Biome biome) {
        var sound = this.soundLibrary.getSoundFactory(DUST_DEVIL_SOUND).orElse(null);
        return new DustDevil(path, age, DustStorms.dustColor(biome, this.biomeLibrary), this.tags, sound, this.audioPlayer);
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
        return switch (kind) {
            case DUST_DEVIL -> this.config.worldActorOptions.enableDustDevils;
        };
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

    private void clear() {
        this.actors.clear();
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
            event.add(CollectDiagnosticsEvent.Section.Systems, "World actors: %d (dust devils %d)".formatted(this.actors.size(), this.count(ActorKind.DUST_DEVIL)));
    }
}
