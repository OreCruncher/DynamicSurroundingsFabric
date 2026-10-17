package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.IDimensionInformation;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IClientDisconnect;
import org.orecruncher.dsurround.eventing.IClientTickEnd;
import org.orecruncher.dsurround.eventing.ICollectDiagnostics;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

import java.util.Locale;

/**
 * The one wind of the scene. Particle rain, snow and dust are carried by it, and other effects lean with it through
 * {@link SceneWind}. It blows with the storm, a light breeze between storms, and harder in a dust storm; it ticks
 * whether or not particle rain is on, so the other effects have it either way.
 */
public final class WindService {

    private static final IRandomizer RANDOM = Randomizer.current();

    private final IWeatherSimulation weatherSimulation;
    private final ISeasonalInformation seasons;
    private final IBiomeLibrary biomeLibrary;
    private final Configuration.WeatherOptions options;
    private final IDimensionInformation dimensionInformation;
    private final Wind wind = new Wind();

    public WindService(IWeatherSimulation weatherSimulation, ISeasonalInformation seasons, IBiomeLibrary biomeLibrary,
                       Configuration.WeatherOptions options, IDimensionInformation dimensionInformation) {
        this.dimensionInformation = dimensionInformation;
        this.weatherSimulation = weatherSimulation;
        this.seasons = seasons;
        this.biomeLibrary = biomeLibrary;
        this.options = options;

        IClientTickEnd.EVENT.register(this::tick);
        IClientDisconnect.EVENT.register(client -> {
            SceneWind.clear();
            this.wind.clearSet();
        });
        ICollectDiagnostics.EVENT.register(this::gatherDiagnostics);
    }

    /**
     * The wind, as of the last tick
     */
    public Wind wind() {
        return this.wind;
    }

    /**
     * Whether the air is still here: the dimension has no wind
     */
    public boolean isStill() {
        return GameUtils.isInGame() && !this.dimensionInformation.simulateWind();
    }

    /**
     * The steady wind's speed, without gusts, in blocks a tick; none where the air is still
     */
    public float steadySpeed() {
        return this.isStill() ? 0F : this.wind.speed();
    }

    /**
     * The wind's speed where the player is, a passing gust included, in blocks a tick; none where the air is still
     */
    public float speedAtPlayer() {
        return this.isStill() ? 0F : this.wind.speed() * this.wind.gustFactor(0F, 0F);
    }

    /**
     * How loud the wind's ambient loops play now, for the wind where the player is and how open to the sky they are
     */
    public float loopScale() {
        if (!GameUtils.isInGame() || this.isStill())
            return 0F;
        var player = GameUtils.getPlayer().orElseThrow();
        var exposure = Shelter.exposure(player.level(), BlockPos.containing(player.getEyePosition()));
        return WindSounds.loopScale(this.speedAtPlayer(), exposure);
    }

    private void gatherDiagnostics(CollectDiagnosticsEvent event) {
        var still = this.isStill();
        event.add(CollectDiagnosticsEvent.Section.Systems, still ? "Wind: none in this dimension" : diagnosticText(this.wind));
    }

    /**
     * The wind's line on the diagnostics overlay: its speed, the way it blows, and whether a gust is passing
     */
    static String diagnosticText(Wind wind) {
        return String.format(Locale.ROOT, "Wind: %.2f toward %s (%.0f)%s%s", wind.speed(), Wind.compassPoint(wind.bearing()),
                wind.bearing(), wind.isGusting() ? " gusting" : "", wind.isSet() ? " (set)" : "");
    }

    private void tick(Minecraft client) {
        if (!GameUtils.isInGame() || GameUtils.isPaused())
            return;
        if (!this.dimensionInformation.simulateWind()) {
            // Still air: nothing drifts
            SceneWind.clear();
            return;
        }
        var level = GameUtils.getWorld().orElseThrow();
        var camera = client.gameRenderer.getMainCamera();
        var pos = camera.isInitialized() ? camera.getPosition() : GameUtils.getPlayer().orElseThrow().position();

        var rainLevel = this.weatherSimulation.rainLevel(level.getRainLevel(1F));
        var at = BlockPos.containing(pos);
        var dusty = rainLevel > 0F
                && this.dimensionInformation.particleWeather()
                && DustStorms.enabled(this.options)
                && DustStorms.isDusty(this.seasons.getPrecipitationAt(at), level.getBiome(at).value(), this.biomeLibrary);
        this.wind.setReach(this.options.precipitationRange);
        this.wind.tick(rainLevel, level.isThundering(), dusty, level.getDayTime() / Level.TICKS_PER_DAY, RANDOM);
        SceneWind.update(this.wind, pos.x, pos.z);
    }
}
