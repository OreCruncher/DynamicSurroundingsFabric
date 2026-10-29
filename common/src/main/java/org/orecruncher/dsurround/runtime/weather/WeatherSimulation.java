package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IClientDisconnect;
import org.orecruncher.dsurround.eventing.IClientTickEnd;
import org.orecruncher.dsurround.eventing.ICollectDiagnostics;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.Mixers;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.Locale;
import java.util.Optional;

public final class WeatherSimulation implements IWeatherSimulation {

    // Keeps the storm's random numbers apart from other per-day ones, like the aurora's
    private static final long STORM_SALT = 0x2545F4914F6CDD1DL;
    // And the surges' from the storm's
    private static final long SURGE_SALT = 0x5851F42D4C957F2DL;
    /**
     * Ticks between one surge's height and the next: the storm rises or eases toward a new one every half minute
     */
    static final int SURGE_TICKS = 600;
    // A storm's maximum is never quite nothing, so a storm still shows
    static final float MIN_STORM_LEVEL = 0.01F;

    private final Configuration.WeatherOptions options;

    // Both 0 between storms. The maximum is the picked one, or the one set by command.
    private float maxIntensity;
    private float intensity;
    // Picked for this storm as it starts; 0 between storms
    private float pickedMaximum;
    // The most the storm reaches right now: its maximum, eased off between surges. Updated each tick.
    private float cap;
    // Set by command, or NaN for none. Kept between storms, until cleared or the player disconnects.
    private float strengthSet = Float.NaN;
    // The world's own rain level, for the diagnostics
    private float vanillaLevel;

    public WeatherSimulation(Configuration.WeatherOptions options) {
        this.options = options;

        IClientTickEnd.EVENT.register(this::tick);
        IClientDisconnect.EVENT.register(client -> {
            this.reset();
            this.clearStrength();
        });
        ICollectDiagnostics.EVENT.register(this::gatherDiagnostics);
    }

    @Override
    public float rainLevel(float vanillaLevel) {
        // A strength set by command applies at once, not from the next tick
        if (!Float.isNaN(this.strengthSet))
            return Math.min(vanillaLevel, this.strengthSet);
        return this.options.enableStormSimulation ? Math.min(vanillaLevel, this.cap) : vanillaLevel;
    }

    @Override
    public float intensity() {
        return this.intensity;
    }

    @Override
    public float maxIntensity() {
        return this.maxIntensity;
    }

    @Override
    public float vanillaLevel() {
        return this.vanillaLevel;
    }

    @Override
    public StormIntensity band() {
        return StormIntensity.of(this.intensity);
    }

    @Override
    public boolean isSimulating() {
        return this.options.enableStormSimulation;
    }

    @Override
    public void setStrength(float strength) {
        this.strengthSet = Mth.clamp(strength, 0F, 1F);
        if (this.pickedMaximum > 0F)
            this.maxIntensity = this.cap = this.strengthSet;
    }

    @Override
    public void clearStrength() {
        this.strengthSet = Float.NaN;
        // Back to this storm's own strength straight away; the surges pick up again from the next tick
        this.maxIntensity = this.cap = this.pickedMaximum;
    }

    @Override
    public Optional<Float> strengthSet() {
        return Float.isNaN(this.strengthSet) ? Optional.empty() : Optional.of(this.strengthSet);
    }

    private void tick(Minecraft client) {
        var level = GameUtils.getWorld();
        if (level.isPresent()) {
            var world = level.get();
            this.update(world.getRainLevel(1F), world.getDayTime() / SharedConstants.TICKS_PER_GAME_DAY, world.getGameTime());
        } else {
            this.reset();
        }
    }

    /**
     * Follows the game's rain level: a storm's maximum is picked as the rain starts, and kept until it has stopped.
     * In between, the storm surges and eases below that maximum.
     *
     * @param gameTime the world's game time, the same for every player, so every player sees the same surges
     */
    void update(float vanillaLevel, long day, long gameTime) {
        if (vanillaLevel <= 0F) {
            this.reset();
            return;
        }

        this.vanillaLevel = vanillaLevel;

        if (this.pickedMaximum == 0F)
            this.pickedMaximum = stormMaximum(day, this.options.minStormIntensity, this.options.maxStormIntensity);
        if (Float.isNaN(this.strengthSet)) {
            this.maxIntensity = this.pickedMaximum;
            this.cap = this.pickedMaximum * surge(gameTime, this.options.stormVariation);
        } else {
            // A strength set by command holds steady
            this.maxIntensity = this.cap = this.strengthSet;
        }
        this.intensity = this.rainLevel(vanillaLevel);
    }

    private void reset() {
        this.maxIntensity = this.intensity = this.vanillaLevel = this.pickedMaximum = this.cap = 0F;
    }

    /**
     * How much of its maximum a storm is at a moment, 0 to 1: rising and easing smoothly between heights picked at
     * random every {@link #SURGE_TICKS} ticks, from the game time alone, so it is the same for every player.
     *
     * @param variationPercent how far it eases at most, as a percent of its maximum; 0 holds it at its maximum
     */
    static float surge(long gameTime, int variationPercent) {
        var depth = Mth.clamp(variationPercent / 100F, 0F, 1F);
        if (depth <= 0F)
            return 1F;
        long knot = Math.floorDiv(gameTime, SURGE_TICKS);
        float along = (gameTime - knot * SURGE_TICKS) / (float) SURGE_TICKS;
        float from = surgeHeight(knot), to = surgeHeight(knot + 1);
        // Eased in and out, so it turns smoothly from rising to falling
        float eased = along * along * (3F - 2F * along);
        return 1F - depth * (1F - Mth.lerp(eased, from, to));
    }

    // A surge's height, 0 to 1, for the given knot
    private static float surgeHeight(long knot) {
        return Randomizer.create(Mixers.splitMix64(knot ^ SURGE_SALT)).nextFloat();
    }

    /**
     * The most a storm starting on the given day will reach. Strengths near the middle of the range are the most
     * likely, and every client gets the same one for the same day.
     *
     * @param minPercent the weakest storm, as a percent of full strength
     * @param maxPercent the strongest storm; taken as {@code minPercent} if lower
     */
    static float stormMaximum(long day, int minPercent, int maxPercent) {
        var random = Randomizer.create(Mixers.splitMix64(day ^ STORM_SALT));
        var low = Mth.clamp(minPercent / 100F, 0F, 1F);
        var high = Math.max(low, Mth.clamp(maxPercent / 100F, 0F, 1F));
        var result = low + (random.nextFloat() + random.nextFloat()) / 2F * (high - low);
        return Mth.clamp(result, MIN_STORM_LEVEL, 1F);
    }

    String diagnosticText() {
        String text;
        var set = !Float.isNaN(this.strengthSet);
        if (!this.options.enableStormSimulation && !set)
            text = "Storm: simulation disabled";
        else if (this.vanillaLevel == 0F)
            text = "Storm: none";
        else {
            text = String.format(Locale.ROOT, "Storm: %s %.2f/%.2f", this.band(), this.intensity, this.maxIntensity);
            if (this.cap < this.maxIntensity - 0.005F)
                text += String.format(Locale.ROOT, " (easing to %.2f)", this.cap);
        }
        if (set)
            text += String.format(Locale.ROOT, " (set to %.2f)", this.strengthSet);
        return text + String.format(Locale.ROOT, " (vanilla %.2f)", this.vanillaLevel);
    }

    private void gatherDiagnostics(CollectDiagnosticsEvent event) {
        event.add(CollectDiagnosticsEvent.Section.Systems, this.diagnosticText());
    }
}
