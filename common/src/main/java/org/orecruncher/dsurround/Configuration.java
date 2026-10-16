package org.orecruncher.dsurround;

import org.orecruncher.dsurround.config.ClockStyle;
import org.orecruncher.dsurround.config.CompassStyle;
import org.orecruncher.dsurround.config.GroundFogQuality;
import org.orecruncher.dsurround.config.LightLevelColors;
import org.orecruncher.dsurround.config.LightLevelMode;
import org.orecruncher.dsurround.config.MarginPosition;
import org.orecruncher.dsurround.config.PocketWatchStyle;
import org.orecruncher.dsurround.config.WaterRippleStyle;
import org.orecruncher.dsurround.lib.config.Comment;
import org.orecruncher.dsurround.lib.config.ConfigPlacement;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.config.DoubleSlider;
import org.orecruncher.dsurround.lib.config.Experimental;
import org.orecruncher.dsurround.lib.config.IntegerRange;
import org.orecruncher.dsurround.lib.config.MovedFrom;
import org.orecruncher.dsurround.lib.config.Property;
import org.orecruncher.dsurround.lib.config.RestartRequired;
import org.orecruncher.dsurround.lib.config.Slider;
import org.orecruncher.dsurround.lib.config.TextStyle;
import org.orecruncher.dsurround.lib.config.TranslationRoot;

@ConfigPlacement(folderName = Constants.MOD_ID, fileName = Constants.MOD_ID)
@TranslationRoot(Constants.MOD_ID + ".config")
@SuppressWarnings("unused")
public class Configuration extends ConfigurationData {

    @Property
    @Comment("Configuration options for modifying Minecraft's Sound System behavior")
    public final SoundSystem soundSystem = new SoundSystem();

    @Property
    @Comment("Configuration options for enhanced sound processing")
    public final EnhancedSounds enhancedSounds = new EnhancedSounds();

    @Property
    @Comment("Configuration options for sounds in general")
    public final SoundOptions soundOptions = new SoundOptions();

    @Property
    @Comment("Configuration options for block effects")
    public final BlockEffects blockEffects = new BlockEffects();

    @Property
    @Comment("Configuration options for waterfalls")
    public final WaterfallOptions waterfallOptions = new WaterfallOptions();

    @Property
    @Comment("Configuration options for flame jets")
    public final FlameJetOptions flameJetOptions = new FlameJetOptions();

    @Property
    @Comment("Configuration options for fireflies")
    public final FireflyOptions fireflyOptions = new FireflyOptions();

    @Property
    @Comment("Configuration options for entity effects")
    public final EntityEffects entityEffects = new EntityEffects();

    @Property
    @Comment("Configuration options for footstep accent effects")
    public final FootstepAccents footstepAccents = new FootstepAccents();

    @Property
    @Comment("Configuration options for tweaking particle behavior")
    public final ParticleTweaks particleTweaks = new ParticleTweaks();

    @Property
    @Comment("Configuration options for the compass and clock overlay")
    public final CompassAndClockOptions compassAndClockOptions = new CompassAndClockOptions();

    @Property
    @Comment("Configuration options for the light level overlay")
    public final LightLevelOptions lightLevelOptions = new LightLevelOptions();

    @Property
    @Comment("Configuration options for weather")
    public final WeatherOptions weatherOptions = new WeatherOptions();

    @Property
    @Comment("Configuration options for fog effects")
    public final FogOptions fogOptions = new FogOptions();

    @Property
    @Comment("Configuration options for auroras")
    public final AuroraOptions auroraOptions = new AuroraOptions();

    @Property
    @Comment("Configuration options for modded Music Manager")
    public final MusicManagerOptions musicManagerOptions = new MusicManagerOptions();

    @Property
    @Comment("Configuration options for modifying diagnostic behavior")
    @TextStyle(color = "#0078D4", italic = true)
    public final Logging logging = new Logging();

    @Property
    @Comment("Configuration options for other things")
    public final OtherOptions otherOptions = new OtherOptions();

