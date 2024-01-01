package org.orecruncher.dsurround.gui.hud.plugins;

import joptsimple.internal.Strings;
import net.minecraft.ChatFormatting;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.gui.hud.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.mixins.audio.MixinSoundManagerAccessor;
import org.orecruncher.dsurround.mixins.audio.MixinSoundEngineAccessor;

import java.util.function.Function;
import java.util.stream.Collectors;

public class SoundEngineDiagnosticsPlugin implements IDiagnosticPlugin {

    private static final String FMT_DBG_SOUND = ChatFormatting.GOLD + "%s: %d";

    public SoundEngineDiagnosticsPlugin() {
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::onCollect, HandlerPriority.LOW);
    }

    public void onCollect(ClientEventHooks.CollectDiagnosticsEvent event) {
        var soundManager = GameUtils.getSoundManager();
        event.left.add(Strings.EMPTY);
        MixinSoundManagerAccessor manager = (MixinSoundManagerAccessor) soundManager;
        MixinSoundEngineAccessor accessors = (MixinSoundEngineAccessor) manager.dsurround_getSoundSystem();
        var sources = accessors.dsurround_getSources();
        var str = soundManager.getDebugString();
        event.left.add(ChatFormatting.GOLD + str);

        if (!sources.isEmpty()) {
            accessors.dsurround_getSources().keySet().stream()
                    .map(s -> s.getSound().getLocation())
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet().stream()
                    .map(e -> String.format(FMT_DBG_SOUND, e.getKey().toString(), e.getValue()))
                    .sorted()
                    .forEach(event.left::add);
        } else {
            event.left.add(ChatFormatting.GOLD + "  No sounds playing");
        }
    }
}
