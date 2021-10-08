package org.orecruncher.dsurround.processing.scanner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.config.BlockLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scanner.RandomScanner;
import org.orecruncher.dsurround.lib.scanner.ScanContext;
import org.orecruncher.dsurround.sound.SoundFactory;

import java.util.Random;

@Environment(EnvType.CLIENT)
public class RandomBlockEffectScanner extends RandomScanner {

    private static final int ITERATION_COUNT = 667;

    public static final int NEAR_RANGE = 16;
    public static final int FAR_RANGE = 32;

    public RandomBlockEffectScanner(final ScanContext locus, final int range) {
        super(locus, "RandomBlockScanner: " + range, range, ITERATION_COUNT);
    }

    @Override
    protected boolean interestingBlock( final BlockState state) {
        return BlockLibrary.getBlockInfo(state).hasSounds();
    }

    @Override
    public void blockScan(final BlockState state, final BlockPos pos, final Random rand) {
        var info = BlockLibrary.getBlockInfo(state);
        var sound = info.getSoundToPlay(rand);

        if (sound != null) {
            var instance = SoundFactory.createAtLocation(sound, pos);
            GameUtils.getSoundHander().play(instance);
        }
    }

}