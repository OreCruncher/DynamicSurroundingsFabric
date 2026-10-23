package org.orecruncher.dsurround.effects.systems;

import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.blocks.AbstractBlockEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.sound.IAudioPlayer;

import java.util.function.Predicate;

/**
 * Samples random blocks around the player each tick, as vanilla's animateTick() does, and plays the sounds and
 * starts the effects configured for them. Vanilla's own random display ticks still run; this adds the mod's
 * configured sounds and effects on top.
 * <p>
 * Two instances run, one sampling within {@link #NEAR_RANGE} and one within {@link #FAR_RANGE}, like vanilla.
 * Sounds can come from anywhere sampled. Effects only start within the configured block effect range and within
 * the particle distance of the camera, and follow the Particles option.
 * <p>
 * A third ({@link #beyondFar}) covers a block effect range set past {@link #FAR_RANGE}: it samples out to that range
 * and keeps only what lies beyond the far sampler's reach, so nearer effects don't get denser. It starts effects
 * only; sounds from that far would barely be heard.
 */
public class RandomBlockEffectSystem extends AbstractEffectSystem {

    public static final int NEAR_RANGE = 16;
    public static final int FAR_RANGE = 32;
    private static final int ITERATION_COUNT = 667;

    private final IBlockLibrary blockLibrary;
    private final IAudioPlayer audioPlayer;
    // Fixed, or 0 for the beyond-far sampler, whose range is the block effect range
    private final int range;

    // Reused for every sample
    private final BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();

    public RandomBlockEffectSystem(IModLog logger, Configuration config, IBlockLibrary blockLibrary, IAudioPlayer audioPlayer, int range) {
        this(logger, config, blockLibrary, audioPlayer, range, "Random(%d block range)".formatted(range));
    }

    private RandomBlockEffectSystem(IModLog logger, Configuration config, IBlockLibrary blockLibrary, IAudioPlayer audioPlayer, int range, String name) {
        super(logger, config, name);

        this.blockLibrary = blockLibrary;
        this.audioPlayer = audioPlayer;
        this.range = range;
    }

    /**
     * The sampler for a block effect range past {@link #FAR_RANGE}: effects only, beyond the far sampler's reach.
     * Idle at the default range.
     */
    public static RandomBlockEffectSystem beyondFar(IModLog logger, Configuration config, IBlockLibrary blockLibrary, IAudioPlayer audioPlayer) {
        return new RandomBlockEffectSystem(logger, config, blockLibrary, audioPlayer, 0, "Random(beyond %d blocks)".formatted(FAR_RANGE));
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void tick(Predicate<IBlockEffect> processingPredicate) {
        super.tick(processingPredicate);

        var player = GameUtils.getPlayer().orElseThrow();
        var world = player.level();
        var center = player.blockPosition();

        // Effects are limited to the configured block effect range, the same sphere SystemsScanner keeps them in,
        // so they aren't started only to be dropped the next time the player moves
        final int effectRange = this.config.blockEffects.blockEffectRange;
        final boolean beyondFar = this.range == 0;
        if (beyondFar && effectRange <= FAR_RANGE)
            return;
        final int sampleRange = beyondFar ? effectRange : this.range;
        final double particleRangeSq = AbstractBlockEffect.particleRangeSq();
        // Effects emit particles straight into the particle engine, which skips vanilla's Particles option and
        // distance checks, so apply them here. Minimal starts no effects; Decreased skips about one in three below.
        final ParticleStatus particles = GameUtils.getGameSettings().particles().get();
        final boolean effectsAllowed = particles != ParticleStatus.MINIMAL;
        // Starting effects is all it does
        if (beyondFar && !effectsAllowed)
            return;
        final var camera = GameUtils.getMC().gameRenderer.getMainCamera().getPosition();

        // 667 samples, each several random numbers: look up the thread's generator once, not on every call
        final IRandomizer random = Randomizer.forThisThread();
        final var pos = this.samplePos;
        for (int i = 0; i < ITERATION_COUNT; i++) {
            pos.set(
                    random.triangle(center.getX(), sampleRange),
                    random.triangle(center.getY(), sampleRange),
                    random.triangle(center.getZ(), sampleRange));

            // The far sampler covers this cube; sampling it again would crowd nearer effects
            if (beyondFar && isWithinCube(pos, center, FAR_RANGE))
                continue;

            if (this.hasSystemAtPosition(pos))
                continue;

            var state = world.getBlockState(pos);
            if (Constants.BLOCKS_TO_IGNORE.contains(state.getBlock()))
                continue;

            var info = this.blockLibrary.getBlockInfo(state);
            if (!info.hasSoundsOrEffects())
                continue;

            var producers = info.getEffectProducers();
            if (!producers.isEmpty()
                    && effectsAllowed
                    && isWithin(pos, center, effectRange)
                    && camera.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= particleRangeSq
                    && !(particles == ParticleStatus.DECREASED && random.nextInt(3) == 0)) {
                for (var producer : producers) {
                    var effect = producer.produce(world, state, pos, random);
                    if (effect.isPresent()) {
                        // Keyed by the sampled block, which is what hasSystemAtPosition() checks above. The effect's
                        // own position can differ (a flame jet on a solid block sits in the block above), and keying
                        // by that let a block start a second effect over the first.
                        this.systems.put(pos.asLong(), effect.get());
                        // Only one effect per block position
                        break;
                    }
                }
            }

            if (!beyondFar)
                info.getSoundToPlay(random).ifPresent(s -> this.audioPlayer.play(s.createAtLocation(pos)));
        }
    }

    /**
     * True if {@code pos} is within {@code range} blocks of {@code center}.
     */
    private static boolean isWithin(BlockPos pos, BlockPos center, int range) {
        return pos.distSqr(center) <= (double) range * range;
    }

    /**
     * True if {@code pos} is within {@code range} blocks of {@code center} on every axis.
     */
    private static boolean isWithinCube(BlockPos pos, BlockPos center, int range) {
        return Math.abs(pos.getX() - center.getX()) <= range
                && Math.abs(pos.getY() - center.getY()) <= range
                && Math.abs(pos.getZ() - center.getZ()) <= range;
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
