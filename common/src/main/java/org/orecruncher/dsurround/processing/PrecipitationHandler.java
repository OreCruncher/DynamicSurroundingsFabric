package org.orecruncher.dsurround.processing;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.IDimensionInformation;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.TargetCheck;
import org.orecruncher.dsurround.effects.particles.DSurroundParticleSprites;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.effects.precipitation.AfterRainDrips;
import org.orecruncher.dsurround.effects.precipitation.DampSpots;
import org.orecruncher.dsurround.effects.precipitation.DropScales;
import org.orecruncher.dsurround.effects.precipitation.IColumnSource;
import org.orecruncher.dsurround.effects.precipitation.Landings;
import org.orecruncher.dsurround.effects.precipitation.LevelColumns;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationField;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationShaders;
import org.orecruncher.dsurround.effects.precipitation.RainColor;
import org.orecruncher.dsurround.effects.precipitation.SnowFlecks;
import org.orecruncher.dsurround.effects.precipitation.Snowflakes;
import org.orecruncher.dsurround.effects.precipitation.WindowStreaks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.threading.Worker;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.WindService;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Rain and snow drawn as individual drops in place of vanilla's sheets of falling texture (see
 * {@link PrecipitationField}). How many drops there are follows the storm's strength. Where rain drops land they
 * splash, as vanilla's rain does, or on soft ground (grass, dirt, sand, wool) leave a damp spot that darkens it and
 * dries ({@link DampSpots}); on windows they run down the glass ({@link WindowStreaks}); snowflakes leave a fleck that
 * melts away ({@link SnowFlecks}); see {@link Landings}.
 * After rain, leaves and overhanging edges drip for a while ({@link AfterRainDrips}): those are vanilla's own drips,
 * so they don't need the drops drawn. What falls where comes from {@link LevelColumns}. Vanilla's own
 * splashes, scattered at random around the player, are left out while the drops are drawn; its rain sound carries on.
 * The distance is left to the weather fog.
 * <p>
 * Off while an Iris shader pack is in use, whose own weather drawing would not see the drops, when the drops' shader
 * didn't load, and after a failure to draw them until resources are reloaded; vanilla's weather is drawn instead.
 * Off too when a mod that draws rain and snow as particles of its own is installed (Particle Rain, Simple Weather):
 * both would be drawn.
 */
public class PrecipitationHandler extends AbstractClientHandler {

    /**
     * Drops at a storm's full strength, at the default range. A field at another range holds as many for its area, so
     * the drops are as dense at any range.
     */
    static final int MAX_DROPS = 6000;
    /**
     * Damp spots at once, at the default range; as many more for the area at a wider one. They last a few seconds, so
     * the most made a tick bounds this in practice.
     */
    static final int MAX_DAMP_SPOTS = 2048;
    /**
     * Snow flecks at once, at the default range; as many more for the area at a wider one. Snow lands far more slowly
     * than rain, and the flecks melt in a few seconds.
     */
    static final int MAX_SNOW_FLECKS = 1024;
    /**
     * Drops running down windows at once, at the default range; as many more for the area at a wider one
     */
    static final int MAX_WINDOW_STREAKS = 384;
    // How far under a window's bottom edge a drop that runs off it hangs, as a drip
    static final double DRIP_BELOW_EDGE = 0.05D;
    /**
     * How far in front of the ground, in blocks, a puff of dust is at full strength: closer, it fades, so a puff
     * partly in the ground blends into it rather than showing a hard edge
     */
    static final float DUST_SOFT_DISTANCE = 1.5F;

    private final IWeatherSimulation weatherSimulation;
    private final WindService windService;
    private final IDimensionInformation dimensionInformation;
    private final Landings landings;
    private PrecipitationField field = new PrecipitationField(MAX_DROPS);
    private DampSpots dampSpots = new DampSpots(MAX_DAMP_SPOTS);
    private SnowFlecks snowFlecks = new SnowFlecks(MAX_SNOW_FLECKS);
    private WindowStreaks windowStreaks = new WindowStreaks(MAX_WINDOW_STREAKS);
    private final AfterRainDrips drips = new AfterRainDrips();
    // The drops drawn this frame, for the render timer's count
    private int dropsDrawn;
    private final Snowflakes snowflakes;
    private final LevelColumns columns;
    // The drops' ticks run here, while frames are drawn: started each tick and finished at the next (see
    // PrecipitationField.start); one thread, so a step never overlaps the next, and only it uses its random numbers
    private final Executor stepper = Executors.newSingleThreadExecutor(Worker.threadFactory("Precipitation"));
    private final IRandomizer stepRandom = Randomizer.create(System.nanoTime());

