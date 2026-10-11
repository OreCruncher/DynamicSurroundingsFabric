package org.orecruncher.dsurround.processing;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.precipitation.IColumnSource;
import org.orecruncher.dsurround.effects.particles.DSurroundParticleSprites;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.effects.precipitation.DampSpots;
import org.orecruncher.dsurround.effects.precipitation.DustStorms;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationKind;
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
import org.orecruncher.dsurround.tags.BlockEffectTags;

import java.util.Objects;

/**
 * Rain and snow drawn as individual drops in place of vanilla's sheets of falling texture (see
 * {@link PrecipitationField}). How many drops there are follows the storm's strength. Where rain drops land they
 * splash, as vanilla's rain does, or on soft ground (grass, dirt, sand, wool) leave a damp spot that darkens it and
 * dries ({@link DampSpots}). Vanilla's
 * own splashes, scattered at random around the player, are left out while the drops are drawn; its rain sound carries
 * on. The distance is left to the weather fog.
 * <p>
 * Off while an Iris shader pack is in use, whose own weather drawing would not see the drops, when the drops' shader
 * didn't load, and after a failure to draw them until resources are reloaded; vanilla's weather is drawn instead.
 * Off too when the Particle Rain mod is installed: it draws rain and snow as particles of its own, and two of them
 * would both be drawn.
 */
public class PrecipitationHandler extends AbstractClientHandler {

    /**
     * Drops at a storm's full strength, at the default range. A field at another range holds as many for its area, so
     * the drops are as dense at any range.
     */
    static final int MAX_DROPS = 6000;
    /**
     * Damp spots a tick at most, and at once. They last a few seconds, so the first bounds the second in practice.
     */
    static final int MAX_DAMP_SPOTS_PER_TICK = 16;
    static final int MAX_DAMP_SPOTS = 2048;
    /**
     * Drops smaller than this never splash on a hard surface, as fine drizzle doesn't; from there the chance rises to
     * certain at {@link #ALWAYS_SPLASH_FROM}, so there is no hard edge between drops that do and don't
     */
    static final float NO_SPLASH_BELOW = 0.15F;
    static final float ALWAYS_SPLASH_FROM = 0.4F;
    /**
     * How far in front of the ground, in blocks, a puff of dust is at full strength: closer, it fades, so a puff
     * partly in the ground blends into it rather than showing a hard edge
     */
    static final float DUST_SOFT_DISTANCE = 1.5F;

    private final IWeatherSimulation weatherSimulation;
    private final WindService windService;
    private final ITagLibrary tagLibrary;
    private final BlockPos.MutableBlockPos landingPos = new BlockPos.MutableBlockPos();
    private PrecipitationField field = new PrecipitationField(MAX_DROPS);
    private final DampSpots dampSpots = new DampSpots(MAX_DAMP_SPOTS);
    private final Snowflakes snowflakes;
    private final LevelColumns columns;

    public PrecipitationHandler(IWeatherSimulation weatherSimulation, WindService windService, ISeasonalInformation seasonalInformation,
                                ITagLibrary tagLibrary, IBiomeLibrary biomeLibrary, Configuration config, IModLog logger) {
        super("Precipitation Handler", config, logger);
        this.weatherSimulation = weatherSimulation;
        this.windService = windService;
        this.tagLibrary = tagLibrary;
        this.columns = new LevelColumns(seasonalInformation, biomeLibrary, tagLibrary, config.weatherOptions);
        this.snowflakes = new Snowflakes(logger);

        if (KnownMod.PARTICLE_RAIN.isInstalled() && config.weatherOptions.enableParticlePrecipitation)
            logger.info("Particle Rain is installed: particle rain, snow and dust storms are off, and left to it");

        // A resource pack may bring other snow
        IReloadEvent.EVENT.register((resources, scope) -> this.snowflakes.reload());
    }

    /**
     * Whether the drops are drawn in place of vanilla's weather
     */
    public boolean isActive() {
        return isEnabled(this.config.weatherOptions.enableParticlePrecipitation, KnownMod.PARTICLE_RAIN.isInstalled())
                && PrecipitationShaders.DROPS.isUsable();
    }

    /**
     * Whether particle rain and snow are wanted: turned on, and not left to the Particle Rain mod
     */
    static boolean isEnabled(boolean optionOn, boolean particleRainInstalled) {
        return optionOn && !particleRainInstalled;
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
        this.columns.level = level;
        this.dampSpots.tick();
        // Between storms, with nothing left falling, the field is left alone: looking up the columns is the costly part
        if (this.field.isIdle(rainLevel)) {
            this.field.idle();
            return;
        }
        var particles = GameUtils.getGameSettings().particles().get();
        this.fitRange(this.config.weatherOptions.precipitationRange);
        this.field.tick(this.columns, pos.x, pos.y, pos.z, rainLevel, density(this.config.weatherOptions.precipitationDensity, particles), this.windService.wind(), RANDOM);
        this.splash(level);
    }

