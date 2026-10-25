package org.orecruncher.dsurround.processing;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.IDimensionInformation;
import org.orecruncher.dsurround.effects.clouds.CloudField;
import org.orecruncher.dsurround.effects.clouds.CloudLights;
import org.orecruncher.dsurround.effects.clouds.CloudRenderer;
import org.orecruncher.dsurround.effects.clouds.CloudSections;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.weather.CloudCover;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.UpperWind;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Clouds that change with the weather ({@link CloudCover}), as blocks ({@link CloudField}), drawn in place of vanilla's
 * ({@link CloudRenderer}). They ease toward what the weather calls for, so a storm's deck closes in over a minute or so
 * rather than at once.
 * <p>
 * Every player in a world sees the same clouds: they drift with the wind up high ({@link UpperWind}) and change shape as
 * the game time goes on, both from the game time alone, and are as cloudy over each cell as the biome under it calls
 * for (the player's, where that chunk isn't loaded), wet ground cloudier than dry, so they build and thin as they
 * drift. The storm is everyone's too. Only the moment a cell changes, and its holding near an edge, are a player's own.
 * <p>
 * Off with a shader pack, when its shader didn't load or failed, with vanilla's clouds turned off, and in a dimension
 * without them ({@code dynamicClouds} in the dimension configs; by default, those that draw vanilla's clouds).
 * {@code /dsweather clouds} holds their coverage, and the thickness of the thickest, for seeing how they look.
 */
public class CloudHandler extends AbstractClientHandler {

    /**
     * What /dsweather clouds holds: the coverage, 0 to 1, and the thickness of the thickest in blocks, or NaN for the
     * weather's
     */
    public record Forced(float coverage, float thickness) {
    }

    /**
     * The share of the way the storm the clouds show moves toward the weather's each tick
     */
    static final float EASE_PER_TICK = 0.004F;
    /**
     * How finely the clouds follow a biome's wetness: what they are like is worked out each tick for this many steps of
     * it, from dry to wet, and each cell takes the nearest
     */
    static final int WETNESS_STEPS = 10;
    /**
     * How fast the clouds change shape, in cells of noise a tick of the game time: a cloud builds or fades over a few
     * minutes
     */
    static final double CHANGE_PER_TICK = 1D / 3000D;
    /**
     * Each cell is worked out again once in this many ticks, as they change shape, each on a tick of its own: so the
     * changes are spread over the sky and the second, not all at once. A change fades over as long, so it is over by
     * the cell's next turn
     */
    static final int UPDATE_TICKS = CloudField.FADE_TICKS;
    /**
     * The quads of cells that changed are built at most this often, in ticks: in a storm many cells change each tick,
     * here and there, and building their sections every few ticks costs a third as much
     */
    static final int SECTION_TICKS = 3;
    // The fewest and the most cells out from the camera
    private static final int MIN_RADIUS = 16;
    private static final int MAX_RADIUS = 48;

    private final IWeatherSimulation weatherSimulation;
    private final IDimensionInformation dimensionInformation;
    private final CloudField field = new CloudField();
    private final UpperWind upperWind = new UpperWind();
    private final LevelCovers covers;
    // The quads, in sections built again as their cells change; and the tick they were last built on
    private final CloudSections sections = new CloudSections();
    private long sectionsBuilt = -SECTION_TICKS;
    private boolean started;
    // The storm the clouds show, easing toward the weather's
    private float rainLevel;
    private float thunder;
    // The coverage the weather calls for over the player, for the diagnostics
    private float targetCoverage;
    // How far the clouds have drifted, in blocks, as of the tick and as drawn
    private final double[] drift = new double[2];
    private final double[] drawnDrift = new double[2];
    // Ticks the clouds have been worked out: the fading is timed by them
    private long ticks;
    // Set by command, or null for the weather's; and whether the clouds are to be worked out again at once for it
    @Nullable
    private Forced forced;
    private boolean refresh;
    // The way the high streaks run: the day's wind (see UpperWind.heading), so it changes only once a day
    private float streakX = 1F;
    private float streakZ;
    // The light inside the clouds each frame, and what gives it (the lightning)
    private final CloudLights lights = new CloudLights();
    @Nullable
    private CloudLights.Source lightSource;

    public CloudHandler(IWeatherSimulation weatherSimulation, IBiomeLibrary biomeLibrary,
                        IDimensionInformation dimensionInformation, Configuration config, IModLog logger) {
        super("Cloud Handler", config, logger);
        this.weatherSimulation = weatherSimulation;
        this.dimensionInformation = dimensionInformation;
        this.covers = new LevelCovers(biomeLibrary);
    }

    /**
     * Whether the clouds are the mod's
     */
    public boolean isActive() {
        return this.config.weatherOptions.enableDynamicClouds && CloudRenderer.isUsable() && GameUtils.isInGame()
                && GameUtils.getMC().options.getCloudsType() != CloudStatus.OFF && this.dimensionInformation.dynamicClouds();
    }

    @Override
    public void process(final Player player) {
        if (!this.isActive() || !(player.level() instanceof ClientLevel level)) {
            this.reset();
            return;
        }
        // Not until the player's own chunk is there, as on arriving: the clouds anywhere not yet loaded are estimated
        // from the player's biome
        var at = player.blockPosition();
        if (!this.started && !level.hasChunk(SectionPos.blockToSectionCoord(at.getX()), SectionPos.blockToSectionCoord(at.getZ())))
            return;

        // The storm, easing toward the weather's: at once when arriving in a world, or when set by command
        float rain = this.weatherSimulation.rainLevel(level.getRainLevel(1F));
        float thunder = level.getThunderLevel(1F);
        var held = this.forced;
        if (!this.started || held != null) {
            this.rainLevel = rain;
            this.thunder = thunder;
        } else {
            this.rainLevel += (rain - this.rainLevel) * EASE_PER_TICK;
            this.thunder += (thunder - this.thunder) * EASE_PER_TICK;
        }
        if (!this.started) {
            this.started = true;
            this.refresh = true;
        }

        // What the clouds are like for each step of a biome's wetness, and over the player
        long dayTime = level.getDayTime();
        float dayCover = CloudCover.dayCover(dayTime);
        this.covers.prepare(level, this.rainLevel, this.thunder, dayTime, dayCover, held, this.dimensionInformation.seaLevel(),
                player.blockPosition());
        var target = CloudCover.of(rain, thunder, dayTime, this.covers.playerWetness(), dayCover);
        this.targetCoverage = held == null ? target.coverage() : target.held(held.coverage(), held.thickness()).coverage();

        // Where the wind up high has carried them, and the way the streaks run today: the same for everyone
        long gameTime = level.getGameTime();
        this.upperWind.drift(gameTime, this.drift);
        this.covers.drift(this.drift[0], this.drift[1]);
        float heading = UpperWind.heading(UpperWind.dayOf(gameTime));
        this.streakX = Mth.cos(heading);
        this.streakZ = Mth.sin(heading);
        this.ticks++;

        // The cell the camera is under, in cloud space
        var camera = GameUtils.getMC().gameRenderer.getMainCamera().getPosition();
        int cx = Mth.floor((camera.x - this.drift[0]) / CloudField.CELL);
        int cz = Mth.floor((camera.z - this.drift[1]) / CloudField.CELL);
        int radius = this.radius();
        // The cells whose turn it is, and any new to the grid as the camera moves; all of them when starting, or when set
        // by command
        int turns = this.refresh ? 1 : UPDATE_TICKS;
        int turn = this.refresh ? 0 : (int) (this.ticks % UPDATE_TICKS);
        this.refresh = false;
        CloudRenderer.FIELD_TIMER.begin();
        try {
            this.field.update(this.covers, cx, cz, radius, gameTime * CHANGE_PER_TICK, this.streakX, this.streakZ,
                    this.ticks, turn, turns);
        } finally {
            CloudRenderer.FIELD_TIMER.end();
        }
    }

    /**
     * How far the clouds reach, in blocks: the Cloud Distance option
     */
    private int cloudBlocks() {
        return this.config.weatherOptions.cloudDistance * 16;
    }

    /**
     * How many cells out from the camera the clouds go: a little past their distance, so they have faded out by the
     * grid's edge
     */
    private int radius() {
        return Mth.clamp(this.cloudBlocks() / CloudField.CELL + 2, MIN_RADIUS, MAX_RADIUS);
    }

    /**
     * Draws the clouds, from where the level calls for vanilla's
     *
     * @param modelView the view's rotation, with the camera at the origin
     * @return true if they were drawn, in place of vanilla's
     */
    public boolean render(Matrix4f modelView, Matrix4f projection, float partialTick, double camX, double camY, double camZ) {
        if (!this.isActive() || !this.started || this.field.radius() < 0 || !(GameUtils.getWorld().orElse(null) instanceof ClientLevel level))
            return false;

        // The quads of the cells that changed, every few ticks: a change's fade is timed from when it happened, so
        // one built a tick or two late only shows it a little way in
        if (this.ticks - this.sectionsBuilt >= SECTION_TICKS || this.sections.sections() == 0) {
            if (this.sections.update(this.field))
                this.sectionsBuilt = this.ticks;
        }

        this.upperWind.drift(level.getGameTime() + partialTick, this.drawnDrift);
        double driftX = this.drawnDrift[0];
        double driftZ = this.drawnDrift[1];
        float offsetX = (float) ((double) this.sections.anchorX() * CloudField.CELL + driftX - camX);
        float offsetZ = (float) ((double) this.sections.anchorZ() * CloudField.CELL + driftZ - camZ);
        float offsetY = (float) (this.dimensionInformation.getCloudHeight() - camY);

        // The light on the clouds: vanilla's for the time of day, grayer and dimmer as the sky closes over
        float day = Mth.clamp(Mth.cos(level.getTimeOfDay(partialTick) * Mth.TWO_PI) * 2F + 0.5F, 0F, 1F);
        float red = day * 0.9F + 0.1F;
        float green = day * 0.9F + 0.1F;
        float blue = day * 0.85F + 0.15F;
        float overcast = this.cover().overcast();
        if (overcast > 0F) {
            float gray = (red * 0.3F + green * 0.59F + blue * 0.11F) * (1F - 0.45F * overcast);
            red = Mth.lerp(overcast, red, gray * 0.94F);
            green = Mth.lerp(overcast, green, gray * 0.97F);
            blue = Mth.lerp(overcast, blue, gray * 1.05F);
        }
        this.lights.clear();
        var source = this.lightSource;
        if (source != null)
            source.offer(this.lights, partialTick, camX, camY, camZ);
        return CloudRenderer.render(this.sections, modelView, projection, offsetX, offsetY, offsetZ, red, green, blue,
                this.ticks + partialTick - this.sections.epoch(), this.field.radius() * CloudField.CELL, this.cloudBlocks(),
                this.lights);
    }

    /**
     * Sets what lights the clouds from inside, each frame (the lightning, whose glow lights the deck rather than being
     * drawn on its own while the clouds are the mod's)
     */
    public void setLightSource(@Nullable CloudLights.Source source) {
        this.lightSource = source;
    }

    /**
     * Whether the clouds are the mod's and drawn: lightning lights them, in place of drawing its glow
     */
    public boolean isLit() {
        return this.isActive() && this.started;
    }

    /**
     * The lowest the clouds' base is now, in the world, or NaN when the clouds aren't the mod's
     */
    public float cloudBase() {
        if (!this.isActive() || !this.started || this.field.isEmpty())
            return Float.NaN;
        return this.dimensionInformation.getCloudHeight() + this.field.lowestBase();
    }

    /**
     * Holds the clouds' coverage, 0 to 1, and the thickness of the thickest, in blocks, until {@link #clearForced};
     * NaN leaves either to the weather
     */
    public void setForced(float coverage, float thickness) {
        this.forced = new Forced(coverage, thickness);
        this.refresh = true;
    }

    public void clearForced() {
        this.forced = null;
        this.refresh = true;
    }

    public Optional<Forced> forced() {
        return Optional.ofNullable(this.forced);
    }

    /**
     * What the clouds are like now, over the player
     */
    public CloudCover cover() {
        return this.covers.overPlayer();
    }

    private void reset() {
        if (this.started) {
            this.field.clear();
            this.sections.clear();
            this.sectionsBuilt = -SECTION_TICKS;
            this.started = false;
        }
    }

    @Override
    public void onDisconnect() {
        this.reset();
        this.forced = null;
        CloudRenderer.release();
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        String text;
        if (!this.config.weatherOptions.enableDynamicClouds)
            text = "Clouds: vanilla's";
        else if (!CloudRenderer.isUsable())
            text = "Clouds: vanilla's (shader unavailable, failed, or a shader pack in use)";
        else if (!this.isActive())
            text = "Clouds: vanilla's or none here";
        else {
            var cover = this.cover();
            text = String.format(Locale.ROOT, "Clouds: cover %.2f (for %.2f), base -%.0f, %.0f-%.0f thick, cirrus %.2f, overcast %.2f, wet %.1f, %d cells, %d high, drift %.0f, %.0f",
                    cover.coverage(), this.targetCoverage, cover.baseDrop(), cover.minThickness(), cover.maxThickness(),
                    cover.cirrus(), cover.overcast(), this.covers.playerWetness(),
                    this.field.count(CloudField.Layer.LOW), this.field.count(CloudField.Layer.HIGH), this.drift[0], this.drift[1])
                    + (this.forced == null ? "" : " (set by /dsweather)");
        }
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }

    /**
     * What the clouds are like over each cell, from the biome under it: worked out each tick for each step of wetness,
     * and looked up for a cell from where it is in the world now. Where its chunk isn't loaded, the player's biome's.
     */
    private static final class LevelCovers implements CloudField.Cover {

        // The most squares' wetness kept before forgetting them: a cloud grid's worth, and some
        private static final int MAX_WETNESS = 1 << 17;

        private final IBiomeLibrary biomeLibrary;
        private final CloudCover[] byWetness = new CloudCover[WETNESS_STEPS + 1];
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        @Nullable
        private ClientLevel level;
        private double driftX;
        private double driftZ;
        private int sampleY;
        private float playerWetness = 0.5F;
        // The biome's wetness by the 4 by 4 block squares biomes come in, at the sample height: the clouds drift a block
        // or two between a cell's turns, so most cells read the same square again. Forgotten with the level, or once it
        // holds a good deal
        private final Long2FloatOpenHashMap wetness = new Long2FloatOpenHashMap();

        LevelCovers(IBiomeLibrary biomeLibrary) {
            this.biomeLibrary = biomeLibrary;
            Arrays.fill(this.byWetness, CloudCover.CLEAR);
            this.wetness.defaultReturnValue(Float.NaN);
        }

        /**
         * @param held    what /dsweather clouds holds, or null
         * @param sampleY the height biomes are read at: the dimension's sea level, the surface's biomes
         */
        void prepare(ClientLevel level, float rainLevel, float thunder, long dayTime, float dayCover, @Nullable Forced held,
                     int sampleY, BlockPos player) {
            if (level != this.level || sampleY != this.sampleY || this.wetness.size() > MAX_WETNESS)
                this.wetness.clear();
            this.level = level;
            this.sampleY = sampleY;
            this.playerWetness = this.wetnessAt(player.getX(), player.getZ(), 0.5F);
            for (int w = 0; w <= WETNESS_STEPS; w++) {
                var cover = CloudCover.of(rainLevel, thunder, dayTime, w / (float) WETNESS_STEPS, dayCover);
                this.byWetness[w] = held == null ? cover : cover.held(held.coverage(), held.thickness());
            }
        }

        void drift(double x, double z) {
            this.driftX = x;
            this.driftZ = z;
        }

        float playerWetness() {
            return this.playerWetness;
        }

        CloudCover overPlayer() {
            return this.byWetness[step(this.playerWetness)];
        }

        @Override
        public CloudCover at(int cx, int cz) {
            return this.byWetness[step(this.wetnessAt(this.blockX(cx), this.blockZ(cz), this.playerWetness))];
        }

        /**
         * Whether the chunk under a cell is loaded: otherwise its clouds are the player's biome's, an estimate
         */
        @Override
        public boolean isKnown(int cx, int cz) {
            var current = this.level;
            return current != null && current.hasChunk(SectionPos.blockToSectionCoord(this.blockX(cx)),
                    SectionPos.blockToSectionCoord(this.blockZ(cz)));
        }

        // Where in the world the middle of a cell is now, as it has drifted
        private int blockX(int cx) {
            return Mth.floor((cx + 0.5D) * CloudField.CELL + this.driftX);
        }

        private int blockZ(int cz) {
            return Mth.floor((cz + 0.5D) * CloudField.CELL + this.driftZ);
        }

        /**
         * The biome's downfall at a place, or {@code otherwise} where its chunk isn't loaded
         */
        private float wetnessAt(int x, int z, float otherwise) {
            var current = this.level;
            if (current == null || !current.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)))
                return otherwise;
            long square = ((long) (x >> 2) << 32) | ((z >> 2) & 0xFFFFFFFFL);
            float known = this.wetness.get(square);
            if (!Float.isNaN(known))
                return known;
            var biome = current.getBiome(this.pos.set(x, this.sampleY, z)).value();
            float wet = this.biomeLibrary.getBiomeInfo(biome).getDownfall();
            this.wetness.put(square, wet);
            return wet;
        }

        private static int step(float wetness) {
            return Mth.clamp(Math.round(wetness * WETNESS_STEPS), 0, WETNESS_STEPS);
        }
    }
}
