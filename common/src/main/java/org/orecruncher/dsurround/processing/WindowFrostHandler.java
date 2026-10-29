package org.orecruncher.dsurround.processing;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import it.unimi.dsi.fastutil.longs.LongCollection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.DepthCopy;
import org.orecruncher.dsurround.effects.MeshDraw;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.frost.FrostConditions;
import org.orecruncher.dsurround.effects.frost.FrostFace;
import org.orecruncher.dsurround.effects.frost.FrostField;
import org.orecruncher.dsurround.effects.frost.FrostRenderer;
import org.orecruncher.dsurround.effects.frost.FrostTexture;
import org.orecruncher.dsurround.effects.frost.IFrostWorld;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IBlockUpdates;
import org.orecruncher.dsurround.eventing.IChunkLoad;
import org.orecruncher.dsurround.eventing.ILightUpdate;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.time.DayCycle;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.tags.BlockEffectTags;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Frost on the outside of windows when it is cold (see {@link FrostConditions}): on glass blocks and panes near the
 * player ({@link FrostField}), stained glass included. Without Fabulous graphics, after the translucent blocks, tested
 * against the scene's depth from before them, so water and stained glass on either side of it don't hide it, and
 * before the rain and snow. With Fabulous graphics, on clear glass where vanilla draws its weather and before it, to the
 * weather's own target, which vanilla clears and lays over the scene by depth, so its depth is written there, as the
 * falling rain's is; on stained glass, into the translucent target (see {@link #renderAfterTranslucentTerrain}).
 * <p>
 * Nothing is looked for where it is too warm for frost near the player. Off while an Iris shader pack is in use, when
 * its shader didn't load, and after a failure to draw it until resources are reloaded.
 */
public class WindowFrostHandler extends AbstractClientHandler {

    /**
     * Windows are looked for only while it is this much warmer at the player than frost needs, at most: a mountain
     * or a cold biome nearby may be colder
     */
    static final float WARM_MARGIN = 0.3F;

    private final ISeasonalInformation seasonalInformation;
    private final IWeatherSimulation weatherSimulation;
    private final ITagLibrary tagLibrary;
    private final FrostField field = new FrostField();
    private final LevelWorld world = new LevelWorld();
    private ClientLevel level;
    private float forced = Float.NaN;
    private int drawn;
    // Drawing: made on first use, on the render thread
    private ByteBufferBuilder meshBytes;
    private final MeshDraw frostDraw = new MeshDraw("window frost");
    // Frost on stained glass with Fabulous graphics, drawn in the same frame as the rest
    private final MeshDraw translucentFrostDraw = new MeshDraw("window frost on stained glass");
    // The scene's depth from before the translucent blocks, without Fabulous graphics, and whether it is this frame's
    private final DepthCopy sceneDepth = new DepthCopy("Dynamic Surroundings window frost depth");
    private boolean depthCopied;

    public WindowFrostHandler(ISeasonalInformation seasonalInformation, IWeatherSimulation weatherSimulation, ITagLibrary tagLibrary,
                              Configuration config, IModLog logger) {
        super("Window Frost Handler", config, logger);
        this.seasonalInformation = seasonalInformation;
        this.weatherSimulation = weatherSimulation;
        this.tagLibrary = tagLibrary;

        IBlockUpdates.EVENT.register(this::blockUpdates);
        ILightUpdate.EVENT.register(this::lightUpdated);
        IChunkLoad.EVENT.register(this::chunkLoaded);
    }

    /**
     * Whether frost is drawn on windows
     */
    public boolean isActive() {
        return this.config.weatherOptions.enableWindowFrost && FrostRenderer.isAvailable() && GameUtils.isInGame();
    }

