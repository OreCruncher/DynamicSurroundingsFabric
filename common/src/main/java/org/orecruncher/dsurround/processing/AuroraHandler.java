package org.orecruncher.dsurround.processing;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.AuroraStyle;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.effects.aurora.Aurora;
import org.orecruncher.dsurround.effects.aurora.AuroraActivity;
import org.orecruncher.dsurround.effects.aurora.AuroraColour;
import org.orecruncher.dsurround.effects.aurora.AuroraPalette;
import org.orecruncher.dsurround.effects.aurora.AuroraRenderer;
import org.orecruncher.dsurround.effects.aurora.RealisticAurora;
import org.orecruncher.dsurround.effects.aurora.RealisticAuroraMotion;
import org.orecruncher.dsurround.effects.aurora.RealisticAuroraRenderer;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.ISkyRender;
import org.orecruncher.dsurround.lib.time.DayCycle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;

import java.util.Locale;
import java.util.Optional;

/**
 * Auroras: on some nights, over cold places, curtains of light in the sky. Drawn in the style chosen: the classic
 * curtains ({@link Aurora}) or arcs modelled on real auroras ({@link RealisticAurora}). A change of style fades the
 * one showing out before the other fades in.
 * <p>
 * Whether a night has one, and what it looks like, comes from the night's number, so everyone in a world sees the
 * same aurora. It shows in the Overworld from sunset through the night, while the player is in a cold biome, and
 * fades in and out rather than appearing at once. It shows less as the sky brightens, in rain (as much as the storm is
 * strong: a drizzle dims it, a downpour hides it), and in fog, as the stars do. It isn't drawn while an Iris shader
 * pack is in use, as the pack replaces the sky rendering.
 * <p>
 * A realistic aurora changes through the night (see {@link RealisticAurora#at}), following how far through it the
 * night is. {@code /dsaurora} can hold how active it is, and where in its night it is, to see each; disconnecting lets
 * them go.
 */
public class AuroraHandler extends AbstractClientHandler {

    // Ten seconds to fade fully in or out
    private static final float FADE_PER_TICK = 1F / 200F;
    // Game ticks wrap at this for the shader's time, keeping it precise as a float; the jump comes every 14.5 hours
    private static final long TIME_WRAP = (1L << 20) - 1;
    private static final long TICKS_PER_DAY = 24000L;
    // How far the sky's fog reaches, as a share of the usual (the render distance): the aurora is hidden once the fog
    // has closed in to the first, and shows fully from the second
    static final float FOG_HIDES_AT = 0.3F;
    static final float FOG_CLEAR_AT = 0.9F;

    private final IBiomeLibrary biomeLibrary;
    private final IWeatherSimulation weatherSimulation;
    private final FogHandler fogHandler;

    // Tonight's aurora, in the style it was started in: one of the two, or neither
    @Nullable
    private Aurora aurora;
    @Nullable
    private RealisticAurora realistic;
    // Where a realistic aurora's waves, curls and rays have moved to
    private final RealisticAuroraMotion motion = new RealisticAuroraMotion();
    // How far through the night it is, now and last tick
    private float nightProgress;
    private float prevNightProgress;
    // What /dsaurora holds, if anything
    @Nullable
    private AuroraActivity heldActivity;
    @Nullable
    private RealisticAurora.Phase heldPhase;
    @Nullable
    private AuroraColour heldColour;
    private float strength;
    private float prevStrength;
    private String status = "none";

    public AuroraHandler(IBiomeLibrary biomeLibrary, IWeatherSimulation weatherSimulation, FogHandler fogHandler, Configuration config, IModLog logger) {
        super("Aurora Handler", config, logger);
        this.biomeLibrary = biomeLibrary;
        this.weatherSimulation = weatherSimulation;
        this.fogHandler = fogHandler;

        ISkyRender.EVENT.register(this::renderSky);
    }

    @Override
    public void process(final Player player) {
        this.prevStrength = this.strength;
        this.prevNightProgress = this.nightProgress;

        var level = player.level();
        this.nightProgress = DayCycle.getNightProgress(level, player.position());
        // The day clock (what /time set changes), so a night lasts from one day to the next
        var night = level.getDefaultClockTime() / TICKS_PER_DAY;
        var wanted = this.wanted(player, night);

        // Once faded out, start tonight's aurora, if there is one, in the style chosen
        var options = this.config.auroraOptions;
        if (this.strength <= 0F) {
            this.aurora = wanted && options.style == AuroraStyle.CLASSIC ? Aurora.create(night, options.classic.maxBands) : null;
            this.realistic = wanted && options.style == AuroraStyle.REALISTIC ? this.createRealistic(night) : null;
        }
        // How active it is follows /dsaurora at once: the same arcs, more or less active
        var held = this.heldActivity;
        if (held != null && this.realistic != null && this.realistic.activity() != held)
            this.realistic = this.realistic.withActivity(held);
        // And its colour character
        var colour = this.heldColour;
        if (colour != null && this.realistic != null && this.realistic.colour() != colour)
            this.realistic = this.realistic.withColour(colour);

        // Last night's aurora, one with more curtains than are now allowed, or one in the style no longer chosen, fades
        // out before the next can begin
        var classic = this.aurora;
        var realistic = this.realistic;
        var stale = classic != null && (options.style != AuroraStyle.CLASSIC || classic.night() != night || classic.bands() > options.classic.maxBands)
                || realistic != null && (options.style != AuroraStyle.REALISTIC || realistic.night() != night);
        var target = (classic != null || realistic != null) && wanted && !stale ? 1F : 0F;
        this.strength = Mth.approach(this.strength, target, FADE_PER_TICK);
    }

