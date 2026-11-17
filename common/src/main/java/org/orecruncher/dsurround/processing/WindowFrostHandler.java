// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import it.unimi.dsi.fastutil.longs.LongCollection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.DepthCopy;
import org.orecruncher.dsurround.effects.MeshDraw;
import org.orecruncher.dsurround.effects.QuadWriter;
import org.orecruncher.dsurround.effects.frost.FrostConditions;
import org.orecruncher.dsurround.effects.frost.FrostField;
import org.orecruncher.dsurround.effects.frost.FrostRenderer;
import org.orecruncher.dsurround.effects.frost.FrostTexture;
import org.orecruncher.dsurround.effects.frost.IFrostWorld;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IBlockUpdates;
import org.orecruncher.dsurround.eventing.IChunkLoad;
import org.orecruncher.dsurround.eventing.ILightUpdate;
import org.orecruncher.dsurround.eventing.IWorldRender;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.time.DayCycle;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.tags.BlockEffectTags;

import java.util.Optional;

/**
 * Frost on the outside of windows when it is cold (see {@link FrostConditions}): on glass blocks and panes near the
 * player ({@link FrostField}), stained glass included. Drawn after the translucent blocks, tested against the depth they
 * left, and before the rain and snow.
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
    private QuadWriter quads;
    private final MeshDraw frostDraw = new MeshDraw("window frost");
    // The depth the level's main pass left
    private final DepthCopy sceneDepth = new DepthCopy("Dynamic Surroundings window frost depth");

    public WindowFrostHandler(ISeasonalInformation seasonalInformation, IWeatherSimulation weatherSimulation, ITagLibrary tagLibrary,
                              Configuration config, IModLog logger) {
        super("Window Frost Handler", config, logger);
        this.seasonalInformation = seasonalInformation;
        this.weatherSimulation = weatherSimulation;
        this.tagLibrary = tagLibrary;

        IBlockUpdates.EVENT.register(this::blockUpdates);
        IWorldRender.EVENT.register(this::render, IWorldRender.WINDOW_FROST);
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
     * Draws the frost once the level's main pass is closed, tested in the shader against a copy of the depth the pass
     * left, which is the only time one can be made. The particles are drawn before it; the breath writes no depth (it
     * is drawn soft, see FrostBreathParticle), so it doesn't cut holes in the frost, though a particle that does write
     * depth (vanilla's smoke) still can.
     */
    private void render() {
        this.drawn = 0;
        if (!this.isActive() || this.field.faceCount() == 0)
            return;
        var main = GameUtils.getMC().gameRenderer.mainRenderTarget();
        boolean[] copied = {false};
        FrostRenderer.SHADER.run(() -> {
            this.sceneDepth.copyFrom(main);
            copied[0] = true;
        }, () -> {});
        if (copied[0])
            this.drawn = this.draw(main);
    }

    /**
     * @return how many faces were drawn
     */
    private int draw(RenderTarget target) {

        // Made the first time, on a thread of its own: no frost until it is ready
        var texture = FrostTexture.view();
        if (texture == null)
            return 0;

        int[] drawn = {0};
        var camera = GameUtils.getMC().gameRenderer.mainCamera();
        var lightmap = GameUtils.getMC().gameRenderer.lightmap();
        if (this.quads == null)
            this.quads = new QuadWriter(QuadWriter.Format.PARTICLE);
        FrostRenderer.RENDER_TIMER.begin();
        try {
            FrostRenderer.SHADER.run(() -> {
                drawn[0] = FrostRenderer.build(this.field, this.quads, camera);
                this.frostDraw.draw(this.quads, FrostRenderer.SHADER.pipeline(), target, pass -> {
                    pass.setUniform("Sampler0", texture, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR, true));
                    pass.setUniform("Sampler2", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                    pass.setUniform(FrostRenderer.DEPTH_SAMPLER, this.sceneDepth.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                });
            }, () -> {});
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
            this.field.chunkChanged(chunkPos.x(), chunkPos.z());
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
            return LightCoordsUtil.getLightCoords(this.level, pos);
        }

        @Override
        public float temperature(BlockPos pos) {
            return WindowFrostHandler.this.seasonalInformation.getTemperatureAt(pos);
        }
    }
}
