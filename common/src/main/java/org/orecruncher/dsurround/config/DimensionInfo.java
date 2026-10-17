package org.orecruncher.dsurround.config;

import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.data.DimensionConfigRule;
import org.orecruncher.dsurround.lib.compat.LevelCompat;

import java.util.function.Predicate;

/**
 * Settings for one dimension, built from the world itself and then adjusted by the matching dimension config rules.
 * <p>
 * Build it in three steps: construct from the world, {@link #update} with each rule, then {@link #finish()}. Rules
 * only change the values they specify, so the order rules are applied in doesn't matter for values only one of them
 * sets, and a rule can't undo another's setting by leaving it out. Values derived from others are worked out once,
 * in finish().
 */
public class DimensionInfo {

    public static final DimensionInfo NONE = new DimensionInfo();

    /**
     * Whether a level's dimension draws its weather its own way: NeoForge lets a dimension register a renderer for it
     * (set by the NeoForge platform). Particle weather drawn in its place would hide it, so it is left to the dimension
     * unless a rule says otherwise. Nothing on Fabric replaces a dimension's weather.
     */
    public static Predicate<Level> drawsOwnWeather = level -> false;
    private static final int SPACE_HEIGHT_OFFSET = 32;
    // Cloud height for a dimension that has no clouds: never reached
    private static final int NO_CLOUDS = Integer.MAX_VALUE;

    protected final boolean isFlatWorld;
    // Attributes about the dimension. This information is loaded from local configs.
    protected Identifier name;
    protected int seaLevel;
    protected int skyHeight;
    protected int cloudHeight;
    protected int spaceHeight;
    protected boolean alwaysOutside = false;
    protected boolean playBiomeSounds = true;
    protected boolean compassWobble = false;
    // TODO: Expose in configs
    protected boolean natural = true;
    // Whether the mod's particle rain, snow and dust storms take the place of the dimension's weather
    protected boolean particleWeather = true;
    // Whether the wind blows here: it drifts the weather, mist, steam, smoke and flames
    protected boolean simulateWind = true;
    // Whether mist lies on the ground here, in the biomes given it
    protected boolean groundFog = true;

    // The dimension's own cloud height, used unless a rule sets one
    private final int defaultCloudHeight;
    // Set by a rule; null if no rule set it
    private @Nullable Integer configuredCloudHeight;

    DimensionInfo() {
        this.name = Constants.asId("no_dimension");
        this.isFlatWorld = false;
        this.defaultCloudHeight = NO_CLOUDS;
        this.cloudHeight = NO_CLOUDS;
    }

    public DimensionInfo(final Level world) {
        // Attributes that come from the world object itself. Set now because the config may override.
        this.name = world.dimension().identifier();
        this.seaLevel = world.getSeaLevel();
        this.skyHeight = world.getHeight();
        this.isFlatWorld = LevelCompat.isSuperFlat(world);
        this.defaultCloudHeight = vanillaCloudHeight(world);

        // Force sea level based on known world types that give heartburn
        if (this.isFlatWorld)
            this.seaLevel = -60;

        this.natural = world.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD;
        this.compassWobble = !this.natural;

        // Guesses, which a rule can overrule: a dimension with a sky has wind and ground fog, and one that draws its
        // weather as vanilla does gets particle weather in its place
        this.simulateWind = world.dimensionType().hasSkyLight();
        this.groundFog = world.dimensionType().hasSkyLight();
        this.particleWeather = !drawsOwnWeather.test(world);

        // Valid even if finish() is never called
        this.finish();
    }

    /**
     * The height vanilla draws this dimension's clouds at (192 for the overworld), or NO_CLOUDS for a dimension
     * without clouds (the nether and the end). Both are environment attributes; vanilla draws no clouds when their
     * color is fully transparent. Read at the world's origin, as the dimension's own values.
     */
    private static int vanillaCloudHeight(final Level world) {
        var attributes = world.environmentAttributes();
        if (ARGB.alpha(attributes.getValue(EnvironmentAttributes.CLOUD_COLOR, Vec3.ZERO)) == 0)
            return NO_CLOUDS;
        return Mth.floor(attributes.getValue(EnvironmentAttributes.CLOUD_HEIGHT, Vec3.ZERO));
    }

    /**
     * Applies a dimension config rule, if it is for this dimension. Only the values the rule specifies change.
     */
    public void update(DimensionConfigRule config) {
        if (this.name.equals(config.dimensionId())) {
            config.seaLevel().ifPresent(v -> this.seaLevel = v);
            config.skyHeight().ifPresent(v -> this.skyHeight = v);
            config.alwaysOutside().ifPresent(v -> this.alwaysOutside = v);
            config.playBiomeSounds().ifPresent(v -> this.playBiomeSounds = v);
            config.cloudHeight().ifPresent(v -> this.configuredCloudHeight = v);
            config.compassWobble().ifPresent(v -> this.compassWobble = v);
            config.particleWeather().ifPresent(v -> this.particleWeather = v);
            config.simulateWind().ifPresent(v -> this.simulateWind = v);
            config.groundFog().ifPresent(v -> this.groundFog = v);
        }
    }

    /**
     * Works out the values that depend on others, once all rules have been applied: the cloud height (a rule's, or
     * else the dimension's vanilla cloud height) and the space height (above the sky height).
     */
    public DimensionInfo finish() {
        this.cloudHeight = this.configuredCloudHeight != null ? this.configuredCloudHeight : this.defaultCloudHeight;
        this.spaceHeight = this.skyHeight + SPACE_HEIGHT_OFFSET;
        return this;
    }

    public Identifier getName() {
        return this.name;
    }

    public int getSeaLevel() {
        return this.seaLevel;
    }

    public int getSkyHeight() {
        return this.skyHeight;
    }

    public int getCloudHeight() {
        return this.cloudHeight;
    }

    public int getSpaceHeight() {
        return this.spaceHeight;
    }

    public boolean playBiomeSounds() {
        return this.playBiomeSounds;
    }

    public boolean alwaysOutside() {
        return this.alwaysOutside;
    }

    public boolean isFlatWorld() {
        return this.isFlatWorld;
    }

    public boolean getCompassWobble() {
        return this.compassWobble;
    }

    public boolean natural() {
        return this.natural;
    }

    public boolean particleWeather() {
        return this.particleWeather;
    }

    public boolean simulateWind() {
        return this.simulateWind;
    }

    public boolean groundFog() {
        return this.groundFog;
    }

}
