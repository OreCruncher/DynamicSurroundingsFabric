package org.orecruncher.dsurround.gui.hud.plugins;

import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.Formatting;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.gui.hud.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.mixins.audio.MixinSoundManagerAccessor;
import org.orecruncher.dsurround.mixins.audio.MixinSoundSystemAccessors;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class SoundEngineDiagnosticsPlugin implements IDiagnosticPlugin {

    private static final String FMT_DBG_SOUND = Formatting.GOLD + "%s: %d";

    public SoundEngineDiagnosticsPlugin() {
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::onCollect, HandlerPriority.LOW);
    }

    public void onCollect(ClientEventHooks.CollectDiagnosticsEvent event) {
        event.left.add(Strings.EMPTY);
        MixinSoundManagerAccessor manager = (MixinSoundManagerAccessor) GameUtils.getSoundManager();
        MixinSoundSystemAccessors accessors = (MixinSoundSystemAccessors) manager.getSoundSystem();
        Map<SoundInstance, Channel.SourceManager> sources = accessors.getSources();

        event.left.add(Formatting.GOLD + GameUtils.getSoundManager().getDebugString());

        if (!sources.isEmpty()) {
            accessors.getSources().keySet().stream()
                    .map(s -> s.getSound().getIdentifier())
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet().stream()
                    .map(e -> String.format(FMT_DBG_SOUND, e.getKey().toString(), e.getValue()))
                    .sorted()
                    .forEach(event.left::add);
        } else {
            event.left.add(Formatting.GOLD + "  No sounds playing");
        }
    }
}