    public static class Flags {
        public static final int AUDIO_PLAYER = 0x1;
        public static final int BASIC_SOUND_PLAY = 0x2;
        public static final int RESOURCE_LOADING = 0x4;
    }

    public static class Logging {

        @Property
        @Comment("Enables/disables debug logging of the mod")
        public boolean enableDebugLogging = false;

        @Property
        @Comment("Bitmask for toggling various debug traces")
        public int traceMask = 0;

        @Property
        @Comment("Enable/disable chat window notification of newer updates available")
        public boolean enableModUpdateChatMessage = true;

        @Property
        @Comment("Enable/disable filtering display of tags in the diagnostics overlay")
        public boolean filteredTagView = true;

        @Property
        @RestartRequired
        @Comment("Enable/disable registration of client side commands")
        public boolean registerCommands = true;

        @Property
        @Comment("Play your own Simple Voice Chat voice back to you, with enhanced sound processing, for testing")
        @Experimental
        public boolean voiceChatHearSelf = false;
    }

    public static class SoundSystem {
        @Property
        @Slider(min = 8, max = 16)
        @RestartRequired
        @Comment("The number of sound channels to reserve for streaming sounds (music, biome sounds, records, etc.)")
        public int streamingChannels = 12;

        @Property
        @Slider(min = 0, max = 20 * 10)
        @Comment("Ticks between culled sound events (0 to disable culling)")
        public int cullInterval = 20;

        @Property
        @Comment("Enables/disables cancellation of sound that a player will not hear")
        public boolean enableSoundPruning = true;
    }

    public static class EnhancedSounds {
        @Property
        @RestartRequired
        @Comment("Enable/disable enhanced sound processing (reverb, occlusion, etc)")
        public boolean enableEnhancedSounds = true;

        @Property
        @Slider(min = 0, max = 8)
        @RestartRequired
        @Comment("Number of background threads to use for enhanced sound processing (0 means use internal default)")
        public int backgroundThreadWorkers = 0;

        @Property
        @Comment("Enable/disable sound occlusion processing (sound muffling behind blocks)")
        public boolean enableOcclusionProcessing = false;

        @Property
        @Comment("Apply enhanced sound processing to players' voices from Simple Voice Chat")
        @Experimental
        public boolean enableVoiceChatProcessing = true;

        @Property
        @Comment("Check whether reflections reach the player only from each reverb ray's last reflection, instead of from every reflection. Cheaper; may sound different")
        public boolean simplifiedSharedAirspace = false;

        @Property
        @IntegerRange(min = 16, max = 64)
        @RestartRequired
        @Comment("The number of rays to project around a sound location to calculate reverb effect")
        public int reverbRays = 32;

        @Property
        @IntegerRange(min = 2, max = 8)
        @RestartRequired
        @Comment("The number of reflections the ray calculation will perform before ending a ray calculation")
        public int reverbBounces = 4;

        @Property
        @IntegerRange(min = 64, max = 256)
        @RestartRequired
        @Comment("Total distance a reverb ray will traverse before ending calculation")
        // Beyond about 70-100 blocks a longer ray no longer changes which reverb a reflection feeds, only whether a
        // distant surface is found to bounce off; an open-air ray costs in proportion to its length
        public int reverbRayTraceDistance = 128;
    }

    public static class SoundOptions {

        @Property
        @Comment("Discard sounds that are played from a non-client thread")
        public boolean discardNonClientSoundPlays = true;

        @Property
        @Comment("Emit stack trace when discarding sounds")
        public boolean logStacktraceWhenDiscarding = false;

        @Property
        @Slider(min = 0, max = 400)
        @Comment("Ambient sounds played by the mod will be multiplied by this factor")
        public int ambientVolumeScaling = 100;

        @Property
        @Comment("Enables replacement of thunder sounds with Dynamic Surroundings' version")
        public boolean replaceThunderSounds = true;

