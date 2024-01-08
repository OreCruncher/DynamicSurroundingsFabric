package org.orecruncher.dsurround.eventing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;
import org.orecruncher.dsurround.lib.math.ITimer;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public final class ClientEventHooks {
    /**
     * Used to collect diagnostic information for display in the debug HUD
     */
    public static final IPhasedEvent<CollectDiagnosticsEvent> COLLECT_DIAGNOSTICS = EventingFactory.createPrioritizedEvent();

    /**
     * Fired when block state updates are received clientside.  Results are coalesced for efficiency.
     */
    public static final IPhasedEvent<BlockUpdateEvent> BLOCK_UPDATE = EventingFactory.createPrioritizedEvent();

    /**
     * Fired when an Entity is detected as generating a step sound.
     */
    public static final IPhasedEvent<EntityStepEvent> ENTITY_STEP_EVENT = EventingFactory.createPrioritizedEvent();

    public static final class CollectDiagnosticsEvent {
        public enum Panel {
            Header(false),
            Handlers(false),
            Timers(false),
            Environment(false),
            Emitters(true),
            Sounds(false),
            BlockView(true),
            FluidView(true),
            Survey(true),
            Misc(true);

            private final boolean addHeader;
            Panel(boolean addHeader) {
                this.addHeader = addHeader;
            }

            public boolean addHeader() {
                return this.addHeader;
            }
        }

        private final Map<Panel, ObjectArray<String>> data = new EnumMap<>(Panel.class);

        public void add(ITimer timer) {
            this.add(Panel.Timers, timer.toString());
        }

        public void add(Panel panel, String text) {
            this.getPanelText(panel).add(text);
        }

        public Collection<String> getPanelText(Panel panel) {
            return this.data.computeIfAbsent(panel, ignored -> new ObjectArray<>());
        }
    }

    public record BlockUpdateEvent(Collection<BlockPos> updates){

    }

    public record EntityStepEvent(Entity entity, BlockPos blockPos, BlockState blockState) {

    }
}
