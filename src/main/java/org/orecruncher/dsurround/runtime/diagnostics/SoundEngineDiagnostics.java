package org.orecruncher.dsurround.runtime.diagnostics;

import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.Formatting;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.math.TimerEMA;
import org.orecruncher.dsurround.mixins.core.MixinSoundManagerAccessor;
import org.orecruncher.dsurround.mixins.core.MixinSoundSystemAccessors;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public final class SoundEngineDiagnostics {

    private static final String FMT_DBG_SOUND = Formatting.GOLD + "%s: %d";

    public static void register() {
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(SoundEngineDiagnostics::onCollect);
    }

    private static void onCollect(Collection<String> left, Collection<String> right, Collection<TimerEMA> timers) {
        left.add(Strings.EMPTY);
        MixinSoundManagerAccessor manager = (MixinSoundManagerAccessor) GameUtils.getSoundHander();
        MixinSoundSystemAccessors accessors = (MixinSoundSystemAccessors) manager.getSoundSystem();
        Map<SoundInstance, Channel.SourceManager> sources = accessors.getSources();

        left.add(Formatting.GOLD + GameUtils.getSoundHander().getDebugString());

        if (sources.size() > 0) {
            accessors.getSources().keySet().stream()
                    .map(s -> s.getSound().getIdentifier())
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet().stream()
                    .map(e -> String.format(FMT_DBG_SOUND, e.getKey().toString(), e.getValue()))
                    .sorted()
                    .forEach(left::add);
        } else {
            left.add(Formatting.GOLD + "  No sounds playing");
        }
    }
}
