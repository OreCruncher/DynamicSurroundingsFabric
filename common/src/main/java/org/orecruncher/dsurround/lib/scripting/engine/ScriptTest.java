package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.collect.ImmutableList;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.List;

public class ScriptTest {

    private static final List<String> scripts = ImmutableList.of(
            "lib.iif(!false, 'Fred', 'George')",
            "'Simple' + 'test'",
            "'Dim: ' + dim.getId() + '/' + dim.getDimName() + '; isSuperFlat: ' + dim.isSuperFlat()",
            "'Biome: ' + biome.getName() + ' (' + biome.getId() + '); Temp ' + biome.getTemperature() + '; rainfall: ' + biome.getRainfall()",
            "'Biome Traits: ' + biome.getTraits()",
            "'Weather: ' + lib.iif(weather.isRaining(),'rain: ' + weather.getRainIntensity(),'not raining') + lib.iif(weather.isThundering(),' thundering','') + '; Temp: ' + weather.getTemperature() + '; ice: ' + lib.iif(weather.getTemperature() < 0.15, 'true', 'false') + ' ' + lib.iif(weather.getTemperature() < 0.2, '(breath)', '')",
            "'Diurnal: ' + lib.iif(diurnal.isNight(),' night,',' day,') + '; celestial angle: ' + diurnal.getCelestialAngle() + '; degrees: ' + (diurnal.getCelestialAngle()*360)",
            "'Player: health ' + player.getHealth() + '/' + player.getMaxHealth() + '; food ' + player.getFoodLevel() + '/' + player.getFoodSaturationLevel() + '; pos (' + player.getX() + ', ' + player.getY() + ', ' + player.getZ() + ')'",
            "'State: isInside ' + state.isInside() + '; inVillage ' + state.isInVillage() + '; isUnderWater ' + state.isUnderWater()",
            "Bad.Ident..ifier.",
            "!MOUNTAIN && (WASTELAND && !SWAMP && !COLD)",
            "weather.isNotRaining() && !weather.canWaterFreeze() && diurnal.isNight()",
            "FOREST && !(DEAD || WASTELAND || SWAMP || SPOOKY) && lib.isBetween(biome.temperature, 0.2, 1.0)",
            "lib.oneof(biome.id, 'minecraft:frozen_ocean', 'minecraft:deep_frozen_ocean')",
            "biome.id == 'minecraft:deep_frozen_ocean'",
            "!(NETHER || END) && biome.getRainfall() < 0.1 && (DESERT || (WASTELAND && !(COLD || SNOWY || SWAMP)))"
    );

    public static void runScriptTests() {
        var scriptEngine = ContainerManager.resolve(ScriptEngine.class);
        for (String script : scripts) {
            try {
                var result = scriptEngine.compile(script);
                var data = result.eval();
                var x = 0;
            } catch(Throwable t) {
                var x = 0;
            }
        }
    }
}