    @Override
    public void process(final Player player) {
        if (!this.isActive() || !(player.level() instanceof ClientLevel current)) {
            this.clear();
            return;
        }
        // Another dimension: nothing found in the last one applies
        if (current != this.level) {
            this.field.clear();
            this.level = current;
        }

        var pos = player.blockPosition();
        boolean isForced = !Float.isNaN(this.forced);
        if (!isForced && FrostConditions.coldness(this.seasonalInformation.getTemperatureAt(pos) - WARM_MARGIN) <= 0F) {
            this.field.clear();
            return;
        }

        // The sun's angle, as a share of the day; 0 is noon
        float timeOfDay = DayCycle.getCelestialAngle(current, player.position()) / 360F;
        float clearSky = 1F - Math.min(1F, this.weatherSimulation.rainLevel(current.getRainLevel(1F)));
        this.world.level = current;
        FrostField.FrostTarget target = isForced
                ? face -> this.forced
                : face -> FrostConditions.target(face, timeOfDay, clearSky);
        this.field.update(current, this.world, pos.getX(), pos.getY(), pos.getZ(), target);
        // Set by /dsweather: shown as it is set, without growing or melting into it
        if (isForced)
            this.field.forEachFace(face -> face.show(this.forced));
    }

    /**
     * Draws the frost in the level's main pass, after the translucent terrain, tested in the shader against the scene's
     * depth from before the translucent things, so those in front of it don't hide it. The frost is drawn over them
     * (untinted), and over those behind it.
     * <ul>
     *     <li>Without Fabulous graphics, all of it, to the main target, against a copy of its depth (see
     *     {@link #copyDepthBeforeTranslucent}). The particles are drawn before it on 26.2; the breath writes no depth (it
     *     is drawn soft, see FrostBreathParticle), so it doesn't cut holes in the frost, though a particle that does
     *     write depth (vanilla's smoke) still can.</li>
     *     <li>With Fabulous graphics, the frost on stained glass, to the translucent target, against the main target's
     *     depth (which never gets the translucent things). The translucent target holds one depth a pixel, the nearest:
     *     seen through the stained glass the frost is on, that is the glass, so the water and ice behind it are one
     *     layer with it, in front of frost drawn to the weather target, and would be laid over it. Drawn into that layer
     *     instead, on top. The glass is always there, so the layer's depth stays right; not so for clear glass, which
     *     isn't drawn there (see {@link #renderBeforeWeather}).</li>
     * </ul>
     */
    public void renderAfterTranslucentTerrain() {
        if (fabulous()) {
            var translucent = GameUtils.getMC().levelRenderer.getTranslucentTarget();
            var main = GameUtils.getMC().getMainRenderTarget();
            this.drawn = translucent == null ? 0
                    : this.render(translucent, FrostRenderer.SHADER, this.translucentFrostDraw, main.getDepthTextureView(), FrostFace::translucent);
        } else {
            this.drawn = this.depthCopied
                    ? this.render(GameUtils.getMC().getMainRenderTarget(), FrostRenderer.SHADER, this.frostDraw, this.sceneDepth.view(), face -> true)
                    : 0;
        }
        this.depthCopied = false;
    }

    /**
     * Copies the scene's depth before the translucent features and blocks are drawn, without Fabulous graphics, for the
     * frost drawn after them to test against: water or stained glass in front of the frost then doesn't hide it, while
     * what is solid still does. Only while there is frost to draw.
     */
    public void copyDepthBeforeTranslucent() {
        this.depthCopied = false;
        if (fabulous() || !this.isActive() || this.field.faceCount() == 0)
            return;
        FrostRenderer.SHADER.run(() -> {
            this.sceneDepth.copyFrom(GameUtils.getMC().getMainRenderTarget());
            this.depthCopied = true;
        }, () -> this.depthCopied = false);
    }

    /**
     * Draws the frost on clear glass with Fabulous graphics, in the weather pass and before the weather: to the
     * weather's target, which is laid over the scene by depth, so its depth is written. The frost on stained glass is
     * drawn before, to the translucent target (see {@link #renderAfterTranslucentTerrain}).
     */
    public void renderBeforeWeather() {
        if (fabulous())
            this.drawn += this.render(OutputTarget.WEATHER_TARGET.getRenderTarget(), FrostRenderer.SHADER_DEPTH, this.frostDraw, null, face -> !face.translucent());
    }

    private static boolean fabulous() {
        return Minecraft.useShaderTransparency();
    }

