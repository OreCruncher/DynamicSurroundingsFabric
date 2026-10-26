package org.orecruncher.dsurround.processing;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.clouds.CloudLights;
import org.orecruncher.dsurround.effects.lightning.LightningRenderer;
import org.orecruncher.dsurround.effects.lightning.Strike;
import org.orecruncher.dsurround.effects.lightning.Strikes;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.WindService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightning (see {@code effects.lightning}): the game's lightning bolts drawn as branching, flickering strikes, and,
 * through a thunderstorm, strikes of the mod's own in the sky around the player, most of them upwind, where the storm
 * comes from, each followed by its thunder, later
 * the farther away it is. How strong the lightning is follows the thunder and the storm's strength: more flashes, more
 * branches and more strokes the stronger it is.
 * <p>
 * Shown while scary content and lightning are allowed (Configuration.ScaryContent) and its shader can be used (not with
 * a shader pack); the game's own bolts are drawn otherwise. With Minecraft's Hide Lightning Flashes option on, the sky
 * has no lightning of the mod's own and bolts strike once, without flickering; the game's flash of the sky, which the
 * mod's strikes close by set off too, already keeps to that option.
 */
public class LightningHandler extends AbstractClientHandler {

    /**
     * The most strikes at once
     */
    static final int MAX_STRIKES = 8;
    /**
     * How high above the ground a strike starts, at least and at most, in blocks: from the cloud base, if it is in
     * between
     */
    static final float MIN_HEIGHT = 60F;
    static final float MAX_HEIGHT = 160F;
    // The game's bolts are drawn as if the storm were at least this strong: a bolt close by is a big one
    private static final float BOLT_INTENSITY = 0.6F;
    // The sky's thunder plays this far from the player toward the strike, so it is heard from that way
    private static final double THUNDER_OFFSET = 8D;
    /**
     * The glow lighting the mod's clouds from inside: how far up into the cloud each patch of it is, in blocks; how
     * much farther its light reaches than the patch would; and how bright it is against the patch
     */
    static final float DECK_DEPTH = 4F;
    static final float DECK_REACH = 1.3F;
    static final float DECK_LIGHT = 1.2F;
    // A flash in the cloud is heard this often; the rest are too far in the cloud to hear
    private static final float SHEET_THUNDER = 0.6F;
    private static final ResourceLocation THUNDER = Constants.asId("thunder");
    private static final String SKY_LIGHTNING = "sky lightning";

    private final IWeatherSimulation weatherSimulation;
    private final WindService windService;
    private final CloudHandler clouds;
    private final List<Active> strikes = new ArrayList<>();
    // The game's bolts drawn as strikes, by entity id, until they are gone
    private final IntSet bolts = new IntOpenHashSet();
    // The level the strikes are in, held weakly so a world left isn't kept in memory
    private WeakReference<ClientLevel> level = new WeakReference<>(null);
    private float intensity;
    private String status = "none";
    private int drawn;
    // Handed to the renderer each frame
    private final List<Strike> toDraw = new ArrayList<>();

    private static final class Active {
        final Strike strike;
        // Its age at the last tick, for the strokes since
        float lastAge;

        Active(Strike strike) {
            this.strike = strike;
        }
    }

    public LightningHandler(IWeatherSimulation weatherSimulation, WindService windService, CloudHandler clouds,
                            Configuration config, IModLog logger) {
        super("Lightning Handler", config, logger);
        this.weatherSimulation = weatherSimulation;
        this.windService = windService;
        this.clouds = clouds;
        clouds.setLightSource(this::lightClouds);
    }

    /**
     * Whether the mod draws lightning
     */
    public boolean isActive() {
        return this.config.scaryContent.lightningAllowed() && LightningRenderer.isUsable();
    }

    private static boolean hideFlashes() {
        return GameUtils.getGameSettings().hideLightningFlash().get();
    }

