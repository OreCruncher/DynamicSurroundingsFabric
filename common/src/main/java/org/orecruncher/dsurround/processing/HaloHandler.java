// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.SkyStyle;
import org.orecruncher.dsurround.effects.halo.HaloConditions;
import org.orecruncher.dsurround.effects.halo.HaloRenderer;
import org.orecruncher.dsurround.effects.rainbow.RainbowConditions;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.ISkyRender;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;

/**
 * The sun's halo and sun dogs (see {@link HaloConditions}): in cold biomes, where it would snow, while it isn't
 * snowing, under a clear sky (with the mod's clouds drawn, fading as they spread), on some stretches of the day. In the
 * Overworld (any dimension with its sky); drawn as part of the sky
 * ({@link HaloRenderer}), so the world and the clouds hide it, and dimmed by fog as the aurora is. Drawn in blocks with
 * Sky style Blocky. Not while an Iris shader pack is in use.
 */
public class HaloHandler extends AbstractClientHandler {

    // Ten seconds to fade fully in or out
    private static final float FADE_PER_TICK = 1F / 200F;

    private final IWeatherSimulation weatherSimulation;
    private final ISeasonalInformation seasons;
    private final FogHandler fogHandler;
    private final ILevelOracle levelOracle;
    private final CloudHandler cloudHandler;

    private float strength;
    private float prevStrength;
    private float sunDogs;
    private float prevSunDogs;
    private String status = "none";

    public HaloHandler(IWeatherSimulation weatherSimulation, ISeasonalInformation seasons, FogHandler fogHandler,
                       ILevelOracle levelOracle, CloudHandler cloudHandler, Configuration config, IModLog logger) {
        super("Halo Handler", config, logger);
        this.weatherSimulation = weatherSimulation;
        this.seasons = seasons;
        this.fogHandler = fogHandler;
        this.levelOracle = levelOracle;
        this.cloudHandler = cloudHandler;

        ISkyRender.EVENT.register(this::renderSky);
    }

    @Override
    public void process(final Player player) {
        this.prevStrength = this.strength;
        this.prevSunDogs = this.sunDogs;

        var level = player.level();
        var options = this.config.weatherOptions;
        float target = 0F;
        float dogsTarget = 0F;
        var blocked = SkyEffects.blocked(options.sky.enableHalos, HaloRenderer.isAvailable(), level);
        if (blocked != null) {
            this.status = blocked;
        } else if (this.seasons.getPrecipitationAt(player.blockPosition()) != Biome.Precipitation.SNOW) {
            this.status = "not a cold biome";
        } else if (this.weatherSimulation.intensity() > 0F) {
            this.status = "snowing";
        } else if (!HaloConditions.slotHasHalo(level.getDefaultClockTime(), options.sky.haloChance / 100F)) {
            this.status = "none this time";
        } else {
            var elevation = RainbowConditions.sunElevation(this.levelOracle.currentCelestialAngle());
            // The clouds drawn are translucent, so the sky shows through them: they hide a halo only by being many.
            // Vanilla's say nothing of how cloudy it is
            var clear = this.cloudHandler.replacesClouds() ? HaloConditions.clearSky(this.cloudHandler.cover().coverage()) : 1F;
            target = HaloConditions.sunFactor(elevation) * clear;
            dogsTarget = HaloConditions.sunDogFactor(elevation);
            if (clear <= 0F)
                this.status = "too cloudy";
            else
                this.status = target > 0F ? "showing" : "sun down";
        }
        this.strength = Mth.approach(this.strength, target, FADE_PER_TICK);
        this.sunDogs = Mth.approach(this.sunDogs, dogsTarget, FADE_PER_TICK);
    }

    @Override
    public void onDisconnect() {
        this.strength = this.prevStrength = 0F;
        this.sunDogs = this.prevSunDogs = 0F;
        this.status = "none";
    }

    private void renderSky(Matrix4f viewMatrix, float partialTick, float starBrightness, float rainBrightness, Vector3fc skyColor) {
        if (this.strength <= 0F && this.prevStrength <= 0F)
            return;

        var level = GameUtils.getWorld().orElse(null);
        if (level == null)
            return;
        var fog = SkyEffects.visibility(level, this.fogHandler);
        var alpha = Mth.lerp(partialTick, this.prevStrength, this.strength) * fog;
        if (alpha <= 0F)
            return;

        var sunDogs = Mth.lerp(partialTick, this.prevSunDogs, this.sunDogs);
        var blocky = this.config.weatherOptions.sky.skyStyle == SkyStyle.BLOCKY;
        HaloRenderer.render(this.levelOracle.currentCelestialAngle(), alpha, sunDogs, viewMatrix, blocky);
    }

    /**
     * Why there is or isn't a halo, as the diagnostics show it
     */
    public String status() {
        return this.status;
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        var text = "Halo: " + this.status;
        if (this.strength > 0F)
            text += ", strength %.2f, sun dogs %.2f".formatted(this.strength, this.sunDogs);
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }
}
