package org.orecruncher.dsurround.eventing;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

import java.util.Collection;

public final class ClientEventHooks {

    public static final IPhasedEvent<ICollectDiagnostics> COLLECT_DIAGNOSTICS_EVENT = EventingFactory.createPrioritizedEvent();
    public static final IPhasedEvent<IBlockUpdates> BLOCK_UPDATES_EVENT = EventingFactory.createPrioritizedEvent();
    public static final IPhasedEvent<IEntityStep> ENTITY_STEP_EVENT = EventingFactory.createPrioritizedEvent();
    public static final IPhasedEvent<IFogRender> FOG_RENDER_EVENT = EventingFactory.createPrioritizedEvent();

    /**
     * Used to collect diagnostic information for display in the debug HUD
     */
    @FunctionalInterface
    public interface ICollectDiagnostics {
        void onCollect(CollectDiagnosticsEvent event);
    }

    /**
     * Fired when block state updates are received clientside.  Results are coalesced for efficiency.
     */
    @FunctionalInterface
    public interface IBlockUpdates {
        void onBlockUpdates(Collection<BlockPos> blockPositions);
    }

    /**
     * Fired when an Entity is detected as generating a step sound.
     */
    @FunctionalInterface
    public interface IEntityStep {
        void onStep(Entity entity, BlockPos stepPosition, BlockState blockState);
    }

    /** Fired when fog is about to be rendered.
     */
    @FunctionalInterface
    public interface IFogRender {
        void onRenderFog(FogRenderer.FogData data, float renderDistance, float partialTick);
    }
}