    @Override
    public void process(final Player player) {
        if (!(player.level() instanceof ClientLevel level))
            return;
        this.inLevel(level);
        if (!this.isActive()) {
            this.clear();
            this.intensity = 0F;
            this.status = this.config.scaryContent.lightningAllowed() ? "off (shader unavailable, failed, or a shader pack in use)" : "off";
            return;
        }

        long now = level.getGameTime();
        this.tickStrikes(level, now);
        if (now % 20 == 0)
            this.bolts.removeIf((int id) -> level.getEntity(id) == null);

        float thunder = level.getThunderLevel(1F);
        this.intensity = Strikes.intensity(thunder, this.weatherSimulation.rainLevel(level.getRainLevel(1F)));
        var blocked = skyBlocked(level, thunder);
        // Formatted only when the diagnostics are shown: this runs every tick
        this.status = blocked == null ? SKY_LIGHTNING : blocked;
        if (blocked == null && this.strikes.size() < MAX_STRIKES && RANDOM.nextFloat() < Strikes.chancePerTick(this.intensity))
            this.strikeInSky(level, Strikes.pickKind(this.intensity, RANDOM), this.intensity);
    }

    /**
     * Why there is no lightning in the sky here, or null if there can be
     */
    private static String skyBlocked(ClientLevel level, float thunder) {
        if (thunder <= 0F)
            return "no thunderstorm";
        if (hideFlashes())
            return "no sky lightning (Hide Lightning Flashes)";
        if (level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL || Float.isNaN(level.effects().getCloudHeight()))
            return "no sky lightning in this dimension";
        return null;
    }

    // Ages the strikes, flashing the sky for each stroke of one close by, and lets go of those that are over
    private void tickStrikes(ClientLevel level, long now) {
        var it = this.strikes.iterator();
        while (it.hasNext()) {
            var active = it.next();
            var strike = active.strike;
            float age = strike.age(now);
            if (flashesSky(strike) && strike.strokesBetween(active.lastAge, age) > 0)
                level.setSkyFlashTime(2);
            active.lastAge = age;
            if (strike.isDone(age))
                it.remove();
        }
    }

    /**
     * Whether a strike's strokes light up the sky and the ground: one of the sky's own close by. The game's bolts
     * flash the sky themselves.
     */
    static boolean flashesSky(Strike strike) {
        return strike.boltId() < 0 && strike.kind() != Strike.Kind.SHEET && strike.distance() <= Strikes.FLASH_RANGE;
    }

    /**
     * A strike of the given kind somewhere in the sky around the player
     */
    private void strikeInSky(ClientLevel level, Strike.Kind kind, float intensity) {
        var camera = cameraPosition();
        float far = Strikes.farthest(GameUtils.getMC().gameRenderer.getDepthFar());
        if (far <= Strikes.NEAREST)
            return;
        // Mostly from upwind, where the storm comes from; from anywhere where the air is still
        var wind = this.windService.wind();
        boolean still = this.windService.steadySpeed() <= 0F;
        float angle = Strikes.strikeAngle(still ? 0F : wind.directionX(), still ? 0F : wind.directionZ(), RANDOM);
        float distance = switch (kind) {
            // Its top is higher and may lean away: kept well inside the view
            case GROUND -> Strikes.spreadDistance(Strikes.NEAREST, far * 0.8F, RANDOM);
            // May be overhead
            case SPIDER -> Strikes.spreadDistance(0F, far * 0.8F, RANDOM);
            case SHEET -> Strikes.spreadDistance(Strikes.NEAREST * 1.5F, far, RANDOM);
        };
        double x = camera.x + Mth.cos(angle) * distance;
        double z = camera.z + Mth.sin(angle) * distance;
        this.add(level, kind, x, z, intensity, -1, RANDOM);
    }

