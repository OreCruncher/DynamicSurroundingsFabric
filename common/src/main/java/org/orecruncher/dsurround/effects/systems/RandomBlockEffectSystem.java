package org.orecruncher.dsurround.effects.systems;

import com.google.common.collect.AbstractIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.sound.IAudioPlayer;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Basically does what doRandomBlockDisplayTicks() does for vanilla.
 */
public class RandomBlockEffectSystem extends AbstractEffectSystem {

    protected static final IRandomizer RANDOM = Randomizer.current();

    public static final int NEAR_RANGE = 16;
    public static final int FAR_RANGE = 32;
    private static final int ITERATION_COUNT = 667;

    private final IBlockLibrary blockLibrary;
    private final IAudioPlayer audioPlayer;
    private final int range;

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

        var iterator = iterateRandomly(Randomizer.current(), ITERATION_COUNT, player.blockPosition(), this.range);

        for (var blockPos : iterator) {
            if (this.hasSystemAtPosition(blockPos))
                continue;

            var state = world.getBlockState(blockPos);
            if (Constants.BLOCKS_TO_IGNORE.contains(state.getBlock()))
                continue;

            var info = this.blockLibrary.getBlockInfo(state);
            if (!info.hasSoundsOrEffects())
                continue;

            final Collection<IBlockEffectProducer> effects = info.getEffectProducers();
            if (!effects.isEmpty()) {
                for (var be : effects) {
                    var effect = be.produce(world, state, blockPos, RANDOM);
                    if (effect.isPresent()) {
                        var e = effect.get();
                        this.systems.put(e.getPosIndex(), e);
                        // Only one effect per block position
                        break;
                    }
                }
            }

            info.getSoundToPlay(RANDOM).ifPresent(s -> {
                var instance = s.createAtLocation(blockPos);
                this.audioPlayer.play(instance);
            });
        }
    }

    @Override
    public void blockScan(Level world, BlockState state, BlockPos pos) {
        // Do nothing - everything is in the tick
    }

    protected Iterable<BlockPos> iterateRandomly(IRandomizer random, int count, BlockPos center, int range) {
        return () -> new AbstractIterator<>() {
            final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int remaining = count;

            protected BlockPos computeNext() {
                if (this.remaining <= 0) {
                    return this.endOfData();
                } else {
                    --this.remaining;
                    return this.pos.set(
                            random.triangle(center.getX(), range),
                            random.triangle(center.getY(), range),
                            random.triangle(center.getZ(), range));
                }
            }
        };
    }
}
