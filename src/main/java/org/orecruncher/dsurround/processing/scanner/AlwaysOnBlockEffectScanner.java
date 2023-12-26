package org.orecruncher.dsurround.processing.scanner;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.scanner.CuboidScanner;
import org.orecruncher.dsurround.lib.scanner.ScanContext;
import org.orecruncher.dsurround.processing.misc.BlockEffectManager;

import java.util.Collection;
import java.util.Random;

public class AlwaysOnBlockEffectScanner extends CuboidScanner {

    private final IBlockLibrary blockLibrary;
    private final BlockEffectManager effectManager;

    public AlwaysOnBlockEffectScanner(final ScanContext locus, IBlockLibrary blockLibrary, BlockEffectManager manager, final int range) {
        super(locus, "AlwaysOnBlockEffectScanner", range, 0);

        this.blockLibrary = blockLibrary;
        this.effectManager = manager;
    }

    @Override
    protected boolean interestingBlock(final BlockState state) {
        return this.blockLibrary.getBlockInfo(state).hasAlwaysOnEffects();
    }

    @Override
    public void blockScan(final BlockState state, final BlockPos pos, final Random rand) {
        final Collection<IBlockEffectProducer> effects = this.blockLibrary.getBlockInfo(state).getAlwaysOnEffectProducers();
        if (!effects.isEmpty() && this.effectManager.okToSpawn(pos)) {
            final World world = this.locus.world().get();
            for (var be : effects) {
                var effect = be.produce(world, state, pos, rand);
                if (effect.isPresent()) {
                    this.effectManager.add(effect.get());
                    // Only one effect per block
                    break;
                }
            }
        }
    }
}