    /**
     * Adds a strike: on the ground at x, z for one from cloud to ground, otherwise around that point in the sky; and its
     * thunder, unless it stands in for one of the game's bolts, which have their own
     */
    private void add(ClientLevel level, Strike.Kind kind, double x, double z, float intensity, int boltId, IRandomizer random) {
        var camera = cameraPosition();
        boolean flicker = !hideFlashes();
        // The base of the mod's clouds, which a storm lowers, or else of vanilla's
        float cloud = this.clouds.cloudBase();
        if (Float.isNaN(cloud))
            cloud = level.effects().getCloudHeight();
        // A dimension without clouds (only a bolt, or /dsweather, strikes there): a cloud base above the player
        if (Float.isNaN(cloud))
            cloud = (float) camera.y + MAX_HEIGHT;
        double now = level.getGameTime();
        Strike strike;
        switch (kind) {
            case GROUND -> {
                double ground = groundHeight(level, x, z);
                float height = Mth.clamp((float) (cloud - ground), MIN_HEIGHT, MAX_HEIGHT);
                // The channel leans: it starts off to one side of where it lands
                float lean = random.nextFloat(0F, 0.3F * height);
                float leanAngle = random.nextFloat() * Mth.TWO_PI;
                float lx = Mth.cos(leanAngle) * lean, lz = Mth.sin(leanAngle) * lean;
                float distance = (float) camera.distanceTo(new Vec3(x, ground, z));
                strike = Strikes.ground(x + lx, ground + height, z + lz, -lx, -height, -lz, intensity, distance, flicker, now,
                        boltId, random);
            }
            case SPIDER -> {
                double y = cloud - random.nextFloat(4F, 16F);
                float length = random.nextFloat(80F, 200F);
                float heading = random.nextFloat() * Mth.TWO_PI;
                float dx = Mth.cos(heading) * length, dz = Mth.sin(heading) * length;
                float drop = random.nextFloat(4F, 16F);
                float distance = (float) camera.distanceTo(new Vec3(x, y, z));
                // Centered on x, z
                strike = Strikes.spider(x - dx / 2F, y, z - dz / 2F, dx, -drop, dz, intensity, distance, flicker, now, random);
            }
            default -> {
                double y = cloud - random.nextFloat(0F, 10F);
                float distance = (float) camera.distanceTo(new Vec3(x, y, z));
                strike = Strikes.sheet(x, y, z, intensity, distance, flicker, now, random);
            }
        }
        // Full: the oldest makes way
        if (this.strikes.size() >= MAX_STRIKES)
            this.strikes.removeFirst();
        this.strikes.add(new Active(strike));
        if (boltId < 0)
            this.thunder(strike, camera, x, z);
    }

    /**
     * The strike's thunder, heard from its direction, later and lower the farther away it is
     */
    private void thunder(Strike strike, Vec3 camera, double x, double z) {
        if (strike.kind() == Strike.Kind.SHEET && RANDOM.nextFloat() > SHEET_THUNDER)
            return;
        float distance = strike.distance();
        double dx = x - camera.x, dz = z - camera.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double px = camera.x, pz = camera.z;
        if (length > 1.0E-3D) {
            px += dx / length * THUNDER_OFFSET;
            pz += dz / length * THUNDER_OFFSET;
        }
        float pitch = Strikes.thunderPitch(distance) * RANDOM.nextFloat(0.95F, 1.05F);
        var sound = new SimpleSoundInstance(THUNDER, SoundSource.WEATHER, Strikes.thunderVolume(distance), pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, px, camera.y, pz, false);
        GameUtils.getSoundManager().playDelayed(sound, Strikes.thunderDelay(distance));
    }

    /**
     * Where the ground is, from the client's heightmap where the chunk is loaded, else sea level
     */
    private static double groundHeight(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (level.hasChunk(SectionPos.blockToSectionCoord(bx), SectionPos.blockToSectionCoord(bz)))
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        return level.getSeaLevel();
    }

    private static Vec3 cameraPosition() {
        return GameUtils.getMC().gameRenderer.getMainCamera().getPosition();
    }

    /**
     * Called as one of the game's lightning bolts comes into the world: draws it as a strike of the mod's, shaped
     * from the bolt's own seed, so everyone sees the same bolt. Client thread.
     */
    public void onBolt(LightningBolt bolt) {
        if (!this.isActive() || !(bolt.level() instanceof ClientLevel level))
            return;
        this.inLevel(level);
        this.add(level, Strike.Kind.GROUND, bolt.getX(), bolt.getZ(), Math.max(BOLT_INTENSITY, this.intensity), bolt.getId(),
                Randomizer.create(bolt.seed));
        this.bolts.add(bolt.getId());
    }

