package org.orecruncher.dsurround.processing;

import com.mojang.blaze3d.pipeline.RenderTarget;
import it.unimi.dsi.fastutil.longs.LongCollection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.IDimensionInformation;
import org.orecruncher.dsurround.effects.groundfog.GroundFogConditions;
import org.orecruncher.dsurround.effects.groundfog.GroundFogField;
import org.orecruncher.dsurround.effects.groundfog.GroundFogVolume;
import org.orecruncher.dsurround.effects.groundfog.IGroundFogSource;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IBlockUpdates;
import org.orecruncher.dsurround.eventing.IChunkLoad;
import org.orecruncher.dsurround.eventing.ILightUpdate;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.SceneWind;
import org.orecruncher.dsurround.runtime.weather.Wind;
import org.orecruncher.dsurround.runtime.weather.WindService;

import java.util.Locale;
import java.util.Optional;

/**
 * Ground fog: mist lying low over the ground in biomes given it ({@code groundFog} in the biome configs), as strong as
 * the conditions allow ({@link GroundFogConditions}: the time of day, the wind, rain, and being out in it), drifting with
 * the wind. Worked out for each column of blocks around the camera ({@link GroundFogField}) and drawn as a volume
 * ({@link GroundFogVolume}), in the weather pass, before the rain.
 * <p>
 * Off with a shader pack, when its shader didn't load or failed, and in a dimension without it ({@code groundFog} in
 * the dimension configs; by default, those without a sky).
 * {@code /dsweather groundfog} gives the mist a thickness everywhere, for seeing how it looks.
 */
public class GroundFogHandler extends AbstractClientHandler {

    /**
     * The share of the wind's speed the mist drifts at: it creeps along rather than blowing past
     */
    static final float DRIFT_SHARE = 0.15F;
    /**
     * The slowest the mist drifts, in blocks a tick, so it never looks frozen: along the wind however light it is, and
     * east where there is no wind
     */
    static final float MIN_DRIFT = 0.01F;
    /**
     * The share of the way the strength moves toward the conditions' each tick: a change takes a few seconds
     */
    static final float EASE_PER_TICK = 0.02F;
    // Weaker than this, there is nothing to see
    private static final float MIN_STRENGTH = 0.005F;

    private final WindService windService;
    private final IWeatherSimulation weatherSimulation;
    private final IDimensionInformation dimensionInformation;
    private final LevelSource source;
    private final GroundFogField field = new GroundFogField();
    // A thickness set by command for everywhere, 0 to 1, or NaN for the biomes'
    private float forced = Float.NaN;
    // How strong the mist is for the conditions, easing toward them, and what they were as of the last tick
    private float strength;
    private float target;
    private float driftX;
    private float driftZ;
    private int ticks;

    public GroundFogHandler(WindService windService, IWeatherSimulation weatherSimulation, IBiomeLibrary biomeLibrary,
                            IDimensionInformation dimensionInformation, Configuration config, IModLog logger) {
        super("Ground Fog Handler", config, logger);
        this.windService = windService;
        this.weatherSimulation = weatherSimulation;
        this.dimensionInformation = dimensionInformation;
        this.source = new LevelSource(biomeLibrary);
        // Columns are looked at again only when something in them changes
        IBlockUpdates.EVENT.register(this::blockUpdates);
        IChunkLoad.EVENT.register(this::chunkLoaded);
        ILightUpdate.EVENT.register(this::lightUpdated);
    }

    private void blockUpdates(LongCollection blockPositions) {
        var it = blockPositions.iterator();
        while (it.hasNext()) {
            long pos = it.nextLong();
            this.field.columnChanged(BlockPos.getX(pos), BlockPos.getZ(pos));
        }
    }

    private void chunkLoaded(ClientLevel level, ChunkPos chunkPos) {
        this.field.chunkChanged(chunkPos.x, chunkPos.z);
    }

    private void lightUpdated(LightLayer layer, SectionPos section) {
        this.field.lightChanged(section.x(), section.y(), section.z());
    }

    /**
     * Whether the mist is drawn
     */
    public boolean isActive() {
        return this.config.weatherOptions.groundFogQuality.isEnabled() && this.shaderUsable()
                && GameUtils.isInGame() && this.dimensionInformation.groundFog();
    }

    @Override
    public void process(final Player player) {
        if (!this.isActive()) {
            this.field.clear();
            return;
        }
        var camera = GameUtils.getMC().gameRenderer.getMainCamera();
        if (!camera.isInitialized() || !(player.level() instanceof ClientLevel level))
            return;

        // Creeping along with the wind, and never quite still
        var wind = this.windService.wind();
        float dx, dz;
        if (this.windService.isStill()) {
            dx = MIN_DRIFT;
            dz = 0F;
        } else {
            dx = wind.x() * DRIFT_SHARE;
            dz = wind.z() * DRIFT_SHARE;
            if (dx * dx + dz * dz < MIN_DRIFT * MIN_DRIFT) {
                dx = wind.directionX() * MIN_DRIFT;
                dz = wind.directionZ() * MIN_DRIFT;
            }
        }
        this.driftX = wrap(this.driftX + dx);
        this.driftZ = wrap(this.driftZ + dz);
        this.ticks++;

        this.source.level = level;
        var pos = camera.getPosition();
        this.field.update(this.source, pos.x, pos.z, this.forced);

        // The time of day, the wind, rain, and whether the player is out in it; all of it when set by command
        if (Float.isNaN(this.forced)) {
            var exposure = SceneWind.exposure(level, BlockPos.containing(player.getEyePosition()));
            var rainLevel = this.weatherSimulation.rainLevel(level.getRainLevel(1F));
            var windPercent = Wind.percentOfFullStorm(this.windService.steadySpeed());
            this.target = GroundFogConditions.strength(level.getDayTime(), windPercent, rainLevel, exposure);
        } else {
            this.target = 1F;
        }
        this.strength += (this.target - this.strength) * EASE_PER_TICK;
    }

