package org.orecruncher.dsurround.processing.scanner;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.lib.BlockPosUtil;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.scanner.CuboidScanner;
import org.orecruncher.dsurround.lib.scanner.ScanContext;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SystemsScanner extends CuboidScanner {

    private static final Predicate<IBlockEffect> EFFECT_PREDICATE = system -> {
        system.tick();
        return system.isDone();
    };

    private final Configuration config;
    private final ObjectArray<IEffectSystem> systems = new ObjectArray<>();

    private int lastRange;

    public SystemsScanner(Configuration config, ScanContext locus) {
        super(locus, "SystemsScanner", config.blockEffects.blockEffectRange);

        this.config = config;
        this.lastRange = config.blockEffects.blockEffectRange;
    }

    public void addEffectSystem(IEffectSystem system) {
        this.systems.add(system);
    }

    public void resetFullScan() {
        super.resetFullScan();
        this.systems.forEach(IEffectSystem::clear);
    }

    @Override
    public void tick() {
         super.tick();

        // If the range changed, we need to reset all effects in process
        if (this.lastRange != this.config.blockEffects.blockEffectRange) {
            this.lastRange = this.config.blockEffects.blockEffectRange;
            this.setRange(this.lastRange);
            return;
        }

        var player = GameUtils.getPlayer().orElseThrow();
        final BlockPos current = player.blockPosition();
        final boolean sittingStill = this.lastPos.equals(current);
        this.lastPos = current;

        Predicate<IBlockEffect> pred;

        if (!sittingStill) {
            var range = this.config.blockEffects.blockEffectRange;
            var minPoint = current.offset(-range, -range, -range);
            var maxPoint = current.offset(range, range, range);

            pred = system -> {
                if (!BlockPosUtil.contains(system.getPos(), minPoint, maxPoint)) {
                    system.remove();
                } else {
                    system.tick();
                }
                return system.isDone();
            };
        } else {
            pred = EFFECT_PREDICATE;
        }

        this.processIfEnabled(true, system -> system.tick(pred));
    }

    protected void processIfEnabled(boolean clearSystems, Consumer<IEffectSystem> systemConsumer) {
        for (var system : this.systems)
            if (system.isEnabled())
                systemConsumer.accept(system);
            else if(clearSystems)
                system.clear();
    }

    @Override
    public boolean doBlockUnscan() {
        return true;
    }

    @Override
    public void blockScan(Level world, BlockState state, BlockPos pos, IRandomizer rand) {
        this.processIfEnabled(false, system -> system.blockScan(world, state, pos));
    }

    @Override
    public void blockUnscan(Level world, BlockState state, BlockPos pos, IRandomizer rand) {
        this.processIfEnabled(false, system -> system.blockUnscan(world, state, pos));
    }

    public void gatherDiagnostics(Collection<String> output) {
        this.systems.forEach(system -> {
            var text = ChatFormatting.LIGHT_PURPLE + system.gatherDiagnostics();
            if (!system.isEnabled())
                text += ChatFormatting.RED + " (disabled)";
            output.add(text);
        });
    }
}
