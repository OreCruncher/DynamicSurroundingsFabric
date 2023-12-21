package org.orecruncher.dsurround.eventing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;
import org.orecruncher.dsurround.lib.math.ITimer;
import org.orecruncher.dsurround.lib.math.TimerEMA;

import java.util.Collection;

@Environment(EnvType.CLIENT)
public final class ClientEventHooks {
    /**
     * Used to collect diagnostic information for display in the debug HUD
     */
    public static final IPhasedEvent<CollectDiagnosticsEvent> COLLECT_DIAGNOSTICS = EventingFactory.createPrioritizedEvent();


    /**
     * Fired when block state updates are received clientside.  Results are coalesced for efficiency.
     */
    public static IPhasedEvent<BlockUpdateEvent> BLOCK_UPDATE = EventingFactory.createPrioritizedEvent();

    public static final class CollectDiagnosticsEvent {
        public final ObjectArray<String> left = new ObjectArray<>();
        public final ObjectArray<String> right = new ObjectArray<>();
        public final ObjectArray<ITimer> timers = new ObjectArray<>();
    }

    public record BlockUpdateEvent(Collection<BlockPos> updates){

    }
}