    /**
     * Where rain drops landed: a splash, as vanilla's, or a puff of smoke on something hot, or a damp spot on soft
     * ground. Follows the particle setting, as vanilla's splashes do.
     */
    private void splash(ClientLevel level) {
        var status = GameUtils.getGameSettings().particles().get();
        if (status == ParticleStatus.MINIMAL)
            return;
        int landings = status == ParticleStatus.DECREASED ? this.field.landings() / 2 : this.field.landings();
        int dampSpots = 0;

        for (int i = 0; i < landings; i++) {
            float x = this.field.landingX(i);
            float z = this.field.landingZ(i);
            var pos = this.landingPos.set(Mth.floor(x), this.field.landingGround(i) - 1, Mth.floor(z));
            var state = level.getBlockState(pos);
            var fluid = level.getFluidState(pos);

            // The top of what the drop hit, where it hit it: a slab, a snow layer, the water's surface
            var shapeTop = state.getCollisionShape(level, pos).max(Direction.Axis.Y, x - pos.getX(), z - pos.getZ());
            var y = pos.getY() + surfaceTop(shapeTop, fluid.getHeight(level, pos));

            if (fluid.is(FluidTags.LAVA) || state.is(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(state)) {
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0D, 0D, 0D);
            } else if (fluid.isEmpty() && this.tagLibrary.is(BlockEffectTags.RAIN_ABSORBING, state)) {
                if (dampSpots++ < MAX_DAMP_SPOTS_PER_TICK)
                    this.dampSpots.add(x, y, z, this.field.landingSize(i), RANDOM);
            } else if (!fluid.isEmpty() || RANDOM.nextFloat() < splashChance(this.field.landingSize(i))) {
                // On water, the mod's ripple takes the splash's place. Every drop makes one there: drizzle on a
                // pond is a scatter of small rings.
                level.addParticle(ParticleTypes.RAIN, x, y, z, 0D, 0D, 0D);
            }
        }
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
        this.columns.level = null;
    }

    /**
     * Makes the field again if the range it is drawn to has changed. The drops of the old field go with it; the new
     * one fills in a few ticks.
     */
    private void fitRange(int range) {
        if (this.field.radius() != range)
            this.field = new PrecipitationField(capacityFor(range), range);
    }

    /**
     * The drops a field holds at full strength to be as dense at the given range as at the default one: as many more
     * as its area is larger
     */
    static int capacityFor(int range) {
        float scale = range / (float) PrecipitationField.DEFAULT_RADIUS;
        return Math.round(MAX_DROPS * scale * scale);
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
     * Just above the snow line, the rain is part snow: at most this share, right at the line, thinning to none this far
     * above it in temperature
     */
    static final float SLEET_MAX_SHARE = 0.5F;
    static final float SLEET_BAND = 0.1F;

    /**
     * The share of the rain that falls as snow at a temperature: half at the snow line, none from {@link #SLEET_BAND}
     * above it. Below the line it is all snow, which isn't asked.
     */
    static float sleetShare(float temperature) {
        float above = temperature - ISeasonalInformation.SNOW_TEMPERATURE;
        if (above < 0F || above >= SLEET_BAND)
            return 0F;
        return SLEET_MAX_SHARE * (1F - above / SLEET_BAND);
    }

    /**
     * What a storm brings to a column, from what the biome and season give it: rain or snow as they say, and where
     * they give nothing, dust if the biome is dusty
     */
    static PrecipitationKind kindOf(Biome.Precipitation precipitation, boolean dusty) {
        return switch (precipitation) {
            case RAIN -> PrecipitationKind.RAIN;
            case SNOW -> PrecipitationKind.SNOW;
            case NONE -> dusty ? PrecipitationKind.DUST : PrecipitationKind.NONE;
        };
    }

    /**
     * The chance a drop of the given size, 0 to 1, splashes on a hard surface
     */
    static float splashChance(float dropSize) {
        return Mth.clamp((dropSize - NO_SPLASH_BELOW) / (ALWAYS_SPLASH_FROM - NO_SPLASH_BELOW), 0F, 1F);
    }

    /**
     * How far up its block the surface a drop landed on is: the top of the block's shape where the drop hit, or of
     * the fluid in it, whichever is higher. A block with no shape there (water, or something motion blocking that
     * can be walked through) has none to offer, so the fluid's surface decides; with neither, the top of the block.
     * The splash must be inside the water for the water ripple to take its place.
     *
     * @param shapeTop    the top of the collision shape at the point, or negative infinity where it has none
     * @param fluidHeight the fluid's height in the block, 0 for none
     */
    static double surfaceTop(double shapeTop, float fluidHeight) {
        var top = Math.max(shapeTop, fluidHeight);
        return Double.isFinite(top) && top > 0D ? top : 1D;
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
     * The dust first, behind the rain and snow, and soft: it reads a copy of the scene's depth, taken from the target
     * the weather is drawn to. Then the rain and snow, drawn hard.
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
            SoftParticles.bindSceneDepth(shader, weatherTarget());
            shader.safeGetUniform("SoftDistance").set(DUST_SOFT_DISTANCE);
            this.field.draw(tesselator, camera, partialTick, true, flakes, largest, dust);
        }

        // The rain is untextured; the snow is cut from the game's snow texture
        RenderSystem.setShaderTexture(0, Snowflakes.TEXTURE);
        shader.safeGetUniform("SoftDistance").set(0F);
        this.field.draw(tesselator, camera, partialTick, false, flakes, largest, dust);
    }

