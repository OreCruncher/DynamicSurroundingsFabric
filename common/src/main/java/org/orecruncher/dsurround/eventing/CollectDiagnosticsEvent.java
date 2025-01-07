package org.orecruncher.dsurround.eventing;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.math.ITimer;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public final class CollectDiagnosticsEvent {
    public enum Section {
        Header(false),
        Systems,
        Particles,
        Timers(false),
        Environment(false),
        Emitters,
        Sounds,
        BlockView,
        FluidView,
        EntityView,
        HeldItem,
        Survey,
        Misc;

        private final boolean addHeader;

        Section() {
            this(true);
        }

        Section(boolean addHeader) {
            this.addHeader = addHeader;
        }

        public boolean addHeader() {
            return this.addHeader;
        }
    }

    private final Map<Section, Collection<Component>> data = new EnumMap<>(Section.class);

    public void add(ITimer timer) {
        this.add(Section.Timers, timer.toString());
    }

    public void add(Section panel, String text) {
        this.add(panel, Component.literal(text));
    }

    public void add(Section panel, Component text) {
        this.getSectionText(panel).add(text);
    }

    public void clear() {
        this.data.forEach((key, value) -> value.clear());
    }

    public Collection<Component> getSectionText(Section section) {
        return this.data.computeIfAbsent(section, ignored -> new ObjectArray<>());
    }
}
