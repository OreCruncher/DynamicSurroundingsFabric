package org.orecruncher.dsurround.eventing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Fired when an Entity is detected as generating a step sound.
 */
@GenerateInvoker
@FunctionalInterface
public interface IEntityStep {

    IPhasedEvent<IEntityStep> EVENT = EventingFactory.createPrioritizedEvent(IEntityStepInvoker::create);

    void onStep(Entity entity, BlockPos stepPosition, BlockState blockState);
}
