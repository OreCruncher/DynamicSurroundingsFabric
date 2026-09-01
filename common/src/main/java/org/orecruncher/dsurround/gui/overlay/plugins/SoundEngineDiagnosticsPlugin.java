package org.orecruncher.dsurround.gui.overlay.plugins;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.gui.overlay.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

import java.util.function.Function;
import java.util.stream.Collectors;

public class SoundEngineDiagnosticsPlugin implements IDiagnosticPlugin {

    private static final String FMT_DBG_SOUND = "%s: %d";

    public SoundEngineDiagnosticsPlugin() {
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::onCollect, HandlerPriority.LOW);
    }

    public void onCollect(CollectDiagnosticsEvent event) {
        var soundManager = GameUtils.getSoundManager();
        var panelText = event.getSectionText(CollectDiagnosticsEvent.Section.Sounds);

        // Check the sound source volume settings because they can disable sounds
        for (var category : SoundSource.values()) {
            var volumeSettings = GameUtils.getGameSettings().getSoundSourceVolume(category);
            if (Float.compare(volumeSettings, 0F) == 0) {
                var text = Component.literal("%s is OFF".formatted(category.name())).withColor(ColorPalette.RED.getValue());
                panelText.add(text);
            }
        }

        var sources = soundManager.soundEngine.instanceToChannel;
        var str = Component.literal(soundManager.getDebugString());
        panelText.add(str);

        if (!sources.isEmpty()) {
            sources.keySet().stream()
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