    public PrecipitationHandler(IWeatherSimulation weatherSimulation, WindService windService, ISoundLibrary soundLibrary, IDimensionInformation dimensionInformation, ISeasonalInformation seasonalInformation,
                                ITagLibrary tagLibrary, IBiomeLibrary biomeLibrary, Configuration config, IModLog logger) {
        super("Precipitation Handler", config, logger);
        this.weatherSimulation = weatherSimulation;
        this.windService = windService;
        this.dimensionInformation = dimensionInformation;
        this.columns = new LevelColumns(seasonalInformation, biomeLibrary, tagLibrary, config.weatherOptions);
        this.landings = new Landings(tagLibrary, soundLibrary, config.weatherOptions);
        this.snowflakes = new Snowflakes(logger);

        // A resource pack may bring other snow
        // A tag sync (each server join) can't change the snow texture
        IReloadEvent.EVENT.register((resources, scope) -> {
            if (scope != IReloadEvent.Scope.TAGS)
                this.snowflakes.reload();
        });
    }

    /**
     * Whether the drops are drawn in place of vanilla's weather
     */
    public boolean isActive() {
        return isEnabled(this.config.weatherOptions.enableParticlePrecipitation, FeatureConflict.PARTICLE_WEATHER.isBlocked())
                && PrecipitationShaders.DROPS.isUsable()
                && GameUtils.isInGame() && this.dimensionInformation.particleWeather();
    }

    /**
     * Whether particle rain and snow are wanted: turned on, and not left to a mod that draws the weather as particles
     * of its own (see {@link FeatureConflict#PARTICLE_WEATHER})
     */
    static boolean isEnabled(boolean optionOn, boolean weatherModInstalled) {
        return optionOn && !weatherModInstalled;
    }

    @Override
    public void process(final Player player) {
        var active = this.isActive();
        if (!active) {
            this.field.clear();
            this.dampSpots.clear();
            this.snowFlecks.clear();
            this.windowStreaks.clear();
        }

        var camera = GameUtils.getMC().gameRenderer.getMainCamera();
        if (!camera.isInitialized() || !(player.level() instanceof ClientLevel level))
            return;

        var pos = camera.getPosition();
        var rainLevel = this.weatherSimulation.rainLevel(level.getRainLevel(1F));
        this.columns.setLevel(level);
        // Vanilla's own particles: they need no drops drawn, so drip even when the drops are off
        if (this.config.weatherOptions.enableAfterRainDrips)
            this.drips.tick(level, this.columns, pos.x, pos.y, pos.z, rainLevel, Randomizer.forThisThread());
        else
            this.drips.clear();
        if (!active)
            return;

        this.dampSpots.tick();
        this.snowFlecks.tick();
        // A drop running off the bottom of a window over open air hangs there as vanilla's dripping water, and falls
        this.windowStreaks.tick(Randomizer.forThisThread(), (x, y, z) -> level.addParticle(ParticleTypes.DRIPPING_WATER, x, y - DRIP_BELOW_EDGE, z, 0D, 0D, 0D));
        // Between storms, with nothing left falling, the field is left alone: looking up the columns is the costly part
        if (this.field.isIdle(rainLevel)) {
            this.field.idle();
            return;
        }
        var particles = GameUtils.getGameSettings().particles().get();
        this.fitRange(this.config.weatherOptions.precipitationRange);
        // The drops' last tick, made while the frames since were drawn: its drops are drawn from now on, and its
        // landings splash. The drops run a tick behind the world, which nobody sees.
        this.field.finish(this.columns);
        var random = Randomizer.forThisThread();
        this.landings.splash(level, this.field, this.dampSpots, this.snowFlecks, this.windowStreaks, particles, random);
        if (particles != ParticleStatus.MINIMAL)
            this.landings.seedWindows(level, this.columns, pos.x, pos.y, pos.z, rainLevel, this.windService.wind(), this.windowStreaks, random);
        this.landings.dustSound(level, this.field, this.columns, pos.x, pos.z, rainLevel, random);
        // Last: once started, the drops being stepped are the step's until the next tick
        this.field.start(this.columns, pos.x, pos.y, pos.z, rainLevel, density(this.config.weatherOptions.precipitationDensity, particles),
                this.windService.wind(), this.stepRandom, this.stepper);
    }

    /**
     * The colour of the dust around the player, as the puffs take it, for the fog and sky to match; or
     * {@link IColumnSource#NOT_A_DUST_SOURCE} if no dust is being drawn
     */
    public int regionalDustColor() {
        return this.isActive() ? this.field.regionalDustColor() : IColumnSource.NOT_A_DUST_SOURCE;
    }

    @Override
    public void onDisconnect() {
        this.field.clear();
        this.dampSpots.clear();
        this.snowFlecks.clear();
        this.windowStreaks.clear();
        this.drips.clear();
        this.columns.setLevel(null);
    }