    /**
     * Draws the mist, where the weather is drawn: before the rain, so the rain is drawn over it, except with Fabulous
     * graphics, where it is drawn after (see {@link #renderAfterWeather})
     */
    public void renderBeforeWeather(LightTexture lightTexture, float partialTick) {
        if (!Minecraft.useShaderTransparency())
            this.render(lightTexture, partialTick, false);
    }

    /**
     * Draws the mist after the rain, with Fabulous graphics: there it writes the depth vanilla's transparency pass sorts
     * the weather by, and rain drawn after it would be hidden behind it. Drawn over the rain, the rain behind the mist
     * is misted, as it should be.
     */
    public void renderAfterWeather(LightTexture lightTexture, float partialTick) {
        if (Minecraft.useShaderTransparency())
            this.render(lightTexture, partialTick, true);
    }

    private void render(LightTexture lightTexture, float partialTick, boolean fabulous) {
        if (!this.isActive() || this.field.isEmpty() || this.strength < MIN_STRENGTH)
            return;
        var camera = GameUtils.getMC().gameRenderer.getMainCamera();
        float seconds = ((this.ticks % 72000) + partialTick) / 20F;
        lightTexture.turnOnLightLayer();
        try {
            GroundFogVolume.render(this.field, camera, weatherTarget(), fabulous, this.config.weatherOptions.groundFogQuality,
                    this.strength, this.driftX, this.driftZ, seconds);
        } finally {
            lightTexture.turnOffLightLayer();
        }
    }

    /**
     * Gives the mist a thickness everywhere, 0 to 1, in place of the biomes', until {@link #clearForced}
     */
    public void setForced(float thickness) {
        this.forced = Math.clamp(thickness, 0F, 1F);
    }

    public void clearForced() {
        this.forced = Float.NaN;
    }

    public Optional<Float> forced() {
        return Float.isNaN(this.forced) ? Optional.empty() : Optional.of(this.forced);
    }

    @Override
    public void onDisconnect() {
        this.field.clear();
        this.strength = 0F;
        this.target = 0F;
        this.clearForced();
        this.source.level = null;
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        String text;
        var quality = this.config.weatherOptions.groundFogQuality;
        if (!quality.isEnabled())
            text = "Ground fog: off";
        else if (!this.shaderUsable())
            text = "Ground fog: off (shader unavailable, failed, or a shader pack in use)";
        else {
            // The ground under the middle of the grid, which is where the camera is
            int middle = this.field.size() / 2;
            text = String.format(Locale.ROOT, "Ground fog: %s, strength %.2f (for %.2f), %d cells, peak %.2f, ground Y %.1f%s",
                    quality, this.strength, this.target, this.field.foggyCells(), this.field.peak(), this.field.height(middle, middle),
                    Float.isNaN(this.forced) ? "" : String.format(Locale.ROOT, " (set to %.0f%%)", this.forced * 100F));
        }
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }

    private boolean shaderUsable() {
        return GroundFogVolume.isUsable();
    }

    /**
     * Keeps the drift to a float's precision, wrapping where the noise it moves repeats, so the wrap can't be seen
     */
    private static float wrap(float drift) {
        return drift % GroundFogVolume.NOISE_WRAP;
    }

    /**
     * Where the weather is drawn: with Fabulous graphics, a target of its own, which vanilla clears but gives no copy of
     * the scene's depth; otherwise the main target
     */
    private static RenderTarget weatherTarget() {
        var minecraft = GameUtils.getMC();
        RenderTarget target = minecraft.levelRenderer.getWeatherTarget();
        return target != null ? target : minecraft.getMainRenderTarget();
    }

    /**
     * The player's level: the ground under any trees, and each biome's ground fog
     */
    private static final class LevelSource implements IGroundFogSource {

        // How far down through leaves to look for the ground
        private static final int MAX_CANOPY_DEPTH = 32;

        private final IBiomeLibrary biomeLibrary;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        @Nullable
        ClientLevel level;

        LevelSource(IBiomeLibrary biomeLibrary) {
            this.biomeLibrary = biomeLibrary;
        }

        /**
         * The client only has the heightmaps the server sends it, which count leaves as ground (the one that doesn't
         * is the server's alone, and empty here). So from the top of what blocks motion, down past leaves and the air
         * between them, to the ground under the trees.
         */
        @Override
        public int groundHeight(int x, int z) {
            assert this.level != null;
            int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            int lowest = Math.max(this.level.getMinBuildHeight(), top - MAX_CANOPY_DEPTH);
            int y = top - 1;
            while (y > lowest) {
                var state = this.level.getBlockState(this.pos.set(x, y, z));
                if (!state.isAir() && !state.is(BlockTags.LEAVES))
                    break;
                y--;
            }
            return y + 1;
        }

        @Override
        public float density(int x, int y, int z) {
            assert this.level != null;
            var biome = this.level.getBiome(this.pos.set(x, y, z)).value();
            return this.biomeLibrary.getBiomeInfo(biome).getGroundFog().getIntensity();
        }

        @Override
        public int light(int x, int y, int z) {
            assert this.level != null;
            return LevelRenderer.getLightColor(this.level, this.pos.set(x, y, z));
        }
    }
}
