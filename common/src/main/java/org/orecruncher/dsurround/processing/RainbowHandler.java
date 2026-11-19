// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.rainbow.RainbowConditions;
import org.orecruncher.dsurround.effects.rainbow.RainbowRenderer;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.ISkyRender;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.time.DayCycle;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.RainbowState;

import java.util.Optional;

/**
 * Rainbows: as rain eases off with the sun low, sometimes a bow in the sky opposite the sun, and after a strong storm a
 * fainter second one outside it (see {@link RainbowConditions}). In the Overworld (any dimension with its sky), while the
 * player is where it rains rather than snows; drawn as part of the sky ({@link RainbowRenderer}), so the world and the
 * clouds hide it, and dimmed by fog as the aurora is. Not while an Iris shader pack is in use, as the pack replaces the
 * sky rendering.
 * <p>
 * {@code /dsweather rainbow} holds one at a strength, with or without the secondary bow, whatever the weather, for
 * seeing it and for the bench; disconnecting lets it go.
 */
public class RainbowHandler extends AbstractClientHandler {

    // Ten seconds to fade fully in or out
    private static final float FADE_PER_TICK = 1F / 200F;
    // Game ticks wrap at this for the shader's time, keeping it precise as a float
    private static final long TIME_WRAP = (1L << 20) - 1;

    private final IWeatherSimulation weatherSimulation;
    private final ISeasonalInformation seasons;
    private final FogHandler fogHandler;
    private final RainbowState state;
    private final RainbowConditions conditions = new RainbowConditions();

    private float strength;
    private float prevStrength;
    private float secondary;
    private float prevSecondary;
    // What /dsweather rainbow holds, if anything
    @Nullable
    private Float held;
    private boolean heldSecondary;
    private String status = "none";

    public RainbowHandler(IWeatherSimulation weatherSimulation, ISeasonalInformation seasons, FogHandler fogHandler, RainbowState state,
                          Configuration config, IModLog logger) {
        super("Rainbow Handler", config, logger);
        this.weatherSimulation = weatherSimulation;
        this.seasons = seasons;
        this.fogHandler = fogHandler;
        this.state = state;

        ISkyRender.EVENT.register(this::renderSky);
    }

    @Override
    public void process(final Player player) {
        this.prevStrength = this.strength;
        this.prevSecondary = this.secondary;

        var level = player.level();
        var options = this.config.weatherOptions;
        this.conditions.update(level.getGameTime(), this.weatherSimulation.intensity(), options.rainbowChance / 100F);

        float target = 0F;
        float secondaryTarget = 0F;
        var held = this.held;
        if (!options.enableRainbows) {
            this.status = "disabled";
        } else if (!RainbowRenderer.isAvailable()) {
            this.status = "shader unavailable or failed";
        } else if (IrisCompat.isShaderPackInUse()) {
            this.status = "shader pack in use";
        } else if (level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD || level.dimensionType().hasCeiling()) {
            this.status = "no sky";
        } else if (held != null) {
            this.status = "held";
            RainbowRenderer.prepare();
            // At once, not faded: the bench records what it holds
            this.strength = this.prevStrength = held;
            this.secondary = this.prevSecondary = this.heldSecondary ? 1F : 0F;
            this.state.setShowing(this.strength > 0F);
            return;
        } else {
            RainbowRenderer.prepare();
            var elevation = RainbowConditions.sunElevation(DayCycle.getCelestialAngle(level, player.position()));
            var sun = RainbowConditions.sunFactor(elevation, this.conditions.secondary());
            if (this.seasons.getPrecipitationAt(player.blockPosition()) != Biome.Precipitation.RAIN) {
                this.status = "not a rain biome";
            } else if (!this.conditions.isClearing()) {
                this.status = "rain not clearing";
            } else if (!this.conditions.hasRainbow()) {
                this.status = "none this time";
            } else if (sun <= 0F) {
                this.status = "sun down or too high";
            } else {
                this.status = "showing";
                target = this.conditions.strength(level.getGameTime()) * sun;
                secondaryTarget = this.conditions.secondary() ? 1F : 0F;
            }
        }
        this.strength = Mth.approach(this.strength, target, FADE_PER_TICK);
        this.secondary = Mth.approach(this.secondary, secondaryTarget, FADE_PER_TICK);
        this.state.setShowing(this.strength > 0F);
    }

    @Override
    public void onDisconnect() {
        this.conditions.reset();
        this.held = null;
        this.heldSecondary = false;
        this.strength = this.prevStrength = 0F;
        this.secondary = this.prevSecondary = 0F;
        this.state.setShowing(false);
        this.status = "none";
    }

    private void renderSky(Matrix4f viewMatrix, float partialTick, float starBrightness, float rainBrightness) {
        if (this.strength <= 0F && this.prevStrength <= 0F)
            return;

        var level = GameUtils.getWorld().orElse(null);
        if (level == null || level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD)
            return;
        // Not from inside water, lava or powder snow, as the aurora isn't
        var camera = GameUtils.getMC().gameRenderer.mainCamera();
        if (camera.getFluidInCamera() != FogType.NONE)
            return;
        if (IrisCompat.isShaderPackInUse())
            return;

        var renderDistance = GameUtils.getMC().options.getEffectiveRenderDistance() * 16F;
        var fog = AuroraHandler.fogVisibility(this.fogHandler.skyFogEnd(), renderDistance);
        var alpha = Mth.lerp(partialTick, this.prevStrength, this.strength) * fog;
        if (alpha <= 0F)
            return;

        var secondary = Mth.lerp(partialTick, this.prevSecondary, this.secondary);
        var celestialAngle = DayCycle.getCelestialAngle(level, camera.position());
        var time = ((level.getGameTime() & TIME_WRAP) + partialTick) / 20F;
        RainbowRenderer.render(celestialAngle, alpha, secondary, viewMatrix, time, this.conditions.seed());
    }

    // ---- /dsweather rainbow

    /**
     * Shows a rainbow at this strength, 0 to 1, whatever the weather, until {@link #clearHeld}
     *
     * @param secondary whether the secondary bow shows too
     */
    public void hold(float strength, boolean secondary) {
        this.held = Math.clamp(strength, 0F, 1F);
        this.heldSecondary = secondary;
    }

    /**
     * Lets the weather decide again
     *
     * @return whether one was held
     */
    public boolean clearHeld() {
        var wasHeld = this.held != null;
        this.held = null;
        this.heldSecondary = false;
        return wasHeld;
    }

    /**
     * The strength /dsweather rainbow holds, if it holds one
     */
    public Optional<Float> held() {
        return Optional.ofNullable(this.held);
    }

    public boolean heldSecondary() {
        return this.heldSecondary;
    }

    /**
     * Why there is or isn't a rainbow, as the diagnostics show it
     */
    public String status() {
        return this.status;
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        var text = "Rainbow: " + this.status;
        if (this.strength > 0F || this.conditions.isClearing())
            text += ", strength %.2f, secondary %.2f, storm peak %.2f".formatted(this.strength, this.secondary, this.conditions.stormPeak());
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }
}