        @Property
        @Comment("Enables playing sounds that are considered scary")
        public boolean allowScarySounds = true;

        @Property
        @Comment("Enables playing biome background music while in creative")
        public boolean playBiomeMusicWhileCreative = false;

        @Property
        @Comment("Enables display of toast messages for credited music")
        public boolean displayToastMessagesForMusic = true;

        @Property
        @Comment("Enables sound remapping when sounds are played")
        public boolean remapSounds = true;
    }

    public static class BlockEffects {

        @Property
        @Slider(min = 16, max = 64)
        @Comment("Distance that will be scanned when generating block effects")
        public int blockEffectRange = 32;

        @Property
        @Comment("Enable/disable steam column effect when liquids are adjacent to hot sources, like lava and magma")
        public boolean steamColumnEnabled = true;

        @Property
        @Comment("Enable/disable bubble columns generated underwater")
        public boolean bubbleColumnEnabled = true;

        @Property
        @Comment("The style of water ripple to render when a drop hits a fluid")
        public WaterRippleStyle waterRippleStyle = WaterRippleStyle.PIXELATED_CIRCLE;
    }

    public static class FlameJetOptions {
        @Property
        @MovedFrom("blockEffects.flameJetEnabled")
        @Comment("Enable/disable flame jets over lava and nether wart")
        public boolean enableFlameJets = true;

        @Property
        @MovedFrom("blockEffects.flameJetLight")
        @Comment("Enable/disable the flicker of flame jets' light on their surroundings")
        public boolean enableLight = true;
    }

    public static class WaterfallOptions {
        @Property
        @MovedFrom("blockEffects.waterfallsEnabled")
        @Comment("Enable/disable waterfall effects where flowing water lands")
        public boolean enableWaterfalls = true;

        @Property
        @MovedFrom("blockEffects.enableWaterfallSounds")
        @Comment("Enable/disable sounds from waterfalls, and from water stepping down a block")
        public boolean enableSounds = true;

        @Property
        @MovedFrom("blockEffects.enableWaterfallParticles")
        @Comment("Enable/disable particles from waterfalls: their splashes, and the mist and foam where they land")
        public boolean enableParticles = true;

        @Property
        @MovedFrom({"particleEffects.enableWaterfallMist", "worksInProgressOptions.enableWaterfallMist"})
        @Comment("Enable/disable waterfall mist: many small puffs of mist thrown up where a waterfall lands")
        public boolean enableMist = true;

        @Property
        @MovedFrom({"particleEffects.enableWaterStepFroth", "worksInProgressOptions.enableWaterStepFroth"})
        @Comment("Enable/disable water froth: foam where flowing water drops one block (with a gentle sound) and around where waterfalls land")
        public boolean enableFroth = true;
    }

    public static class FireflyOptions {
        @Property
        @MovedFrom("blockEffects.firefliesEnabled")
        @Comment("Enable/disable fireflies")
        public boolean enableFireflies = true;

        @Property
        @MovedFrom({"particleEffects.enableFireflyGlow", "worksInProgressOptions.enableFireflyGlow"})
        @Comment("Enable/disable a soft glow around fireflies")
        public boolean enableGlow = true;

        @Property
        @MovedFrom({"particleEffects.enableFireflyLight", "worksInProgressOptions.enableFireflyLight"})
        @Comment("Enable/disable fireflies lighting the grass, leaves and ground close to them. Not shown while a shader pack is in use")
        public boolean enableLight = true;
    }

    public static class EntityEffects {

        @Property
        @Slider(min = 16, max = 64)
        @Comment("The maximum range at which entity special effects are applied")
        public int entityEffectRange = 24;

        @Property
        @Comment("Enable/disable bow pull sound effect")
        public boolean enableBowPull = true;

        @Property
        @Comment("Enable/disable breath effect in cold biomes and underwater")
        public boolean enableBreathEffect = true;

        @Property
        @Comment("Enable/disable player toolbar sound effects")
        public boolean enablePlayerToolbarEffect = true;

