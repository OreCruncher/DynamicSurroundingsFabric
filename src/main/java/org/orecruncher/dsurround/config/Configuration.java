package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.util.IMinecraftDirectories;

public class Configuration extends ConfigurationData {

    private static final IModLog LOGGER = ContainerManager.resolve(IModLog.class);

    public Configuration() {
        super("dsurround.config", ContainerManager.resolve(IMinecraftDirectories.class).getModConfigDirectory().resolve(Client.ModId + ".json"));
    }

    @Property
    @Comment("Configuration options for modifying logging behavior")
    public final Logging logging = new Logging();

    @Property
    @Comment("Configuration options for modifying Minecraft's Sound System behavior")
    public final SoundSystem soundSystem = new SoundSystem();

    @Property
    @Comment("Configuration options for enhanced sound processing")
    public final EnhancedSounds enhancedSounds = new EnhancedSounds();

    @Property
    @Comment("Configuration options for thunder storms")
    public final ThunderStorms thunderStorms = new ThunderStorms();

    @Property
    @Comment("Configuration options for block effects")
    public final BlockEffects blockEffects = new BlockEffects();

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
    @Comment("Configuration options for other things")
    public final OtherOptions otherOptions = new OtherOptions();

    public static Configuration getConfig() {
        try {
            return ConfigurationData.getConfig(Configuration.class);
        } catch(Throwable t) {
            LOGGER.error(t, "Unable to get config");
        }
        return null;
    }

    public static class Flags {
        public static final int AUDIO_PLAYER = 0x1;
        public static final int BASIC_SOUND_PLAY = 0x2;
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
    }

    public static class SoundSystem {
        @Property
        @IntegerRange(min = 8, max = 16)
        @Slider
        @RestartRequired
        @Comment("The number of sound channels to reserve for streaming sounds (music, biome sounds, records, etc.)")
        public int streamingChannels = 12;

        @Property
        @IntegerRange(min = 0, max = 20 * 10)
        @Slider
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
        @IntegerRange(min = 0, max = 8)
        @Slider
        @RestartRequired
        @DefaultValue
        @Comment("Number of background threads to use for enhanced sound processing (0 means use internal default)")
        public int backgroundThreadWorkers = 0;

        @Property
        @Comment("Enable/disable on the fly conversion of stereo sounds to mono as needed")
        public boolean enableMonoConversion = true;

        @Property
        @Comment("Enable/disable sound occlusion processing (sound muffling behind blocks)")
        public boolean enableOcclusionProcessing = false;

        @Property
        @IntegerRange(min = 16, max = 64)
        @RestartRequired
        @DefaultValue
        @Comment("The number of rays to project around a sound location to calculate reverb effect")
        public int reverbRays = 32;

        @Property
        @IntegerRange(min = 2, max = 8)
        @RestartRequired
        @DefaultValue
        @Comment("The number of reflections the ray calculation will perform before ending a ray calculation")
        public int reverbBounces = 4;

        @Property
        @IntegerRange(min = 64, max = 512)
        @RestartRequired
        @DefaultValue
        @Comment("Total distance a reverb ray will traverse before ending calculation")
        public int reverbRayTraceDistance = 256;
    }

    public static class ThunderStorms {
        @Property
        @Comment("Enables replacement of thunder sounds with Dynamic Surroundings' version")
        public boolean replaceThunderSounds = true;
    }

    public static class BlockEffects {

        @Property
        @IntegerRange(min = 16, max = 64)
        @Slider
        @RestartRequired
        @Comment("Distance that will be scanned when generating block effects")
        public int blockEffectRange = 24;

        @Property
        @Comment("Enable/disable steam column effect when liquids are adjacent to hot sources, like lava and magma")
        public boolean steamColumnEnabled = true;

        @Property
        @Comment("Enable/disable flame jets produced over lava, etc.")
        public boolean flameJetEnabled = true;

        @Property
        @Comment("Enable/disable bubble columns generated underwater")
        public boolean bubbleColumnEnabled = true;

        @Property
        @Comment("Enable/disable firefly generation")
        public boolean firefliesEnabled = true;

        @Property
        @RestartRequired
        @Comment("Enable/disable waterfall effect from flowing water")
        public boolean waterfallsEnabled = true;

        @Property
        @Comment("Enable/disable sounds from waterfalls")
        public boolean enableWaterfallSounds = true;

        @Property
        @Comment("Enable/disable particles from waterfalls")
        public boolean enableWaterfallParticles = true;

        @Property
        @EnumType(WaterRippleStyle.class)
        @Comment("The style of water ripple to render when a drop hits a fluid")
        public WaterRippleStyle waterRippleStyle = WaterRippleStyle.PIXELATED_CIRCLE;
    }

    public static class EntityEffects {

        @Property
        @IntegerRange(min = 16, max = 64)
        @Slider
        @Comment("The maximum range at which entity special effects are applied")
        public int entityEffectRange = 24;

        @Property
        @RestartRequired
        @Comment("Enable/disable bow pull sound effect")
        public boolean enableBowPull = true;

        @Property
        @RestartRequired
        @Comment("Enable/disable breath effect in cold biomes and underwater")
        public boolean enableBreathEffect = true;

        @Property
        @RestartRequired
        @Comment("Enable/disable player toolbar sound effects")
        public boolean enablePlayerToolbarEffect = false;

        @Property
        @RestartRequired
        @Comment("Enable/disable item swing sound effects from players and mobs")
        public boolean enableSwingEffect = false;

        @Property
        @RestartRequired
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
        @Comment("Enable/disable suppressing player potion particles in first person")
        public boolean suppressPlayerParticles = false;

        @Property
        @Comment("Enable/disable showing of projectile particle trails")
        public boolean suppressProjectileParticleTrails = false;

    }

    public static class CompassAndClockOptions {
        @Property
        @Comment("Enable/disable display of the clock display when holding a clock item")
        public boolean enableClock = false;

        @Property
        @Comment("Enable/disable display of the compass display when holding a compass item")
        public boolean enableCompass = false;

        @Property
        @Comment("Style of compass rendering")
        @EnumType(CompassStyle.class)
        public CompassStyle compassStyle = CompassStyle.TRANSPARENT_WITH_INDICATOR;

        @Property
        @Comment("Scales the display by the specified amount")
        @DoubleRange(min = 0.5D, max = 4D)
        public double scale = 1D;
    }

    public static class OtherOptions {
        @Property
        @Comment("Enable/disable playing random sound at the Minecraft finish loading to main screen")
        public boolean playRandomSoundOnStartup = true;
    }
}
