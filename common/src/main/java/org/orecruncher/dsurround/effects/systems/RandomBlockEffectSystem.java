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
 * vanilla's particle distance of the camera, and follow the Particles option.
 */
public class RandomBlockEffectSystem extends AbstractEffectSystem {

    protected static final IRandomizer RANDOM = Randomizer.current();

    public static final int NEAR_RANGE = 16;
    public static final int FAR_RANGE = 32;
    private static final int ITERATION_COUNT = 667;

    private final IBlockLibrary blockLibrary;
    private final IAudioPlayer audioPlayer;
    private final int range;

    // Reused for every sample
    private final BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();

    public RandomBlockEffectSystem(IModLog logger, Configuration config, IBlockLibrary blockLibrary, IAudioPlayer audioPlayer, int range) {
        super(logger, config, "Random(%d block range)".formatted(range));

        this.blockLibrary = blockLibrary;
        this.audioPlayer = audioPlayer;
        this.range = range;
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

        // Effects are limited to the configured block effect range, the same box SystemsScanner keeps them in, so
        // they aren't started only to be dropped the next time the player moves
        final int effectRange = this.config.blockEffects.blockEffectRange;
        // Effects emit particles straight into the particle engine, which skips vanilla's Particles option and
        // distance checks, so apply them here. Minimal starts no effects; Decreased skips about one in three below.
        final ParticleStatus particles = GameUtils.getGameSettings().particles().get();
        final boolean effectsAllowed = particles != ParticleStatus.MINIMAL;
        final var camera = GameUtils.getMC().gameRenderer.getMainCamera().getPosition();

        final var pos = this.samplePos;
        for (int i = 0; i < ITERATION_COUNT; i++) {
            pos.set(
                    RANDOM.triangle(center.getX(), this.range),
                    RANDOM.triangle(center.getY(), this.range),
                    RANDOM.triangle(center.getZ(), this.range));

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
                    && camera.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= AbstractBlockEffect.PARTICLE_RANGE_SQ
                    && !(particles == ParticleStatus.DECREASED && RANDOM.nextInt(3) == 0)) {
                for (var producer : producers) {
                    var effect = producer.produce(world, state, pos, RANDOM);
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

            info.getSoundToPlay(RANDOM).ifPresent(s -> this.audioPlayer.play(s.createAtLocation(pos)));
        }
    }

    /**
     * True if {@code pos} is within {@code range} blocks of {@code center} on every axis.
     */
    private static boolean isWithin(BlockPos pos, BlockPos center, int range) {
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