        @Property
        @Comment("Enable/disable sound effects for blocks on the toolbar")
        public boolean enableToolbarBlockSounds = false;

        @Property
        @Comment("Enable/disable item swing sound effects from players and mobs")
        public boolean enableSwingEffect = true;

        @Property
        @Comment("Enable/disable sound effect when walking through dense brush")
        public boolean enableBrushStepEffect = true;
    }

    public static class FootstepAccents {
        @Property
        @Comment("Enable/disable foot step accents globally")
        public boolean enableAccents = true;

        @Property
        @Comment("Enable/disable accents for armor that is worn")
        public boolean enableArmorAccents = true;

        @Property
        @Comment("Enable/disable accents when it is raining or blocks are waterlogged")
        public boolean enableWetSurfaceAccents = true;

        @Property
        @Comment("Enable/disable accents when the player is walking on squeaky blocks")
        public boolean enableFloorSqueaks = true;
    }

    public static class ParticleTweaks {
        @Property
        @Comment("Enable/disable showing of projectile particle trails")
        public boolean suppressProjectileParticleTrails = false;

        @Property
        @Comment("Smoke, ash, leaves and spores, from the game and other mods, drift with the wind out in the open")
        public boolean windBlowsParticles = true;
    }

    public static class CompassAndClockOptions {
        // Each was a switch and a style; the switch is now the style's NONE. The compass style already existed, so
        // its switch being off has to replace whatever style was there.
        @Property
        @Comment("Style of compass rendering, or NONE for no compass display")
        @MovedFrom(value = "compassAndClockOptions.enableCompass", values = "false=NONE", replaces = true)
        public CompassStyle compassStyle = CompassStyle.TRANSPARENT_WITH_INDICATOR;

        @Property
        @Comment("Style of clock rendering, or NONE for no clock display")
        @MovedFrom(value = "compassAndClockOptions.enableClock", values = "false=NONE")
        public ClockStyle clockStyle = ClockStyle.POCKET_WATCH;

        @Property
        @Comment("Look of the pocket watch: CLASSIC (a worn round watch) or SQUARE (a pixel art square watch)")
        public PocketWatchStyle watchStyle = PocketWatchStyle.CLASSIC;

        @Property
        @Comment("Shows the compass and pocket watch in a side or corner of the screen while one is in the hot bar or off hand, rather than above the crosshair while held")
        public boolean showInMargin = false;

        @Property
        @Comment("The side or corner of the screen for the compass and pocket watch when shown there")
        public MarginPosition marginPosition = MarginPosition.LEFT;

        @Property
        @Comment("Scales the display by the specified amount")
        @DoubleSlider(min = 0.5D, max = 4D, step = 0.1D)
        public double scale = 1D;
    }

    public static class LightLevelOptions {
        @Property
        @Comment("The light shown on each block: BLOCK (torches and other lights), SKY (as it is now), or BLOCK_SKY (the brighter of the two)")
        public LightLevelMode mode = LightLevelMode.BLOCK;

        @Property
        @Comment("The colors for the numbers: BRIGHT or DARK")
        public LightLevelColors colors = LightLevelColors.BRIGHT;

        @Property
        @Slider(min = 8, max = 32)
        @Comment("How many blocks out from the player light levels are shown")
        public int range = 16;

        @Property
        @Comment("Shows numbers only where monsters can spawn")
        public boolean hideSafe = false;

        @Property
        @Comment("Shows what the colors mean in the bottom right corner of the screen while light levels are shown")
        public boolean showLegend = true;
    }

    public static class WeatherOptions {
        @Property
        @Comment("Enable/disable simulated storms, each storm has its own strength, from a drizzle to a downpour")
        public boolean enableStormSimulation = true;

        @Property
        @Slider(min = 1, max = 100)
        @Comment("The weakest a storm can be, as a percent of full strength")
        public int minStormIntensity = 10;

        @Property
        @Slider(min = 1, max = 100)
        @Comment("The strongest a storm can be, as a percent of full strength")
        public int maxStormIntensity = 100;

