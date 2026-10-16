package org.orecruncher.dsurround.processing;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import org.orecruncher.dsurround.effects.MeshDraw;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.runtime.oracle.IDimensionOracle;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.particles.DSurroundParticleSprites;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.effects.precipitation.AfterRainDrips;
import org.orecruncher.dsurround.effects.precipitation.DampSpots;
import org.orecruncher.dsurround.effects.precipitation.DropScales;
import org.orecruncher.dsurround.effects.precipitation.IColumnSource;
import org.orecruncher.dsurround.effects.precipitation.Landings;
import org.orecruncher.dsurround.effects.precipitation.LevelColumns;
import org.orecruncher.dsurround.effects.precipitation.ParticleQuads;
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
import org.orecruncher.dsurround.mixinutils.OptionalHook;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.WindService;

import java.util.Locale;
import java.util.Objects;

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

    private final IWeatherSimulation weatherSimulation;
    private final WindService windService;
    private final IDimensionOracle dimensionInformation;
    private final Landings landings;
    private PrecipitationField field = new PrecipitationField(MAX_DROPS);
    private DampSpots dampSpots = new DampSpots(MAX_DAMP_SPOTS);
    private SnowFlecks snowFlecks = new SnowFlecks(MAX_SNOW_FLECKS);
    private WindowStreaks windowStreaks = new WindowStreaks(MAX_WINDOW_STREAKS);
    private final AfterRainDrips drips = new AfterRainDrips();
    private final Snowflakes snowflakes;
    private final LevelColumns columns;
    // Drawing: made on first use, on the render thread
    private ByteBufferBuilder meshBytes;
    private final ParticleQuads quads = new ParticleQuads();
    private final MeshDraw dampSpotDraw = new MeshDraw("damp spots");
    private final MeshDraw dustDraw = new MeshDraw("dust");
    private final MeshDraw dropDraw = new MeshDraw("rain and snow");
    private final MeshDraw fleckDraw = new MeshDraw("snow flecks");
    private final MeshDraw streakDraw = new MeshDraw("window streaks");

    public PrecipitationHandler(IWeatherSimulation weatherSimulation, WindService windService, ISoundLibrary soundLibrary, IDimensionOracle dimensionInformation, ISeasonalInformation seasonalInformation,
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
                && PrecipitationShaders.isAvailable()
                && OptionalHook.PRECIPITATION.status() != OptionalHook.Status.MISSING
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

        var camera = GameUtils.getMC().gameRenderer.mainCamera();
        if (!camera.isInitialized() || !(player.level() instanceof ClientLevel level))
            return;

        var pos = camera.position();
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
        // Thousands of numbers a tick: this thread's generator, rather than looking it up for each
        var random = Randomizer.forThisThread();
        this.field.tick(this.columns, pos.x, pos.y, pos.z, rainLevel, density(this.config.weatherOptions.precipitationDensity, particles), this.windService.wind(), random);
        this.landings.splash(level, this.field, this.dampSpots, this.snowFlecks, this.windowStreaks, particles, random);
        if (particles != ParticleStatus.MINIMAL)
            this.landings.seedWindows(level, this.columns, pos.x, pos.y, pos.z, rainLevel, this.windService.wind(), this.windowStreaks, random);
        this.landings.dustSound(level, this.field, this.columns, pos.x, pos.z, rainLevel, random);
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
     * Draws the damp spots and the drops where vanilla draws its weather (see MixinWeatherEffectRenderer), outside any
     * render pass, to the weather's target: with Fabulous graphics a target of its own, otherwise the main one. The
     * fog and the camera's matrices are those vanilla set for its weather.
     *
     * @return true if they were drawn, and vanilla's weather should not be
     */
    public boolean render() {
        if (!this.isActive())
            return false;

        var minecraft = GameUtils.getMC();
        var camera = minecraft.gameRenderer.mainCamera();
        var partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var fabulous = minecraft.gameRenderer.gameRenderState().useShaderTransparency();
        var target = OutputTarget.WEATHER_TARGET.getRenderTarget();
        if (this.meshBytes == null)
            this.meshBytes = new ByteBufferBuilder(this.field.capacity() * 4 * DefaultVertexFormat.PARTICLE.getVertexSize());

        PrecipitationShaders.RENDER_TIMER.begin();
        try {
            var spots = PrecipitationShaders.fabulous(fabulous, PrecipitationShaders.DAMP_SPOTS, PrecipitationShaders.DAMP_SPOTS_DEPTH);
            if (this.dampSpots.count() > 0 && spots.isUsable()) {
                PrecipitationShaders.DAMP_SPOTS_TIMER.begin();
                spots.run(() -> this.drawDampSpots(spots.pipeline(), target, camera, partialTick, !fabulous), () -> {});
                PrecipitationShaders.DAMP_SPOTS_TIMER.end();
            }

            // If the drops fail to draw, vanilla's weather is drawn in their place, this frame and after
            var drops = PrecipitationShaders.fabulous(fabulous, PrecipitationShaders.DROPS, PrecipitationShaders.DROPS_DEPTH);
            PrecipitationShaders.DROPS_TIMER.begin();
            boolean drawn = drops.run(() -> this.drawDrops(drops.pipeline(), target, camera, partialTick, fabulous), () -> {});
            PrecipitationShaders.DROPS_TIMER.end();
            return drawn;
        } finally {
            PrecipitationShaders.RENDER_TIMER.end();
        }
    }

    /**
     * The dust first, behind the rain and snow, and soft: it reads a copy of the scene's depth, water included. Then
     * the rain and snow, drawn hard, the snow flecks, which are cut from the same texture, and the drops on windows.
     */
    private void drawDrops(RenderPipeline pipeline, RenderTarget target, Camera camera, float partialTick, boolean fabulous) {
        var flakes = this.snowflakes.flakes(GameUtils.getResourceManager());
        var largest = this.snowflakes.largest();
        var dust = DSurroundParticleSprites.DUST.sprites();
        var options = this.config.weatherOptions;
        var scales = new DropScales(options.rainDropScale / 100F, options.snowflakeScale / 100F);
        var rainColor = RainColor.ofTint(options.rainTint);
        var textures = GameUtils.getTextureManager();
        var lightmap = GameUtils.getMC().gameRenderer.lightmap();
        var lightmapSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        if (this.field.dustCount() > 0) {
            var atlas = textures.getTexture(TextureAtlas.LOCATION_PARTICLES);
            // Soft if it can be, reading the scene's depth, water included. With Fabulous graphics the weather's own
            // target has no depth of its own; the translucent target has the scene's, with the water's drawn over it.
            var soft = PrecipitationShaders.fabulous(fabulous, PrecipitationShaders.DUST, PrecipitationShaders.DUST_DEPTH);
            var depthSource = fabulous ? GameUtils.getMC().levelRenderer.translucentTarget() : target;
            boolean isSoft = soft.isUsable() && depthSource != null && SoftParticles.captureDepth(depthSource);
            var dustPipeline = isSoft ? soft.pipeline() : pipeline;
            this.field.draw(this.quads, camera, partialTick, true, flakes, largest, dust, scales, rainColor);
            this.dustDraw.drawQuads(this.quads.vertices(), this.quads.vertexCount(), dustPipeline, target, pass -> {
                pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
                pass.bindTexture("Sampler2", lightmap, lightmapSampler);
                if (isSoft)
                    SoftParticles.bindSoftDepth(pass);
            });
        }

        // The rain is untextured; the snow is cut from the game's snow texture
        var snow = textures.getTexture(Snowflakes.TEXTURE);
        Consumer<RenderPass> bind = pass -> {
            pass.bindTexture("Sampler0", snow.getTextureView(), snow.getSampler());
            pass.bindTexture("Sampler2", lightmap, lightmapSampler);
        };
        this.field.draw(this.quads, camera, partialTick, false, flakes, largest, dust, scales, rainColor);
        this.dropDraw.drawQuads(this.quads.vertices(), this.quads.vertexCount(), pipeline, target, bind);
        this.snowFlecks.draw(this.meshBytes, mesh -> this.fleckDraw.draw(mesh, pipeline, target, bind), camera, partialTick, flakes, largest, scales.snow());
        this.windowStreaks.draw(this.meshBytes, mesh -> this.streakDraw.draw(mesh, pipeline, target, bind), camera, partialTick, rainColor);
    }

    private void drawDampSpots(RenderPipeline pipeline, RenderTarget target, Camera camera, float partialTick, boolean darkening) {
        var atlas = GameUtils.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        this.dampSpots.draw(this.meshBytes, mesh -> this.dampSpotDraw.draw(mesh, pipeline, target,
                        pass -> pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler())),
                camera, partialTick, DSurroundParticleSprites.DAMP_SPOT.get(0, 1), darkening);
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        String text;
        if (!this.config.weatherOptions.enableParticlePrecipitation)
            text = "Precipitation: vanilla";
        else if (FeatureConflict.PARTICLE_WEATHER.isBlocked())
            text = "Precipitation: off (%s installed)".formatted(FeatureConflict.PARTICLE_WEATHER.blockingMod().orElseThrow().installedId().orElseThrow());
        else if (!PrecipitationShaders.isAvailable())
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
