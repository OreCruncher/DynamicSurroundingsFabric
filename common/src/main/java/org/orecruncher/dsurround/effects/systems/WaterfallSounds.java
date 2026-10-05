package org.orecruncher.dsurround.effects.systems;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.effects.BlockEffectUtils;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.HighestWeights;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.sound.BackgroundSoundLoop;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;
import org.orecruncher.dsurround.tags.FluidTags;

import java.util.Arrays;

/**
 * The sounds of the waterfalls around the player: a looping sound at each, louder for a bigger drop. Only
 * {@link #SOUND_INSTANCE_CAP} play at once, chosen for the most impact (see {@link #desiredLocations}). Each sound
 * belongs to one waterfall effect, by position, and is stopped when that effect goes.
 */
final class WaterfallSounds {

    static final int SOUND_INSTANCE_CAP = 32;
    private static final int SOUND_CHECK_INTERVAL = 4;

    private static final ISoundFactory[] ACOUSTICS = new ISoundFactory[BlockEffectUtils.MAX_STRENGTH + 1];

    static {
        var soundLibrary = ContainerManager.resolve(ISoundLibrary.class);

        // A missing sound factory (a resource pack removing one, say) must not fail class loading, which would take
        // the game down. The base sound never throws: an undefined factory gets a default. The louder tiers fall
        // back to the base sound, with a warning.
        var base = soundLibrary.getSoundFactoryOrDefault(Constants.asId("waterfalls/0"));
        Arrays.fill(ACOUSTICS, base);

        // By strength, the height of the drop. (Strength used to count 2 more than the drop; these tiers were
        // moved down to match, so a given waterfall sounds as it did. waterfalls/0 is only the fallback.)
        ACOUSTICS[1] = factoryOrBase(soundLibrary, "waterfalls/1", base);
        ACOUSTICS[2] = factoryOrBase(soundLibrary, "waterfalls/2", base);
        ACOUSTICS[3] = ACOUSTICS[4] = factoryOrBase(soundLibrary, "waterfalls/3", base);
        ACOUSTICS[5] = ACOUSTICS[6] = factoryOrBase(soundLibrary, "waterfalls/4", base);
        ACOUSTICS[7] = ACOUSTICS[8] = ACOUSTICS[9] = ACOUSTICS[10] = factoryOrBase(soundLibrary, "waterfalls/5", base);
    }

    private static ISoundFactory factoryOrBase(ISoundLibrary soundLibrary, String name, ISoundFactory base) {
        return soundLibrary.getSoundFactory(Constants.asId(name)).orElseGet(() -> {
            Library.LOGGER.warn("Waterfall sound factory %s not found; using waterfalls/0 instead", name);
            return base;
        });
    }

    private final IModLog logger;
    private final String systemName;
    private final IAudioPlayer audioPlayer;
    private final Long2ObjectOpenHashMap<BackgroundSoundLoop> instances = new Long2ObjectOpenHashMap<>();
    private long soundCheckThrottle;

    // Reused each time sound locations are chosen, so choosing them allocates nothing
    private final LongOpenHashSet desiredLocations = new LongOpenHashSet(SOUND_INSTANCE_CAP);
    private final HighestWeights loudest = new HighestWeights(SOUND_INSTANCE_CAP);

    WaterfallSounds(IModLog logger, String systemName, IAudioPlayer audioPlayer) {
        this.logger = logger;
        this.systemName = systemName;
        this.audioPlayer = audioPlayer;
    }

    /**
     * Whether the waterfall at {@code posIndex} has a sound.
     */
    boolean hasSound(long posIndex) {
        return this.instances.containsKey(posIndex);
    }

    /**
     * Stops every waterfall sound.
     */
    void stopAll() {
        if (this.instances.isEmpty())
            return;
        this.instances.values().forEach(this.audioPlayer::stop);
        this.instances.clear();
    }

    /**
     * Stops the sound of the waterfall at {@code posIndex}, if it has one.
     */
    void stop(long posIndex) {
        var sound = this.instances.remove(posIndex);
        if (sound != null)
            this.audioPlayer.stop(sound);
    }