        @Property
        @Slider(min = 0, max = 100)
        @Comment("How far a storm eases off between its surges, as a percent of its peak")
        public int stormVariation = 40;

        @Property
        @Comment("Draw rain and snow as individual drops instead of vanilla's sheets")
        public boolean enableParticlePrecipitation = true;

        @Property
        @Slider(min = 25, max = 100)
        @Comment("How many drops of particle rain and snow there are, as a percent of the most")
        public int precipitationDensity = 100;

        @Property
        @Slider(min = 8, max = 32)
        @Comment("How far from the player, in blocks, particle rain, snow and dust are drawn")
        public int precipitationRange = 12;

        @Property
        @Slider(min = 50, max = 250)
        @Comment("How big particle rain drops are drawn, as a percent of their usual size")
        public int rainDropScale = 100;

        @Property
        @Slider(min = 0, max = 100)
        @Comment("How blue particle rain is: 0 is a nearly colourless pale grey, 100 a deep blue")
        public int rainTint = 15;

        @Property
        @Slider(min = 50, max = 200)
        @Comment("How big particle snowflakes are drawn, as a percent of their usual size")
        public int snowflakeScale = 100;

        @Property
        @Comment("With particle rain and snow on, storms in deserts and badlands blow dust and thicken the fog")
        public boolean enableDustStorms = true;

        @Property
        @Comment("Rain sounds that follow the storm's strength, from a patter to a roar, and the sound of blowing dust")
        public boolean enableStormSounds = true;

        @Property
        @Comment("Water drips from leaves and overhanging edges for a while after the rain stops")
        public boolean enableAfterRainDrips = true;

        @Property
        @Comment("With particle snow on, snowflakes blown by the wind against a wall or tree trunk stick to it for a moment")
        public boolean enableSnowOnSides = true;

        @Property
        @Comment("With particle rain on, rain drops run down the outside of windows in stick-and-slide streaks")
        public boolean enableWindowStreaks = true;

        @Property
        @Comment("Frost forms on the outside of clear glass windows when it is cold")
        public boolean enableWindowFrost = true;

        @Property
        @Experimental
        @MovedFrom(value = "weatherOptions.enableGroundFog", values = "false=NONE")
        @Comment("Mist lying low over the ground in swamps and eerie biomes: off, or how finely it is drawn. Fast costs least; Fabulous costs several times Fancy")
        public GroundFogQuality groundFogQuality = GroundFogQuality.FANCY;
    }

    public static class FogOptions {
        @Property
        @Comment("Enable/disable fog effects")
        public boolean enableFogEffects = true;

        @Property
        @Comment("Enable/disable morning fog effect")
        public boolean enableMorningFog = true;

        @Property
        @Comment("Enable/disable biome fog effect")
        public boolean enableBiomeFog = true;

        @Property
        @Comment("Enable/disable weather fog effect")
        public boolean enableWeatherFog = true;
    }

    public static class AuroraOptions {
        @Property
        @Comment("Enable/disable auroras: curtains of light in the night sky, on some nights, over cold biomes")
        public boolean enableAuroras = true;

        @Property
        @Slider(min = 0, max = 100)
        @Comment("Percent of nights with an aurora")
        public int chance = 33;

        @Property
        @Slider(min = 1, max = 3)
        @Comment("The most curtains of light an aurora can have")
        public int maxBands = 3;
    }

    public static class MusicManagerOptions {
        @Property
        @RestartRequired
        @Comment("Replace Minecraft's music manager with modded version")
        public boolean replaceMusicManager = true;

        @Property
        @Slider(min = 0, max = 100)
        @Comment("Reduce the wait time between music plays by a percentage")
        public int reduceWaitTime = 0;
    }

    public static class OtherOptions {
        @Property
        @Comment("Enable/disable playing random sound at the Minecraft finish loading to main screen")
        public boolean playRandomSoundOnStartup = true;
    }
}