    /**
     * Makes the field, the damp spots, the snow flecks and the window streaks again if the range they are drawn to has changed, sized for its area. The
     * drops and spots of the old ones go with them; the new field fills in a few ticks.
     */
    private void fitRange(int range) {
        if (this.field.radius() != range) {
            // Its step under way is waited out and dropped
            this.field.clear();
            this.field = new PrecipitationField(capacityFor(range), range);
            this.dampSpots = new DampSpots(PrecipitationField.forArea(MAX_DAMP_SPOTS, range));
            this.snowFlecks = new SnowFlecks(PrecipitationField.forArea(MAX_SNOW_FLECKS, range));
            this.windowStreaks = new WindowStreaks(PrecipitationField.forArea(MAX_WINDOW_STREAKS, range));
        }
    }

    /**
     * The drops a field holds at full strength to be as dense at the given range as at the default one: as many more
     * as its area is larger
     */
    static int capacityFor(int range) {
        return PrecipitationField.forArea(MAX_DROPS, range);
    }

    /**
     * The share of the drops a storm calls for that there are: the density option, lowered further by the Particles
     * video setting, as vanilla lowers its own particles
     *
     * @param densityPercent the density option, 25 to 100
     */
    static float density(int densityPercent, ParticleStatus particles) {
        float density = Mth.clamp(densityPercent / 100F, 0F, 1F);
        return switch (particles) {
            case ALL -> density;
            case DECREASED -> density / 2F;
            case MINIMAL -> density / 4F;
        };
    }

    /**
     * Draws the damp spots and the drops where vanilla draws its weather, in the same drawing state. The spots darken
     * the ground by scaling what is already drawn there; with Fabulous graphics, weather is drawn to a target of its
     * own that starts out empty, with nothing to darken, so they are laid over the ground as a dark colour instead.
     *
     * @return true if they were drawn, and vanilla's weather should not be
     */
    public boolean render(LightTexture lightTexture, float partialTick) {
        if (!this.isActive())
            return false;

        // The frost and the mist drawn before should leave it bound, but check: drawn to the main target, the drops would
        // hide the water behind them
        TargetCheck.ensureWeather("the rain and snow");
        var tesselator = Tesselator.getInstance();
        var camera = GameUtils.getMC().gameRenderer.getMainCamera();
        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        PrecipitationShaders.RENDER_TIMER.begin();
        // For recordings, how much there was to draw: the drops in the area, whatever the storm and range make
        PrecipitationShaders.RENDER_TIMER.count(this.field.count());
        try {
            if (this.dampSpots.count() > 0 && PrecipitationShaders.DAMP_SPOTS.isUsable()) {
                PrecipitationShaders.DAMP_SPOTS_TIMER.begin();
                PrecipitationShaders.DAMP_SPOTS_TIMER.count(this.dampSpots.count());
                PrecipitationShaders.DAMP_SPOTS.run(() -> this.drawDampSpots(tesselator, camera, partialTick), tesselator::clear);
                PrecipitationShaders.DAMP_SPOTS_TIMER.end();
            }

            // If the drops fail to draw, vanilla's weather is drawn in their place, this frame and after
            PrecipitationShaders.DROPS_TIMER.begin();
            this.dropsDrawn = 0;
            boolean drawn = PrecipitationShaders.DROPS.run(() -> this.drawDrops(tesselator, camera, partialTick), () -> {
                tesselator.clear();
                weatherTarget().bindWrite(false);
            });
            PrecipitationShaders.DROPS_TIMER.count(this.dropsDrawn);
            PrecipitationShaders.DROPS_TIMER.end();
            return drawn;
        } finally {
            PrecipitationShaders.RENDER_TIMER.end();
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            lightTexture.turnOffLightLayer();
        }
    }