    private RealisticAurora createRealistic(long night) {
        var aurora = RealisticAurora.create(night, this.config.auroraOptions.realistic.stormChance);
        var held = this.heldActivity;
        if (held != null)
            aurora = aurora.withActivity(held);
        var colour = this.heldColour;
        return colour != null ? aurora.withColour(colour) : aurora;
    }

    @Override
    public void onDisconnect() {
        this.aurora = null;
        this.realistic = null;
        this.heldActivity = null;
        this.heldPhase = null;
        this.heldColour = null;
        this.nightProgress = this.prevNightProgress = 0F;
        this.strength = this.prevStrength = 0F;
        this.status = "none";
    }

    /**
     * Whether there should be an aurora now, noting why not for the diagnostics.
     */
    private boolean wanted(Player player, long night) {
        var options = this.config.auroraOptions;
        var level = player.level();
        if (!options.enableAuroras) {
            this.status = "disabled";
        } else if (options.style == AuroraStyle.CLASSIC ? !AuroraRenderer.isAvailable() : !RealisticAuroraRenderer.isAvailable()) {
            this.status = "shader unavailable or failed";
        } else if (IrisCompat.isShaderPackInUse()) {
            this.status = "shader pack in use";
        } else if (level.dimension() != Level.OVERWORLD) {
            this.status = "not the Overworld";
        } else if (!isDark(player)) {
            this.status = "daytime";
        } else if (!Aurora.appears(night, options.chance) && !(this.heldActivity != null && options.style == AuroraStyle.REALISTIC)) {
            this.status = "none tonight";
        } else if (!this.isCold(player)) {
            this.status = "not a cold biome";
        } else {
            this.status = "showing";
            return true;
        }
        return false;
    }

    private static boolean isDark(Player player) {
        var cycle = DayCycle.getCycle(player.level(), player.position());
        return cycle == DayCycle.NIGHTTIME || cycle == DayCycle.SUNSET;
    }

    private boolean isCold(Player player) {
        var biome = player.level().getBiome(player.blockPosition()).value();
        var info = this.biomeLibrary.getBiomeInfo(biome);
        return info.hasTrait(BiomeTrait.COLD) || info.hasTrait(BiomeTrait.TAIGA) || info.hasTrait(BiomeTrait.SNOWY) || info.hasTrait(BiomeTrait.ICY);
    }

    private void renderSky(Matrix4f viewMatrix, float partialTick, float starBrightness, float rainBrightness) {
        var classic = this.aurora;
        var realistic = this.realistic;
        if (classic == null && realistic == null)
            return;

        var level = GameUtils.getWorld().orElse(null);
        if (level == null || level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD)
            return;
        // Vanilla doesn't draw the sky from inside lava or powder snow, nor when blinded; the aurora isn't drawn
        // from inside water either
        var camera = GameUtils.getMC().gameRenderer.mainCamera();
        if (camera.getFluidInCamera() != FogType.NONE)
            return;
        if (IrisCompat.isShaderPackInUse())
            return;

        // Stars are at half brightness at the darkest of night; the aurora shows as they do, less the stronger the
        // storm, and less in fog. The sky's fog for this frame is set by now, with the mod's own thickening.
        var stars = Math.clamp(starBrightness * 2F, 0F, 1F);
        var clear = 1F - this.weatherSimulation.rainLevel(level.getRainLevel(partialTick));
        var renderDistance = GameUtils.getMC().options.getEffectiveRenderDistance() * 16F;
        var fog = fogVisibility(this.fogHandler.skyFogEnd(), renderDistance);
        var alpha = Mth.lerp(partialTick, this.prevStrength, this.strength) * stars * clear * fog;
        if (alpha <= 0F)
            return;

        var time = ((level.getGameTime() & TIME_WRAP) + partialTick) / 20F;
        if (classic != null) {
            AuroraRenderer.render(classic, alpha, viewMatrix, time);
        } else {
            var moment = realistic.at(this.progressOf(realistic, partialTick));
            this.motion.advance(realistic, moment.speed(), time);
            // How high the Earth's shadow is: early and late in the night, the tops of the rays are still sunlit
            var options = this.config.auroraOptions.realistic;
            var sunAngle = DayCycle.getCelestialAngle(level, camera.position());
            var shadow = RealisticAurora.shadowHeight(sunAngle);
            // And how much a bright moon washes out its faint parts
            var washout = options.moonWashout ? RealisticAurora.moonWashout(sunAngle, DayCycle.getMoonSize(level, camera.position())) : 0F;
            RealisticAuroraRenderer.render(realistic, moment, this.motion, options.quality, shadow, washout, alpha, viewMatrix, time);
        }
    }