    /**
     * Where the weather is drawn: with Fabulous graphics, a target of its own, which holds a copy of the scene's depth;
     * otherwise the main target
     */
    private static RenderTarget weatherTarget() {
        var minecraft = GameUtils.getMC();
        RenderTarget target = minecraft.levelRenderer.getWeatherTarget();
        return target != null ? target : minecraft.getMainRenderTarget();
    }

    private void drawDampSpots(Tesselator tesselator, Camera camera, float partialTick) {
        var darkening = !Minecraft.useShaderTransparency();
        RenderSystem.depthMask(false);
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
        else if (KnownMod.PARTICLE_RAIN.isInstalled())
            text = "Precipitation: off (Particle Rain installed)";
        else if (!PrecipitationShaders.DROPS.isUsable())
            text = "Precipitation: vanilla (shader unavailable, failed, or a shader pack in use)";
        else
            text = "Precipitation: %d/%d drops (%d dust), %d damp spots, range %d".formatted(this.field.count(),
                    this.field.capacity(), this.field.dustCount(), this.dampSpots.count(), this.field.radius());
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }

    /**
     * The columns of the player's level. Rain or snow by the biome and the season's temperature at the position, so
     * high mountains get snow, as vanilla decides it; dust where the biome gets neither and is dry and sandy, coloured
     * by the loose ground it is blown from (the dust sources tag).
     */
    private static final class LevelColumns implements IColumnSource {

        private final ISeasonalInformation seasons;
        private final IBiomeLibrary biomeLibrary;
        private final ITagLibrary tagLibrary;
        private final Configuration.WeatherOptions options;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // The share of snow in the rain where precipitation() last looked
        private float snowShare;
        ClientLevel level;

        LevelColumns(ISeasonalInformation seasons, IBiomeLibrary biomeLibrary, ITagLibrary tagLibrary, Configuration.WeatherOptions options) {
            this.seasons = seasons;
            this.biomeLibrary = biomeLibrary;
            this.tagLibrary = tagLibrary;
            this.options = options;
        }

        @Override
        public int groundHeight(int x, int z) {
            return this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        }

        @Override
        public PrecipitationKind precipitation(int x, int y, int z) {
            this.pos.set(x, y, z);
            // Whether anything falls, then rain or snow, and how much snow is mixed into the rain, all from one look at
            // the temperature: Serene Seasons builds a string each time it is asked. Both are the season provider's, so
            // the same as vanilla's precipitation would say (seasonal, with Serene Seasons).
            this.snowShare = 0F;
            Biome.Precipitation precipitation;
            if (!this.seasons.hasPrecipitationAt(this.pos)) {
                precipitation = Biome.Precipitation.NONE;
            } else {
                var temperature = this.seasons.getTemperature(this.pos);
                precipitation = temperature < ISeasonalInformation.SNOW_TEMPERATURE ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
                this.snowShare = sleetShare(temperature);
            }
            var dusty = precipitation == Biome.Precipitation.NONE
                    && DustStorms.enabled(this.options)
                    && DustStorms.isDusty(precipitation, this.level.getBiome(this.pos).value(), this.biomeLibrary);
            return kindOf(precipitation, dusty);
        }

        @Override
        public float snowShare(int x, int y, int z) {
            return this.snowShare;
        }

        @Override
        public int light(int x, int y, int z) {
            return LevelRenderer.getLightColor(this.level, this.pos.set(x, y, z));
        }

        @Override
        public int dustColor(int x, int y, int z) {
            this.pos.set(x, y, z);
            var state = this.level.getBlockState(this.pos);
            if (!this.tagLibrary.is(BlockEffectTags.DUST_SOURCES, state))
                return NOT_A_DUST_SOURCE;
            var mapColor = state.getMapColor(this.level, this.pos);
            return mapColor == MapColor.NONE ? DustStorms.SAND_COLOR : mapColor.col;
        }

        @Override
        public int fallbackDustColor(int x, int y, int z) {
            return DustStorms.dustColor(this.level.getBiome(this.pos.set(x, y, z)).value(), this.biomeLibrary);
        }
    }
}
