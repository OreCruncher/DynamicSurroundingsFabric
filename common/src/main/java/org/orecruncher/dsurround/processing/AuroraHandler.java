package org.orecruncher.dsurround.processing;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.effects.aurora.Aurora;
import org.orecruncher.dsurround.effects.aurora.AuroraPalette;
import org.orecruncher.dsurround.effects.aurora.AuroraRenderer;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.ISkyRender;
import org.orecruncher.dsurround.lib.time.DayCycle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.logging.IModLog;

/**
 * Auroras: on some nights, over cold places, curtains of light in the sky.
 * <p>
 * Whether a night has one, and what it looks like, comes from the night's number, so everyone in a world sees the
 * same aurora. It shows in the Overworld from sunset through the night, while the player is in a cold biome, and
 * fades in and out rather than appearing at once. It shows less as the sky brightens and in rain. It isn't drawn
 * while an Iris shader pack is in use, as the pack replaces the sky rendering.
 */
public class AuroraHandler extends AbstractClientHandler {

    // Ten seconds to fade fully in or out
    private static final float FADE_PER_TICK = 1F / 200F;
    // Game ticks wrap at this for the shader's time, keeping it precise as a float; the jump comes every 14.5 hours
    private static final long TIME_WRAP = (1L << 20) - 1;
    private static final long TICKS_PER_DAY = 24000L;

    private final IBiomeLibrary biomeLibrary;

    @Nullable
    private Aurora aurora;
    private float strength;
    private float prevStrength;
    private String status = "none";

    public AuroraHandler(IBiomeLibrary biomeLibrary, Configuration config, IModLog logger) {
        super("Aurora Handler", config, logger);
        this.biomeLibrary = biomeLibrary;

        ISkyRender.EVENT.register(this::renderSky);
    }

    @Override
    public void process(final Player player) {
        this.prevStrength = this.strength;

        var level = player.level();
        // The day clock (what /time set changes), so a night lasts from one day to the next
        var night = level.getDefaultClockTime() / TICKS_PER_DAY;
        var wanted = this.wanted(player, night);

        // Once faded out, start tonight's aurora, if there is one
        if (this.strength <= 0F)
            this.aurora = wanted ? Aurora.create(night, this.config.auroraOptions.maxBands) : null;

        // Last night's aurora, or one with more curtains than are now allowed, fades out before the next can begin
        var current = this.aurora;
        var stale = current != null && (current.night() != night || current.bands() > this.config.auroraOptions.maxBands);
        var target = current != null && wanted && !stale ? 1F : 0F;
        this.strength = Mth.approach(this.strength, target, FADE_PER_TICK);
    }

    @Override
    public void onDisconnect() {
        this.aurora = null;
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
        } else if (!AuroraRenderer.isAvailable()) {
            this.status = "shader unavailable or failed";
        } else if (IrisCompat.isShaderPackInUse()) {
            this.status = "shader pack in use";
        } else if (level.dimension() != Level.OVERWORLD) {
            this.status = "not the Overworld";
        } else if (!isDark(player)) {
            this.status = "daytime";
        } else if (!Aurora.appears(night, options.chance)) {
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
        var current = this.aurora;
        if (current == null)
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

        // Stars are at half brightness at the darkest of night; the aurora shows as they do, and less in rain
        var stars = Math.clamp(starBrightness * 2F, 0F, 1F);
        var alpha = Mth.lerp(partialTick, this.prevStrength, this.strength) * stars * rainBrightness;
        if (alpha <= 0F)
            return;

        var time = ((level.getGameTime() & TIME_WRAP) + partialTick) / 20F;
        AuroraRenderer.render(current, alpha, viewMatrix, time);
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        var text = "Aurora: " + this.status;
        var current = this.aurora;
        if (current != null) {
            text += ", palette %d, %d band(s), strength %.2f".formatted(
                    AuroraPalette.PALETTES.indexOf(current.palette()), current.bands(), this.strength);
        }
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }
}
