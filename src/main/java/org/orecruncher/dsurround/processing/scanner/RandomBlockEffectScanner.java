package org.orecruncher.dsurround.processing.scanner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.config.BlockLibrary;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.scanner.RandomScanner;
import org.orecruncher.dsurround.lib.scanner.ScanContext;
import org.orecruncher.dsurround.processing.misc.BlockEffectManager;
import org.orecruncher.dsurround.sound.MinecraftAudioPlayer;
import org.orecruncher.dsurround.sound.SoundFactory;

import java.util.Collection;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class RandomBlockEffectScanner extends RandomScanner {

    public static final int NEAR_RANGE = 16;
    public static final int FAR_RANGE = 32;
    private static final int ITERATION_COUNT = 667;

    private final BlockEffectManager effectManager;

    public RandomBlockEffectScanner(final ScanContext locus, final BlockEffectManager manager,  int range) {
        super(locus, "RandomBlockScanner: " + range, range, ITERATION_COUNT);

        this.effectManager = manager;
    }

    @Override
    protected boolean interestingBlock(final BlockState state) {
        return BlockLibrary.getBlockInfo(state).hasSoundsOrEffects();
    }

    @Override
    public void blockScan(final BlockState state, final BlockPos pos, final Random rand) {
        var info = BlockLibrary.getBlockInfo(state);

        final Collection<IBlockEffectProducer> effects = info.getEffectProducers();
        if (effects.size() > 0 && this.effectManager.okToSpawn(pos)) {
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

        var sound = info.getSoundToPlay(rand);
        if (sound != null) {
            var instance = SoundFactory.createAtLocation(sound, pos);
            MinecraftAudioPlayer.INSTANCE.play(instance);
        }
    }

}