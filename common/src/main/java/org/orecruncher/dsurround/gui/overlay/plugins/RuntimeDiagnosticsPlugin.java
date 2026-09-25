package org.orecruncher.dsurround.gui.overlay.plugins;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.gui.overlay.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.runtime.oracle.IMinecraftClock;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.music.DSurroundMusicManager;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

import java.util.List;

public class RuntimeDiagnosticsPlugin implements IDiagnosticPlugin {

    private static final List<String> SCRIPTS = ImmutableList.of(
            "'Dim: ' + dim.getId() + '/' + dim.getDimName() + '; isSuperFlat: ' + dim.isSuperFlat()",
            "'Biome: ' + biome.getName() + ' (' + biome.getId() + '); Temp ' + math.round(biome.getTemperature(), 2) + '; rainfall: ' + math.round(biome.getRainfall(), 2)",
            "'Biome Traits: ' + biome.getTraits()",
            "'Weather: ' + lib.iif(weather.isRaining(),'rain: ' + math.round(weather.getRainIntensity(), 2),'not raining') + lib.iif(weather.isThundering(),' thundering','') + '; Temp: ' + math.round(weather.getTemperature(), 2) + '; ice: ' + lib.iif(weather.getTemperature() < 0.15, 'true', 'false') + ' ' + lib.iif(weather.getTemperature() < 0.2, '(breath)', '')",
            "'Diurnal: ' + lib.iif(diurnal.isNight(),'night','day') + '; celestial angle: ' + math.round(diurnal.getCelestialAngle(), 2) + '; degrees: ' + math.round(diurnal.getCelestialAngle()*360, 2)",
            "'Player: health ' + player.getHealth() + '/' + player.getMaxHealth() + '; food ' + player.getFoodLevel() + '/' + player.getFoodSaturationLevel() + '; pos (' + math.round(player.getX(), 2) + ', ' + math.round(player.getY(), 2) + ', ' + math.round(player.getZ(), 2) + ')'",
            "'State: isInside ' + state.isInside() + '; inVillage ' + state.isInVillage() + '; isUnderWater ' + state.isUnderWater()"
    );

    private final static List<Script> DIAGNOSTIC_SCRIPTS;

    static {
        DIAGNOSTIC_SCRIPTS = SCRIPTS.stream().map(Script::new).collect(ImmutableList.toImmutableList());
    }

    private final IMinecraftClock clock;
    private final IConditionEvaluator conditionEvaluator;
    private final ISeasonalInformation seasonalInformation;

    public RuntimeDiagnosticsPlugin(IMinecraftClock clock, IConditionEvaluator conditionEvaluator, ISeasonalInformation seasonalInformation) {
        this.clock = clock;
        this.conditionEvaluator = conditionEvaluator;
        this.seasonalInformation = seasonalInformation;
        ClientEventHooks.COLLECT_DIAGNOSTICS_EVENT.register(this::onCollect, HandlerPriority.HIGH);
    }

    public void onCollect(CollectDiagnosticsEvent event) {
        if (GameUtils.isInGame()) {
            event.add(CollectDiagnosticsEvent.Section.Header, this.clock.getFormattedTime());

            var seasonInfo = this.seasonalInformation.getCurrentSeasonTranslated().orElse(Component.literal("UNKNOWN"));
            var seasonText = Component.translatable("Season: %s (%s)", seasonInfo, this.seasonalInformation.getProviderName());
            event.add(CollectDiagnosticsEvent.Section.Header, seasonText);

            var particleLoad = "Particle Manager: %s".formatted(GameUtils.getParticleManager().countParticles());
            event.add(CollectDiagnosticsEvent.Section.Systems, particleLoad);

            ReflectionHelper.cast(GameUtils.getMC().getMusicManager(), DSurroundMusicManager.class)
                    .ifPresentOrElse(
                            mm -> event.add(CollectDiagnosticsEvent.Section.Systems, mm.getDiagnosticText()),
                            () -> event.add(CollectDiagnosticsEvent.Section.Systems, Component.literal("MusicManager unavailable")));

            for (var script : DIAGNOSTIC_SCRIPTS) {
                Object result = this.conditionEvaluator.eval(script);
                event.add(CollectDiagnosticsEvent.Section.Environment, result.toString());
            }
        }
    }
}
