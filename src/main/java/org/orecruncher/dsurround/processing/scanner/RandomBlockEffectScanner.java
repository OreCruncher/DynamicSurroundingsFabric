package org.orecruncher.dsurround.processing.scanner;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.scanner.RandomScanner;
import org.orecruncher.dsurround.lib.scanner.ScanContext;
import org.orecruncher.dsurround.processing.misc.BlockEffectManager;
import org.orecruncher.dsurround.sound.IAudioPlayer;

import java.util.Collection;
import java.util.Random;

public class RandomBlockEffectScanner extends RandomScanner {

    public static final int NEAR_RANGE = 16;
    public static final int FAR_RANGE = 32;
    private static final int ITERATION_COUNT = 667;

    private final BlockEffectManager effectManager;
    private final IBlockLibrary blockLibrary;
    private final IAudioPlayer audioPlayer;

    public RandomBlockEffectScanner(final ScanContext locus, final IBlockLibrary blockLibrary, IAudioPlayer audioPlayer, final BlockEffectManager manager, int range) {
        super(locus, "RandomBlockScanner: " + range, range, ITERATION_COUNT);

        this.effectManager = manager;
        this.blockLibrary = blockLibrary;
        this.audioPlayer = audioPlayer;
    }

    @Override
    protected boolean interestingBlock(final BlockState state) {
        return this.blockLibrary.getBlockInfo(state).hasSoundsOrEffects();
    }

    @Override
    public void blockScan(final BlockState state, final BlockPos pos, final Random rand) {
        var info = this.blockLibrary.getBlockInfo(state);

        final Collection<IBlockEffectProducer> effects = info.getEffectProducers();
        if (!effects.isEmpty() && this.effectManager.okToSpawn(pos)) {
            var world = this.locus.getWorld();
            for (var be : effects) {
                var effect = be.produce(world, state, pos, rand);
                if (effect.isPresent()) {
                    this.effectManager.add(effect.get());
                    // Only one effect per block position
                    break;
                }
            }
        }

        info.getSoundToPlay(rand).ifPresent(s -> {
            var instance = s.createAtLocation(pos);
            this.audioPlayer.play(instance);
        });
    }
}