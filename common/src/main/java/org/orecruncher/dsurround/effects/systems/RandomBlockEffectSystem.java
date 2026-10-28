package org.orecruncher.dsurround.effects.systems;

import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.blocks.AbstractBlockEffect;
import org.orecruncher.dsurround.effects.blocks.producers.BlockParticleEffectProducer;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.profiling.RandomBlockSamples;
import org.orecruncher.dsurround.sound.IAudioPlayer;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Samples random blocks around the player each tick, as vanilla's animateTick() does, and plays the sounds and
 * starts the effects configured for them. Vanilla's own random display ticks still run; this adds the mod's
 * configured sounds and effects on top.
 * <p>
 * Where it looks is {@link RandomBlockSampler}'s: vanilla's near and far spreads, which can play sounds and start
 * effects, and past them a tail out to the block effect range, which only starts effects (sounds from that far would
 * barely be heard). Effects only start within the configured block effect range and within the particle distance
 * of the camera, and follow the Particles option. One map holds them all, so a block starts one effect at a time.
 * <p>
 * While /dsprofile records, what it did is counted by distance ({@link RandomBlockSamples}).
 */
public class RandomBlockEffectSystem extends AbstractEffectSystem {

    private final IBlockLibrary blockLibrary;
    private final IAudioPlayer audioPlayer;

    // Reused for every sample
    private final BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();

    // Set at the start of each tick for sample()
    private Level world;
    private BlockPos center;
    private Vec3 camera;
    private int effectRange;
    private double particleRangeSq;
    private ParticleStatus particles;
    private IRandomizer random;
    // Counts for the recording, by distance; null while nothing records
    private int[] counts;
    private final int[] countsWhileRecording = new int[RandomBlockSamples.SHELLS * RandomBlockSamples.KINDS];

    public RandomBlockEffectSystem(IModLog logger, Configuration config, IBlockLibrary blockLibrary, IAudioPlayer audioPlayer) {
        super(logger, config, "Random");

        this.blockLibrary = blockLibrary;
        this.audioPlayer = audioPlayer;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void tick(Predicate<IBlockEffect> processingPredicate) {
        super.tick(processingPredicate);

        var player = GameUtils.getPlayer().orElseThrow();
        this.world = player.level();
        this.center = player.blockPosition();

        // Effects are limited to the configured block effect range, the same sphere SystemsScanner keeps them in,
        // so they aren't started only to be dropped the next time the player moves
        this.effectRange = this.config.blockEffects.blockEffectRange;
        this.particleRangeSq = AbstractBlockEffect.particleRangeSq();
        // Effects emit particles straight into the particle engine, which skips vanilla's Particles option and
        // distance checks, so apply them here. Minimal starts no effects; Decreased skips about one in three below.
        this.particles = GameUtils.getGameSettings().particles().get();
        this.camera = GameUtils.getMC().gameRenderer.getMainCamera().getPosition();
        // Thousands of samples, each several random numbers: look up the thread's generator once, not on every call
        this.random = Randomizer.forThisThread();
        this.counts = null;
        if (RandomBlockSamples.isRecording()) {
            this.counts = this.countsWhileRecording;
            Arrays.fill(this.counts, 0);
        }

        final var pos = this.samplePos;
        for (int i = 0; i < RandomBlockSampler.PAIRS; i++) {
            RandomBlockSampler.near(this.random, this.center, pos);
            this.sample(pos, true, RandomBlockSamples.SAMPLES);
            RandomBlockSampler.far(this.random, this.center, pos);
            this.sample(pos, true, RandomBlockSamples.SAMPLES);
        }

        // The tail only starts effects
        if (this.particles != ParticleStatus.MINIMAL) {
            int draws = RandomBlockSampler.tailDraws(this.random, this.effectRange);
            for (int i = 0; i < draws; i++)
                if (RandomBlockSampler.tail(this.random, this.center, this.effectRange, pos))
                    this.sample(pos, false, RandomBlockSamples.TAIL);
        }

        if (this.counts != null)
            RandomBlockSamples.ticked(this.effectRange, this.counts);

        // Nothing to hold on to between ticks
        this.world = null;
    }

    /**
     * @param kind which count the sample goes in while recording: {@link RandomBlockSamples#SAMPLES} or
     *             {@link RandomBlockSamples#TAIL}
     */
    private void sample(BlockPos pos, boolean withSounds, int kind) {
        this.count(pos, kind);
        if (this.hasSystemAtPosition(pos))
            return;

        var state = this.world.getBlockState(pos);
        if (Constants.BLOCKS_TO_IGNORE.contains(state.getBlock()))
            return;

        var info = this.blockLibrary.getBlockInfo(state);
        if (!info.hasSoundsOrEffects())
            return;

        var producers = info.getEffectProducers();
        if (!producers.isEmpty())
            this.count(pos, RandomBlockSamples.EFFECT_BLOCKS);
        if (!producers.isEmpty()
                && this.particles != ParticleStatus.MINIMAL
                && isWithin(pos, this.center, this.effectRange)
                && this.camera.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= this.particleRangeSq
                && !(this.particles == ParticleStatus.DECREASED && this.random.nextInt(3) == 0)) {
            for (var producer : producers) {
                int particles = BlockParticleEffectProducer.spawned();
                var effect = producer.produce(this.world, state, pos, this.random);
                this.count(pos, RandomBlockSamples.PARTICLES, BlockParticleEffectProducer.spawned() - particles);
                if (effect.isPresent()) {
                    // Keyed by the sampled block, which is what hasSystemAtPosition() checks above. The effect's
                    // own position can differ (a flame jet on a solid block sits in the block above), and keying
                    // by that let a block start a second effect over the first.
                    this.systems.put(pos.asLong(), effect.get());
                    this.count(pos, RandomBlockSamples.STARTS);
                    // Only one effect per block position
                    break;
                }
            }
        }

        if (withSounds)
            info.getSoundToPlay(this.random).ifPresent(s -> {
                this.audioPlayer.play(s.createAtLocation(pos));
                this.count(pos, RandomBlockSamples.SOUNDS);
            });
    }

    private void count(BlockPos pos, int kind) {
        this.count(pos, kind, 1);
    }

    private void count(BlockPos pos, int kind, int amount) {
        if (this.counts != null && amount != 0)
            this.counts[RandomBlockSamples.index(pos, this.center, kind)] += amount;
    }

    /**
     * True if {@code pos} is within {@code range} blocks of {@code center}.
     */
    private static boolean isWithin(BlockPos pos, BlockPos center, int range) {
        return pos.distSqr(center) <= (double) range * range;
    }

    @Override
    public boolean wantsBlockScans() {
        // Blocks are sampled at random in tick(), so the scanner needn't report each one
        return false;
    }

    @Override
    public void blockScan(Level world, BlockState state, BlockPos pos) {
        // Do nothing - everything is in the tick
    }
}
