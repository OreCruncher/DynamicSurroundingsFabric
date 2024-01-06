package org.orecruncher.dsurround.gui.hud.plugins;

import com.google.common.collect.ImmutableList;
import joptsimple.internal.Strings;
import net.minecraft.ChatFormatting;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.gui.hud.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

import java.util.List;

public class RuntimeDiagnosticsPlugin implements IDiagnosticPlugin {

    private static final List<String> scripts = ImmutableList.of(
            "'Dim: ' + dim.getId() + '/' + dim.getDimName() + '; isSuperFlat: ' + dim.isSuperFlat()",
            "'Biome: ' + biome.getName() + '; Temp ' + biome.getTemperature() + '; rainfall: ' + biome.getRainfall()",
            "'Biome Traits: ' + biome.getTraits()",
            "'Weather: ' + lib.iif(weather.isRaining(),'rain: ' + weather.getRainIntensity(),'not raining') + lib.iif(weather.isThundering(),' thundering','') + '; Temp: ' + weather.getTemperature() + '; ice: ' + lib.iif(weather.getTemperature() < 0.15, 'true', 'false') + ' ' + lib.iif(weather.getTemperature() < 0.2, '(breath)', '')",
            "'Diurnal: ' + lib.iif(diurnal.isNight(),' night,',' day,') + '; celestial angle: ' + diurnal.getCelestialAngle() + '; degrees: ' + (diurnal.getCelestialAngle()*360)",
            "'Player: health ' + player.getHealth() + '/' + player.getMaxHealth() + '; food ' + player.getFoodLevel() + '/' + player.getFoodSaturationLevel() + '; pos (' + player.getX() + ', ' + player.getY() + ', ' + player.getZ() + ')'",
            "'State: isInside ' + state.isInside() + '; inVillage ' + state.isInVillage() + '; isUnderWater ' + state.isUnderWater()"
    );

    private final MinecraftClock clock = new MinecraftClock();
    private final IConditionEvaluator conditionEvaluator;

    public RuntimeDiagnosticsPlugin(IConditionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::onCollect, HandlerPriority.HIGH);
    }

    public void onCollect(ClientEventHooks.CollectDiagnosticsEvent event) {
        if (GameUtils.isInGame()) {
            var world = GameUtils.getWorld().orElseThrow();
            this.clock.update(world);
            event.left.add(ChatFormatting.GREEN + this.clock.getFormattedTime());
            event.left.add(Strings.EMPTY);

            for (String script : scripts) {
                Object result = this.conditionEvaluator.eval(new Script(script));
                event.left.add(ChatFormatting.YELLOW + result.toString());
            }
        }
    }
}