    /**
     * Brings the sounds up to date with the waterfalls, by position: stops those whose waterfall has gone, and every
     * few ticks chooses which waterfalls should be heard, starting and stopping sounds to match.
     */
    void update(Long2ObjectOpenHashMap<IBlockEffect> waterfalls) {
        // Effects can be removed without the system hearing of it (their own shouldRemove() check, or the scanner
        // dropping effects that left range), so stop their sounds here. There are at most SOUND_INSTANCE_CAP sounds,
        // so doing this every tick is cheap, and a sound never outlives its waterfall.
        this.pruneOrphans(waterfalls);

        // Throttle these checks as it can be expensive
        if (((++this.soundCheckThrottle) % SOUND_CHECK_INTERVAL) != 0)
            return;

        var player = GameUtils.getPlayer().orElseThrow();
        var eyePosition = player.getEyePosition();
        var desired = this.desiredLocations(waterfalls, player.level(), eyePosition);

        for (var effect : waterfalls.values()) {
            var posIndex = effect.getPosIndex();
            var sound = this.instances.get(posIndex);

            if (desired.contains(posIndex)) {
                if (sound == null) {
                    int tier = Mth.clamp(((WaterfallEffect) effect).getStrength(), 0, ACOUSTICS.length - 1);
                    sound = ACOUSTICS[tier].createBackgroundSoundLoopAt(effect.getPos());
                    this.instances.put(posIndex, sound);
                }

                final boolean inRange = SoundInstanceHandler.inRange(eyePosition, sound, 4);
                final boolean isDone = !this.audioPlayer.isPlaying(sound);

                if (inRange && isDone) {
                    this.audioPlayer.play(sound);
                } else if (!inRange && !isDone) {
                    this.audioPlayer.stop(sound);
                }
            } else if (sound != null) {
                // Not in the list - cross it off
                final var removed = sound;
                this.logger.debug(() -> "[%s] removing sound instance %s".formatted(this.systemName, removed));
                this.audioPlayer.stop(sound);
                this.instances.remove(posIndex);
            }
        }
    }

    private void pruneOrphans(Long2ObjectOpenHashMap<IBlockEffect> waterfalls) {
        if (this.instances.isEmpty())
            return;

        this.instances.values().removeIf(sound -> {
            if (!waterfalls.containsKey(sound.getPos().asLong())) {
                this.logger.debug(() -> "[%s] Orphan sound removed: %s".formatted(this.systemName, sound));
                this.audioPlayer.stop(sound);
                return true;
            }
            return false;
        });
    }

    /**
     * Where waterfall sounds should play. Only SOUND_INSTANCE_CAP sounds are allowed, so when there are more
     * waterfalls than that, the ones with the most impact are chosen: impact is loudness squared over the squared
     * distance from the listener, so loud nearby waterfalls win. Loudness is strength + 2: strength used to count 2
     * more than the drop, and this keeps the ranking (how much a small waterfall counts against a big one) as it was.
     * Only waterfalls of a fluid that makes waterfall sounds are heard; that check is only made for those that would
     * be chosen.
     * <p>
     * The result is a set reused between calls; it is only valid until the next call.
     */
    LongSet desiredLocations(Long2ObjectOpenHashMap<IBlockEffect> waterfalls, Level world, Vec3 listener) {
        this.desiredLocations.clear();
        if (waterfalls.isEmpty())
            return this.desiredLocations;

        // Happy path: no more waterfalls than the cap, so every one that should play, does
        if (waterfalls.size() <= SOUND_INSTANCE_CAP) {
            for (var effect : waterfalls.values()) {
                if (makesSound(world, effect))
                    this.desiredLocations.add(effect.getPosIndex());
            }
            return this.desiredLocations;
        }

        this.loudest.clear();
        for (var e : waterfalls.values()) {
            var effect = (WaterfallEffect) e;
            var loudness = effect.getStrength() + 2;
            var weight = (loudness * loudness) / effect.distanceSqTo(listener);
            if (this.loudest.wouldKeep(weight) && makesSound(world, effect))
                this.loudest.offer(effect.getPosIndex(), weight);
        }

        for (int i = 0; i < this.loudest.size(); i++)
            this.desiredLocations.add(this.loudest.key(i));
        return this.desiredLocations;
    }

    private static boolean makesSound(Level world, IBlockEffect effect) {
        return AbstractEffectSystem.TAG_LIBRARY.is(FluidTags.WATERFALL_SOUND, world.getFluidState(effect.getPos()));
    }
}