    /**
     * Where in its night a realistic aurora is: where /dsaurora holds it, or how far through the night it is
     */
    private float progressOf(RealisticAurora aurora, float partialTick) {
        var phase = this.heldPhase;
        return phase != null ? aurora.progressOf(phase) : Mth.lerp(partialTick, this.prevNightProgress, this.nightProgress);
    }

    // ---- /dsaurora

    /**
     * Tonight's realistic aurora, if one is showing
     */
    public Optional<RealisticAurora> realistic() {
        return Optional.ofNullable(this.realistic);
    }

    /**
     * How tonight's realistic aurora is now, if one is showing
     */
    public Optional<RealisticAurora.Moment> moment() {
        var current = this.realistic;
        return current == null ? Optional.empty() : Optional.of(current.at(this.progressOf(current, 1F)));
    }

    /**
     * Why there is or isn't an aurora, as the diagnostics show it
     */
    public String status() {
        return this.status;
    }

    public Optional<AuroraActivity> heldActivity() {
        return Optional.ofNullable(this.heldActivity);
    }

    public Optional<RealisticAurora.Phase> heldPhase() {
        return Optional.ofNullable(this.heldPhase);
    }

    public Optional<AuroraColour> heldColour() {
        return Optional.ofNullable(this.heldColour);
    }

    /**
     * Gives the realistic aurora this colour character, until {@link #clearHeld}
     */
    public void holdColour(AuroraColour colour) {
        this.heldColour = colour;
    }

    /**
     * Makes the realistic aurora this active, until {@link #clearHeld}; and shows it tonight whatever the aurora chance
     */
    public void holdActivity(AuroraActivity activity) {
        this.heldActivity = activity;
    }

    /**
     * Holds the realistic aurora at this point in its night, until {@link #clearHeld}
     */
    public void holdPhase(RealisticAurora.Phase phase) {
        this.heldPhase = phase;
    }

    /**
     * Lets the realistic aurora follow its night again
     *
     * @return whether anything was held
     */
    public boolean clearHeld() {
        var wasHeld = this.heldActivity != null || this.heldPhase != null || this.heldColour != null;
        this.heldActivity = null;
        this.heldPhase = null;
        this.heldColour = null;
        // Back to how active tonight's aurora is, and its colour (which follows from that)
        var current = this.realistic;
        if (current != null)
            this.realistic = current.withActivity(RealisticAurora.activityOf(current.night(), this.config.auroraOptions.realistic.stormChance));
        return wasHeld;
    }

    /**
     * How much of the aurora shows through the sky's fog, 0 to 1: all of it while the fog reaches as far as usual,
     * fading as it closes in, and none once it is down to {@link #FOG_HIDES_AT} of that
     *
     * @param fogEnd         how far the sky's fog reaches, in blocks
     * @param renderDistance how far it usually reaches: the render distance, in blocks
     */
    static float fogVisibility(float fogEnd, float renderDistance) {
        if (renderDistance <= 0F)
            return 1F;
        var reach = fogEnd / renderDistance;
        return Mth.clamp((reach - FOG_HIDES_AT) / (FOG_CLEAR_AT - FOG_HIDES_AT), 0F, 1F);
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        var text = "Aurora: " + this.status;
        var classic = this.aurora;
        var realistic = this.realistic;
        if (classic != null) {
            text += ", classic, palette %d, %d band(s), strength %.2f".formatted(
                    AuroraPalette.PALETTES.indexOf(classic.palette()), classic.bands(), this.strength);
        } else if (realistic != null) {
            var moment = realistic.at(this.progressOf(realistic, 1F));
            var nearest = realistic.arcs().getFirst().distance() - moment.approach();
            text += ", realistic (%s), %s, %s, %s, %d arc(s), nearest %.0f km north, declination %.0f, strength %.2f%s".formatted(
                    this.config.auroraOptions.realistic.quality.name().toLowerCase(Locale.ROOT),
                    realistic.activity().name().toLowerCase(Locale.ROOT), moment.phase().name().toLowerCase(Locale.ROOT),
                    realistic.colour().name().toLowerCase(Locale.ROOT), realistic.arcs().size(), nearest,
                    Math.toDegrees(realistic.declination()), this.strength,
                    this.heldActivity != null || this.heldPhase != null || this.heldColour != null ? " (held)" : "");
        }
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }
}
