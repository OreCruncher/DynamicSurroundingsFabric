package org.orecruncher.dsurround.effects.systems;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.sound.IAudioPlayer;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Waterfalls: an effect wherever falling water lands (see {@link WaterfallEffect}), with its splashes, mist and foam,
 * and the sound of the nearest and biggest of them (see {@link WaterfallSounds}). This finds them as blocks are
 * scanned, and keeps the effects and their sounds in step.
 */
public class WaterfallEffectSystem extends AbstractEffectSystem implements IEffectSystem {

    private final WaterfallSounds sounds;

    // Waterfalls found this tick whose column height changed; reused between ticks
    private final LongArrayList staleStrengths = new LongArrayList();

    public WaterfallEffectSystem(IModLog logger, Configuration config, IAudioPlayer audioPlayer) {
        super(logger, config, "Waterfall");
        this.sounds = new WaterfallSounds(logger, this.systemName, audioPlayer);
    }

    @Override
    public boolean isEnabled() {
        return this.config.blockEffects.waterfallsEnabled;
    }

    @Override
    public int getDiagnosticColor() {
        return ColorPalette.TURQUOISE.getValue();
    }

    @Override
    public void describeEffect(IBlockEffect effect, Consumer<String> lines) {
        super.describeEffect(effect, lines);
        lines.accept(this.sounds.hasSound(effect.getPosIndex()) ? "sound: playing" : "sound: none");
        if (effect instanceof WaterfallEffect waterfall) {
            lines.accept("splash limit " + waterfall.particleLimit);
            if (WaterfallEffect.WIP_OPTIONS.enableWaterfallMist)
                lines.accept("mist");
            if (WaterfallEffect.WIP_OPTIONS.enableWaterStepFroth)
                lines.accept("foam");
        }
    }

    @Override
    public void clear() {
        super.clear();
        this.sounds.stopAll();
    }

    @Override
    public void tick(Predicate<IBlockEffect> processingPredicate) {
        // Process the waterfall effects first, which prunes those that have gone, before the sounds are brought up
        // to date with them
        super.tick(processingPredicate);
        this.refreshStaleStrengths();

        if (this.isEnabled() && this.config.blockEffects.enableWaterfallSounds)
            this.sounds.update(this.systems);
        else
            this.sounds.stopAll();
    }

    /**
     * Replaces waterfalls whose column has grown or shrunk since they were created, so the splashes and sound match
     * the new strength. Each waterfall recounts its column as part of its periodic validity check; this only acts
     * on the ones that found a change. The old sound is stopped here and the next sound pass starts one for the new
     * strength.
     */
    private void refreshStaleStrengths() {
        for (var effect : this.systems.values()) {
            if (((WaterfallEffect) effect).isStrengthStale())
                this.staleStrengths.add(effect.getPosIndex());
        }
        if (this.staleStrengths.isEmpty())
            return;

        for (int i = 0; i < this.staleStrengths.size(); i++) {
            long posLong = this.staleStrengths.getLong(i);
            var old = (WaterfallEffect) this.systems.get(posLong);
            var replacement = old.rebuild();
            old.remove();
            this.onRemoveSystem(posLong);
            if (replacement != null)
                this.systems.put(posLong, replacement);
        }
        this.staleStrengths.clear();
    }

    @Override
    public void blockScan(Level world, BlockState state, BlockPos pos) {
        if (WaterfallEffect.canForm(world, state, pos)) {
            // Ignore if a waterfall is already present. This scan is due to a block update of some sort.
            if (this.hasSystemAtPosition(pos))
                return;
            this.systems.put(pos.asLong(), WaterfallEffect.create(world, state, pos));
        } else {
            // The block no longer supports a waterfall: drop the effect and, through onRemoveSystem, its sound
            this.blockUnscan(world, state, pos);
        }
    }

    @Override
    protected void onRemoveSystem(long posLong) {
        // Keeps the sounds in step with the effects
        super.onRemoveSystem(posLong);
        this.sounds.stop(posLong);
    }
}