    /**
     * Whether the mod draws this bolt, in place of the game drawing it
     */
    public boolean drawsBolt(LightningBolt bolt) {
        return this.bolts.contains(bolt.getId()) && this.isActive();
    }

    /**
     * Draws the strikes, where vanilla draws its weather, after it
     */
    public void render(float partialTick) {
        this.drawn = 0;
        if (this.strikes.isEmpty() || !this.isActive())
            return;
        var level = GameUtils.getWorld().orElse(null);
        if (level == null)
            return;
        this.toDraw.clear();
        for (var active : this.strikes)
            this.toDraw.add(active.strike);
        var camera = GameUtils.getMC().gameRenderer.getMainCamera();
        // The fog drawn with the weather: how far the view reaches in it, which dims far strikes. And the cloud's glow
        // drawn only when the mod's clouds aren't there to be lit by it (see lightClouds)
        float fogEnd = RenderSystem.getShaderFogEnd();
        this.drawn = LightningRenderer.draw(this.toDraw, camera, level.getGameTime() + partialTick, fogEnd, !this.clouds.isLit());
    }

    /**
     * The glow of each strike as light inside the mod's clouds, where they are drawn: the cloud around a strike lights
     * up from inside, its own shape and all, and a nearer cloud hides it. Asked for as the clouds are drawn, each frame.
     */
    private void lightClouds(CloudLights lights, float partialTick, double cameraX, double cameraY, double cameraZ) {
        if (this.strikes.isEmpty() || !this.isActive())
            return;
        var level = GameUtils.getWorld().orElse(null);
        if (level == null)
            return;
        double now = level.getGameTime() + partialTick;
        for (var active : this.strikes) {
            var strike = active.strike;
            float age = strike.age(now);
            if (age < 0F || strike.isDone(age))
                continue;
            var glow = strike.glow();
            for (int p = 0; p < glow.count(); p++) {
                float power = strike.glowBrightness(p, age) * DECK_LIGHT;
                lights.add((float) (strike.x() + glow.x(p) - cameraX),
                        (float) (strike.y() + glow.y(p) + DECK_DEPTH - cameraY),
                        (float) (strike.z() + glow.z(p) - cameraZ), glow.radius(p) * DECK_REACH, power);
            }
        }
    }

    /**
     * A strike of the given kind ahead of the player now, whatever the weather; for /dsweather
     *
     * @return false if the mod's lightning is off
     */
    public boolean strikeNow(Strike.Kind kind) {
        var level = GameUtils.getWorld().orElse(null);
        if (level == null || !this.isActive())
            return false;
        var camera = GameUtils.getMC().gameRenderer.getMainCamera();
        var look = camera.getLookVector();
        double lx = look.x(), lz = look.z();
        double length = Math.sqrt(lx * lx + lz * lz);
        if (length < 1.0E-3D) {
            lx = 0D;
            lz = -1D;
        } else {
            lx /= length;
            lz /= length;
        }
        double ahead = switch (kind) {
            case GROUND -> 100D;
            case SPIDER -> 80D;
            case SHEET -> 150D;
        };
        var position = camera.getPosition();
        this.add(level, kind, position.x + lx * ahead, position.z + lz * ahead, Math.max(this.intensity, 0.8F), -1, RANDOM);
        return true;
    }

    @Override
    public void onDisconnect() {
        this.clear();
    }

    /**
     * Drops the strikes of another level than this one (a new dimension): they were in its sky, and its bolts' ids are
     * its own
     */
    private void inLevel(ClientLevel level) {
        if (this.level.get() != level) {
            this.clear();
            this.level = new WeakReference<>(level);
        }
    }

    private void clear() {
        this.strikes.clear();
        this.bolts.clear();
        this.drawn = 0;
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        var text = "Lightning: " + this.status;
        if (SKY_LIGHTNING.equals(this.status))
            text += " at %.2f".formatted(this.intensity);
        if (this.isActive())
            text += ", %d strike(s), %d drawn".formatted(this.strikes.size(), this.drawn);
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }
}
