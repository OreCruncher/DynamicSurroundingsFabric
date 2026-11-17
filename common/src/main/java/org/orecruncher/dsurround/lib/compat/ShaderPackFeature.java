// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.compat;

import org.orecruncher.dsurround.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The effects that are off while an Iris shader pack is in use ({@link IrisCompat#isShaderPackInUse}), for
 * {@code /dsconflicts} to say so: a pack replaces the game's rendering, so the mod's own shaders don't fit in. Most step
 * aside as their shaders do ({@code ModShader.isUsable}); burning mobs' flames and footprints check for a pack
 * themselves. Lightning isn't here: with a pack it is drawn the game's way, and still shows.
 * <p>
 * A new effect that stands aside for a shader pack goes here, with a {@code dsurround.shader_pack.<name>} entry in
 * en_us (a test checks).
 */
public enum ShaderPackFeature {
    AURORAS(config -> config.auroraOptions.enableAuroras),
    GROUND_FOG(config -> config.weatherOptions.enableGroundFog),
    DYNAMIC_CLOUDS(config -> config.weatherOptions.enableDynamicClouds),
    PARTICLE_WEATHER(config -> config.weatherOptions.enableParticlePrecipitation),
    WINDOW_FROST(config -> config.weatherOptions.enableWindowFrost),
    LIGHTS(config -> config.fireflyOptions.enableLight || config.flameJetOptions.enableLight),
    SOFT_PARTICLES(config -> true),
    BURNING_FLAMES(config -> config.entityEffects.enableBurningEffect),
    FOOTPRINTS(config -> config.entityEffects.enableFootprints || config.entityEffects.enableTrackedFootprints),
    APPARITIONS(config -> config.worldActorOptions.enableWorldActors && config.worldActorOptions.enableApparitions
            && config.scaryContent.allowScaryContent);

    private final Predicate<Configuration> switchedOn;

    ShaderPackFeature(Predicate<Configuration> switchedOn) {
        this.switchedOn = switchedOn;
    }

    /**
     * Whether the effect is switched on in the configuration, so a shader pack is what keeps it off
     */
    public boolean isSwitchedOn(Configuration config) {
        return this.switchedOn.test(config);
    }

    /**
     * The effects switched on in the configuration, which a shader pack keeps off
     */
    public static List<ShaderPackFeature> switchedOn(Configuration config) {
        return Arrays.stream(values()).filter(feature -> feature.isSwitchedOn(config)).toList();
    }

    public String translationKey() {
        return "dsurround.shader_pack." + this.name().toLowerCase(Locale.ROOT);
    }
}
