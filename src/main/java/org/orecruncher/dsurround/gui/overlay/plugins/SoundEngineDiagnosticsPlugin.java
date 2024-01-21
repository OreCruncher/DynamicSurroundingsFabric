package org.orecruncher.dsurround.gui.overlay.plugins;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.gui.overlay.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.mixins.audio.MixinSoundManagerAccessor;
import org.orecruncher.dsurround.mixins.audio.MixinSoundEngineAccessor;

import java.util.function.Function;
import java.util.stream.Collectors;

public class SoundEngineDiagnosticsPlugin implements IDiagnosticPlugin {

    private static final String FMT_DBG_SOUND = "%s: %d";

    public SoundEngineDiagnosticsPlugin() {
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::onCollect, HandlerPriority.LOW);
    }

    public void onCollect(CollectDiagnosticsEvent event) {
        var soundManager = GameUtils.getSoundManager();
        MixinSoundManagerAccessor manager = (MixinSoundManagerAccessor) soundManager;
        MixinSoundEngineAccessor accessors = (MixinSoundEngineAccessor) manager.dsurround_getSoundSystem();
        var sources = accessors.dsurround_getSources();
        var str = Component.literal(soundManager.getDebugString());
        var panelText = event.getSectionText(CollectDiagnosticsEvent.Section.Sounds);
        panelText.add(str);

        if (!sources.isEmpty()) {
            accessors.dsurround_getSources().keySet().stream()
                    .map(SoundInstance::getLocation)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet().stream()
                    .map(e -> FMT_DBG_SOUND.formatted(e.getKey(), e.getValue()))
                    .sorted()
                    .map(Component::literal)
                    .forEach(panelText::add);
        } else {
            panelText.add(Component.literal("No sounds playing"));
        }
    }
}