    /**
     * Draws the faces {@code which} picks
     *
     * @param depth the depth the shader tests against, bound as {@link FrostRenderer#DEPTH_SAMPLER}, for
     *              {@link FrostRenderer#SHADER}; null for {@link FrostRenderer#SHADER_DEPTH}
     * @return how many faces were drawn
     */
    private int render(RenderTarget target, ModShader shader, MeshDraw meshDraw, @Nullable GpuTextureView depth, Predicate<FrostFace> which) {
        if (!this.isActive() || this.field.faceCount() == 0)
            return 0;

        // Made the first time, on a thread of its own: no frost until it is ready
        var texture = FrostTexture.view();
        if (texture == null)
            return 0;

        int[] drawn = {0};
        var camera = GameUtils.getMC().gameRenderer.getMainCamera();
        var lightmap = GameUtils.getMC().gameRenderer.lightTexture().getTextureView();
        if (this.meshBytes == null)
            this.meshBytes = new ByteBufferBuilder(4096);
        FrostRenderer.RENDER_TIMER.begin();
        try {
            shader.run(() -> drawn[0] = FrostRenderer.draw(this.field, this.meshBytes, mesh -> meshDraw.draw(mesh, shader.pipeline(), target, pass -> {
                pass.bindTexture("Sampler0", texture, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR, true));
                pass.bindTexture("Sampler2", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                if (depth != null)
                    pass.bindTexture(FrostRenderer.DEPTH_SAMPLER, depth, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            }), camera, which), () -> {});
        } finally {
            FrostRenderer.RENDER_TIMER.end();
        }
        return drawn[0];
    }

    /**
     * Gives every window this much frost, 0 to 1, whatever the weather, until {@link #clearForced}; for /dsweather
     */
    public void setForced(float frost) {
        this.forced = frost;
    }

    public void clearForced() {
        this.forced = Float.NaN;
    }

    public Optional<Float> forced() {
        return Float.isNaN(this.forced) ? Optional.empty() : Optional.of(this.forced);
    }

    @Override
    public void onDisconnect() {
        this.clear();
        this.clearForced();
    }

    private void clear() {
        this.field.clear();
        this.level = null;
        this.world.level = null;
    }

    private void blockUpdates(LongCollection positions) {
        if (this.level == null)
            return;
        var it = positions.iterator();
        while (it.hasNext()) {
            long pos = it.nextLong();
            this.field.blockChanged(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
        }
    }

    private void lightUpdated(LightLayer layer, SectionPos section) {
        if (this.level != null)
            this.field.lightChanged(section.x(), section.y(), section.z());
    }

    private void chunkLoaded(ClientLevel level, ChunkPos chunkPos) {
        if (level == this.level)
            this.field.chunkChanged(chunkPos.x, chunkPos.z);
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        String text;
        if (!this.config.weatherOptions.enableWindowFrost)
            text = "Window frost: off";
        else if (!FrostRenderer.isAvailable())
            text = "Window frost: off (shader unavailable, failed, or a shader pack in use)";
        else
            text = "Window frost: %d faces in %d sections, %d drawn%s".formatted(this.field.faceCount(), this.field.sectionCount(),
                    this.drawn, Float.isNaN(this.forced) ? "" : " (set by /dsweather)");
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }

    /**
     * The client's level, as frost sees it
     */
    private final class LevelWorld implements IFrostWorld {
        ClientLevel level;

        @Override
        public BlockGetter blocks() {
            return this.level;
        }

        @Override
        public boolean frosts(BlockState state) {
            return WindowFrostHandler.this.tagLibrary.is(BlockEffectTags.FROSTED_WINDOWS, state);
        }

        @Override
        public boolean translucent(BlockState state) {
            return ItemBlockRenderTypes.getChunkRenderType(state) == ChunkSectionLayer.TRANSLUCENT;
        }

        @Override
        public boolean seesSky(BlockPos pos) {
            // The client's MOTION_BLOCKING heightmap: glass counts, so a greenhouse roof is a roof
            return pos.getY() >= this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        }

        @Override
        public int skyLight(BlockPos pos) {
            return this.level.getBrightness(LightLayer.SKY, pos);
        }

        @Override
        public int packedLight(BlockPos pos) {
            return LevelRenderer.getLightColor(this.level, pos);
        }

        @Override
        public float temperature(BlockPos pos) {
            return WindowFrostHandler.this.seasonalInformation.getTemperatureAt(pos);
        }
    }
}
