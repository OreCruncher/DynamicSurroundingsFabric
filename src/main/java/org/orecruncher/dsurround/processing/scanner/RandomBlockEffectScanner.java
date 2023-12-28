package org.orecruncher.dsurround.processing.scanner;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.scanner.RandomScanner;
import org.orecruncher.dsurround.lib.scanner.ScanContext;
import org.orecruncher.dsurround.sound.IAudioPlayer;

import java.util.Collection;
import java.util.Random;

public class RandomBlockEffectScanner extends RandomScanner {

    public static final int NEAR_RANGE = 16;
    public static final int FAR_RANGE = 32;
    private static final int ITERATION_COUNT = 667;

    private final AlwaysOnBlockEffectScanner alwaysOn;
    private final IBlockLibrary blockLibrary;
    private final IAudioPlayer audioPlayer;

    public RandomBlockEffectScanner(ScanContext locus, IBlockLibrary blockLibrary, IAudioPlayer audioPlayer, AlwaysOnBlockEffectScanner alwaysOn, int range) {
        super(locus, "RandomBlockScanner: " + range, range, ITERATION_COUNT);

        this.alwaysOn = alwaysOn;
        this.blockLibrary = blockLibrary;
        this.audioPlayer = audioPlayer;
    }

    @Override
    public void blockScan(final BlockState state, final BlockPos pos, final Random rand) {
        var info = this.blockLibrary.getBlockInfo(state);
        if (!info.hasSoundsOrEffects())
            return;

        final Collection<IBlockEffectProducer> effects = info.getEffectProducers();
        if (!effects.isEmpty() && this.alwaysOn.okToSpawn(pos)) {
            var world = this.locus.world().get();
            for (var be : effects) {
                var effect = be.produce(world, state, pos, rand);
                if (effect.isPresent()) {
                    this.alwaysOn.add(effect.get());
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