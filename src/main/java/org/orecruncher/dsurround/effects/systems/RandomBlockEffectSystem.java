package org.orecruncher.dsurround.effects.systems;

import com.google.common.collect.AbstractIterator;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.random.LCGRandom;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;
import org.orecruncher.dsurround.sound.IAudioPlayer;

import java.util.Random;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Basically does what doRandomBlockDisplayTicks() does for vanilla.
 */
public class RandomBlockEffectSystem extends AbstractEffectSystem {

    protected static final Random RANDOM = XorShiftRandom.current();

    // These block states are to be ignored as they have no value for effects
    // within the system.
    protected static final Set<BlockState> BLOCKSTATES_TO_IGNORE = new ReferenceArraySet<>(3);

    static {
        BLOCKSTATES_TO_IGNORE.add(Blocks.VOID_AIR.getDefaultState());
        BLOCKSTATES_TO_IGNORE.add(Blocks.CAVE_AIR.getDefaultState());
        BLOCKSTATES_TO_IGNORE.add(Blocks.AIR.getDefaultState());
    }

    public static final int NEAR_RANGE = 16;
    public static final int FAR_RANGE = 32;
    private static final int ITERATION_COUNT = 667;

    // Use LCG because it is FAST. A random block scanner system will be
    // checking a lot of block positions per tick, so we avoid "true"
    // random for performance.
    private final LCGRandom lcg = new LCGRandom();
    private final IBlockLibrary blockLibrary;
    private final IAudioPlayer audioPlayer;
    private final int range;

    public RandomBlockEffectSystem(IModLog logger, Configuration config, IBlockLibrary blockLibrary, IAudioPlayer audioPlayer, int range) {
        super(logger, config, "Random(%d block)".formatted(range));

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
        var world = player.getEntityWorld();

        var iterator = iterateRandomly(this.lcg, ITERATION_COUNT, player.getBlockPos(), this.range);

        for (var blockPos : iterator) {
            if (this.hasSystemAtPosition(blockPos))
                continue;

            var state = world.getBlockState(blockPos);
            if (BLOCKSTATES_TO_IGNORE.contains(state))
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
    public void blockScan(World world, BlockState state, BlockPos pos) {
        // Do nothing - everything is in the tick
    }

    protected Iterable<BlockPos> iterateRandomly(LCGRandom random, int count, BlockPos center, int range) {
        return () -> new AbstractIterator<>() {
            final BlockPos.Mutable pos = new BlockPos.Mutable();
            final LCGRandom lcg = random;
            int remaining = count;

            private int randomRange(int range) {
                return this.lcg.nextInt(range) - this.lcg.nextInt(range);
            }

            protected BlockPos computeNext() {
                if (this.remaining <= 0) {
                    return this.endOfData();
                } else {
                    --this.remaining;
                    return this.pos.set(
                            center,
                            this.randomRange(range),
                            this.randomRange(range),
                            this.randomRange(range));
                }
            }
        };
    }
}
