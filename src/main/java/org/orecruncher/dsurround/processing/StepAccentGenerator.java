package org.orecruncher.dsurround.processing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.processing.accents.FootstepAccents;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.ISoundFactory;

/**
 * Listens to the ENTITY_STEP_EVENT stream for opportunities to accentuate an Entities
 * step effect.
 */
public class StepAccentGenerator extends AbstractClientHandler {

    private final IAudioPlayer audioPlayer;
    private final FootstepAccents footstepAccents;
    private final ObjectArray<ISoundFactory> accents = new ObjectArray<>(4);

    public StepAccentGenerator(Configuration config, IAudioPlayer audioPlayer, FootstepAccents footstepAccents, IModLog logger) {
        super("Step Accent", config, logger);
        this.audioPlayer = audioPlayer;
        this.footstepAccents = footstepAccents;
        ClientEventHooks.ENTITY_STEP_EVENT.register(this::footStepGenerated);
    }

    protected void footStepGenerated(Entity entity, BlockPos blockPos, BlockState blockState) {
        if (entity instanceof LivingEntity living) {
            if (living.isSilent() || living.isSpectator())
                return;

            this.accents.clear();
            this.footstepAccents.collect(living, blockPos, blockState, this.accents);
            this.accents.forEach(factory -> this.audioPlayer.play(factory.createAtEntity(living)));
        }
    }
}