    /**
     * The rain and snow, drawn hard, the snow flecks on the ground, which are cut from the same texture, and the drops
     * on windows; and the dust, soft: it reads a copy of the scene's depth, water included (see
     * SoftParticles.sceneDepthTarget). Without Fabulous graphics nothing here writes depth, so the dust goes first,
     * behind the rest. With it, everything writes depth, the dust too, even where it has faded to almost nothing as it
     * nears the ground, so it goes last: drawn first, it hid the snow flecks lying under it, and they blinked as it
     * drifted.
     */
    private void drawDrops(Tesselator tesselator, Camera camera, float partialTick) {
        var shader = Objects.requireNonNull(PrecipitationShaders.DROPS.get());
        var flakes = this.snowflakes.flakes(GameUtils.getResourceManager());
        var largest = this.snowflakes.largest();
        var dust = DSurroundParticleSprites.DUST.sprites();
        var options = this.config.weatherOptions;
        var scales = new DropScales(options.rainDropScale / 100F, options.snowflakeScale / 100F);
        var rainColor = RainColor.ofTint(options.rainTint);
        // As vanilla's weather: depth is written only with Fabulous graphics, which draws weather to its own target
        boolean fabulous = Minecraft.useShaderTransparency();
        RenderSystem.depthMask(fabulous);
        RenderSystem.setShader(PrecipitationShaders.DROPS.supplier());

        if (!fabulous)
            this.drawDust(tesselator, camera, partialTick, shader, flakes, largest, dust, scales, rainColor);

        // The rain is untextured; the snow is cut from the game's snow texture
        RenderSystem.setShaderTexture(0, Snowflakes.TEXTURE);
        shader.safeGetUniform("SoftDistance").set(0F);
        this.dropsDrawn += this.field.draw(tesselator, camera, partialTick, false, flakes, largest, dust, scales, rainColor);
        this.snowFlecks.draw(tesselator, camera, partialTick, flakes, largest, scales.snow());
        this.windowStreaks.draw(tesselator, camera, partialTick, rainColor);

        if (fabulous)
            this.drawDust(tesselator, camera, partialTick, shader, flakes, largest, dust, scales, rainColor);
    }

    private void drawDust(Tesselator tesselator, Camera camera, float partialTick, ShaderInstance shader,
                          List<Snowflakes.Flake> flakes, int largest, TextureAtlasSprite[] dust, DropScales scales,
                          RainColor rainColor) {
        if (this.field.dustCount() == 0)
            return;
        RenderSystem.setShaderTexture(0, particleAtlas());
        // The scene's depth, water included: the weather's own target has none with Fabulous graphics
        SoftParticles.bindSceneDepth(shader, SoftParticles.sceneDepthTarget(), weatherTarget());
        shader.safeGetUniform("SoftDistance").set(DUST_SOFT_DISTANCE);
        this.dropsDrawn += this.field.draw(tesselator, camera, partialTick, true, flakes, largest, dust, scales, rainColor);
    }

    /**
     * Where the weather is drawn: with Fabulous graphics, a target of its own, which vanilla clears but gives no copy of
     * the scene's depth (read the main target's for that); otherwise the main target
     */
    private static RenderTarget weatherTarget() {
        var minecraft = GameUtils.getMC();
        RenderTarget target = minecraft.levelRenderer.getWeatherTarget();
        return target != null ? target : minecraft.getMainRenderTarget();
    }

    private void drawDampSpots(Tesselator tesselator, Camera camera, float partialTick) {
        var darkening = !Minecraft.useShaderTransparency();
        // With Fabulous graphics their depth is written: the weather's target has none of its own, and the transparency
        // pass sorts it by depth, so without it the scene is laid over them. They lie on the ground, so it hides
        // nothing that the ground wouldn't.
        RenderSystem.depthMask(!darkening);
        RenderSystem.setShader(PrecipitationShaders.DAMP_SPOTS.supplier());
        RenderSystem.setShaderTexture(0, particleAtlas());
        // What is drawn there is scaled by one less the spot's alpha
        if (darkening)
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        try {
            this.dampSpots.draw(tesselator, camera, partialTick, DSurroundParticleSprites.DAMP_SPOT.get(0, 1), darkening);
        } finally {
            RenderSystem.defaultBlendFunc();
        }
    }

    /**
     * Deprecated in 1.21.1, but vanilla's own particle sheets use it and there is no replacement (see
     * DSurroundParticleRenderType)
     */
    @SuppressWarnings("deprecation")
    private static ResourceLocation particleAtlas() {
        return TextureAtlas.LOCATION_PARTICLES;
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        String text;
        if (!this.config.weatherOptions.enableParticlePrecipitation)
            text = "Precipitation: vanilla";
        else if (FeatureConflict.PARTICLE_WEATHER.isBlocked())
            text = "Precipitation: off (%s installed)".formatted(FeatureConflict.PARTICLE_WEATHER.blockingMod().orElseThrow().installedId().orElseThrow());
        else if (!PrecipitationShaders.DROPS.isUsable())
            text = "Precipitation: vanilla (shader unavailable, failed, or a shader pack in use)";
        else
            text = "Precipitation: %d/%d drops (%d dust), %d damp spots, %d snow flecks, %d window streaks, range %d".formatted(this.field.count(),
                    this.field.capacity(), this.field.dustCount(), this.dampSpots.count(), this.snowFlecks.count(), this.windowStreaks.count(),
                    this.field.radius());
        if (this.config.weatherOptions.enableAfterRainDrips && this.drips.strength() > 0F)
            text += String.format(Locale.ROOT, ", drips %.2f", this.drips.strength());
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }
}
