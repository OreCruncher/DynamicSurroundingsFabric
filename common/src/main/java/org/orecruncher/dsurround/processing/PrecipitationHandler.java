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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.IDimensionInformation;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.particles.DSurroundParticleSprites;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.effects.precipitation.DampSpots;
import org.orecruncher.dsurround.effects.precipitation.IColumnSource;
import org.orecruncher.dsurround.effects.precipitation.Landings;
import org.orecruncher.dsurround.effects.precipitation.LevelColumns;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationField;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationShaders;
import org.orecruncher.dsurround.effects.precipitation.Snowflakes;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.platform.KnownMod;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.WindService;

import java.util.Objects;

/**
 * Rain and snow drawn as individual drops in place of vanilla's sheets of falling texture (see
 * {@link PrecipitationField}). How many drops there are follows the storm's strength. Where rain drops land they
 * splash, as vanilla's rain does, or on soft ground (grass, dirt, sand, wool) leave a damp spot that darkens it and
 * dries ({@link DampSpots}); see {@link Landings}. What falls where comes from {@link LevelColumns}. Vanilla's own
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
    private final Snowflakes snowflakes;
    private final LevelColumns columns;

    public PrecipitationHandler(IWeatherSimulation weatherSimulation, WindService windService, ISoundLibrary soundLibrary, IDimensionInformation dimensionInformation, ISeasonalInformation seasonalInformation,
                                ITagLibrary tagLibrary, IBiomeLibrary biomeLibrary, Configuration config, IModLog logger) {
        super("Precipitation Handler", config, logger);
        this.weatherSimulation = weatherSimulation;
        this.windService = windService;
        this.dimensionInformation = dimensionInformation;
        this.columns = new LevelColumns(seasonalInformation, biomeLibrary, tagLibrary, config.weatherOptions);
        this.landings = new Landings(tagLibrary, soundLibrary, config.weatherOptions);
        this.snowflakes = new Snowflakes(logger);

        if (config.weatherOptions.enableParticlePrecipitation)
            KnownMod.particleWeatherMod().ifPresent(mod -> logger.info(
                    "%s is installed: particle rain, snow and dust storms are off, and left to it", mod.installedId().orElseThrow()));

        // A resource pack may bring other snow
        IReloadEvent.EVENT.register((resources, scope) -> this.snowflakes.reload());
    }

    /**
     * Whether the drops are drawn in place of vanilla's weather
     */
    public boolean isActive() {
        return isEnabled(this.config.weatherOptions.enableParticlePrecipitation, KnownMod.particleWeatherMod().isPresent())
                && PrecipitationShaders.DROPS.isUsable()
                && GameUtils.isInGame() && this.dimensionInformation.particleWeather();
    }

    /**
     * Whether particle rain and snow are wanted: turned on, and not left to a mod that draws the weather as particles
     * of its own (see {@link KnownMod#PARTICLE_WEATHER})
     */
    static boolean isEnabled(boolean optionOn, boolean weatherModInstalled) {
        return optionOn && !weatherModInstalled;
    }

    @Override
    public void process(final Player player) {
        if (!this.isActive()) {
            this.field.clear();
            this.dampSpots.clear();
            return;
        }

        var camera = GameUtils.getMC().gameRenderer.getMainCamera();
        if (!camera.isInitialized() || !(player.level() instanceof ClientLevel level))
            return;

        var pos = camera.getPosition();
        var rainLevel = this.weatherSimulation.rainLevel(level.getRainLevel(1F));
        this.columns.setLevel(level);
        this.dampSpots.tick();
        // Between storms, with nothing left falling, the field is left alone: looking up the columns is the costly part
        if (this.field.isIdle(rainLevel)) {
            this.field.idle();
            return;
        }
        var particles = GameUtils.getGameSettings().particles().get();
        this.fitRange(this.config.weatherOptions.precipitationRange);
        this.field.tick(this.columns, pos.x, pos.y, pos.z, rainLevel, density(this.config.weatherOptions.precipitationDensity, particles), this.windService.wind(), RANDOM);
        this.landings.splash(level, this.field, this.dampSpots, particles, RANDOM);
        this.landings.dustSound(level, this.field, this.columns, pos.x, pos.z, rainLevel, RANDOM);
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
        this.columns.setLevel(null);
    }

    /**
     * Makes the field and the damp spots again if the range they are drawn to has changed, sized for its area. The
     * drops and spots of the old ones go with them; the new field fills in a few ticks.
     */
    private void fitRange(int range) {
        if (this.field.radius() != range) {
            this.field = new PrecipitationField(capacityFor(range), range);
            this.dampSpots = new DampSpots(PrecipitationField.forArea(MAX_DAMP_SPOTS, range));
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

        var tesselator = Tesselator.getInstance();
        var camera = GameUtils.getMC().gameRenderer.getMainCamera();
        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        try {
            if (this.dampSpots.count() > 0 && PrecipitationShaders.DAMP_SPOTS.isUsable())
                PrecipitationShaders.DAMP_SPOTS.run(() -> this.drawDampSpots(tesselator, camera, partialTick), tesselator::clear);

            // If the drops fail to draw, vanilla's weather is drawn in their place, this frame and after
            return PrecipitationShaders.DROPS.run(() -> this.drawDrops(tesselator, camera, partialTick), () -> {
                tesselator.clear();
                weatherTarget().bindWrite(false);
            });
        } finally {
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            lightTexture.turnOffLightLayer();
        }
    }

    /**
     * The dust first, behind the rain and snow, and soft: it reads a copy of the scene's depth, water included (see
     * SoftParticles.sceneDepthTarget). Then the rain and snow, drawn hard.
     */
    private void drawDrops(Tesselator tesselator, Camera camera, float partialTick) {
        var shader = Objects.requireNonNull(PrecipitationShaders.DROPS.get());
        var flakes = this.snowflakes.flakes(GameUtils.getResourceManager());
        var largest = this.snowflakes.largest();
        var dust = DSurroundParticleSprites.DUST.sprites();
        // As vanilla's weather: depth is written only with Fabulous graphics, which draws weather to its own target
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(PrecipitationShaders.DROPS.supplier());

        if (this.field.dustCount() > 0) {
            RenderSystem.setShaderTexture(0, particleAtlas());
            // The scene's depth, water included: the weather's own target has none with Fabulous graphics
            SoftParticles.bindSceneDepth(shader, SoftParticles.sceneDepthTarget(), weatherTarget());
            shader.safeGetUniform("SoftDistance").set(DUST_SOFT_DISTANCE);
            this.field.draw(tesselator, camera, partialTick, true, flakes, largest, dust);
        }

        // The rain is untextured; the snow is cut from the game's snow texture
        RenderSystem.setShaderTexture(0, Snowflakes.TEXTURE);
        shader.safeGetUniform("SoftDistance").set(0F);
        this.field.draw(tesselator, camera, partialTick, false, flakes, largest, dust);
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
        else if (KnownMod.particleWeatherMod().isPresent())
            text = "Precipitation: off (%s installed)".formatted(KnownMod.particleWeatherMod().get().installedId().orElseThrow());
        else if (!PrecipitationShaders.DROPS.isUsable())
            text = "Precipitation: vanilla (shader unavailable, failed, or a shader pack in use)";
        else
            text = "Precipitation: %d/%d drops (%d dust), %d damp spots, range %d".formatted(this.field.count(),
                    this.field.capacity(), this.field.dustCount(), this.dampSpots.count(), this.field.radius());
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }
}
