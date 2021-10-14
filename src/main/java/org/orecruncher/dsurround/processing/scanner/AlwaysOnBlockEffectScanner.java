package org.orecruncher.dsurround.processing.scanner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.config.BlockLibrary;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.scanner.CuboidScanner;
import org.orecruncher.dsurround.lib.scanner.ScanContext;
import org.orecruncher.dsurround.processing.misc.BlockEffectManager;

import java.util.Collection;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class AlwaysOnBlockEffectScanner extends CuboidScanner {

    private final BlockEffectManager effectManager;

    public AlwaysOnBlockEffectScanner(final ScanContext locus, BlockEffectManager manager, final int range) {
        super(locus, "AlwaysOnBlockEffectScanner", range, 0);

        this.effectManager = manager;
    }

    @Override
    protected boolean interestingBlock(final BlockState state) {
        return BlockLibrary.getBlockInfo(state).hasAlwaysOnEffects();
    }

    @Override
    public void blockScan(final BlockState state, final BlockPos pos, final Random rand) {
        final Collection<IBlockEffectProducer> effects = BlockLibrary.getBlockInfo(state).getAlwaysOnEffectProducers();
        if (effects.size() > 0 && this.effectManager.okToSpawn(pos)) {
            final World world = this.locus.getWorld();
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