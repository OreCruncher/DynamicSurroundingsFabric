package org.orecruncher.dsurround.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.config.ConfigurationData;

@Environment(EnvType.CLIENT)
public class Configuration extends ConfigurationData {

    public Configuration() {
        super("dsurround.config", Client.CONFIG_PATH.resolve(Client.ModId + ".json"));
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
    @Comment("Configuration options for tweaking particle behavior")
    public final ParticleTweaks particleTweaks = new ParticleTweaks();

    public static Configuration getConfig() {
        try {
            return ConfigurationData.getConfig(Configuration.class);
        } catch(Throwable t) {
            Client.LOGGER.error(t, "Unable to get config");
        }
        return null;
    }

    @Override
    public void postLoad() {
        Client.LOGGER.setDebug(this.logging.enableDebugLogging);
        Client.LOGGER.setTraceMask(this.logging.traceMask);
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
        @RestartRequired
        @Comment("Enable/disable HRTF sound processing if OpenAL feature is available")
        public boolean enableHRTF = true;

        @Property
        @Comment("Enable/disable on the fly conversion of stereo sounds to mono as needed")
        public boolean enableMonoConversion = true;

        @Property
        @Comment("Enable/disable sound occlusion processing (sound muffling behind blocks)")
        public boolean enableOcclusionProcessing = false;
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
        @RestartRequired
        @Comment("Enable/disable steam column effect when liquids are adjacent to hot sources, like lava and magma")
        public boolean steamColumnEnabled = true;

        @Property
        @RestartRequired
        @Comment("Enable/disable flame jets produced over lava, etc.")
        public boolean flameJetEnabled = true;

        @Property
        @RestartRequired
        @Comment("Enable/disable bubble columns generated underwater")
        public boolean bubbleColumnEnabled = true;
    }

    public static class EntityEffects {

        @Property
        @IntegerRange(min = 16, max = 64)
        @Slider
        @RestartRequired
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

    }

    public static class ParticleTweaks {

        @Property
        @RestartRequired
        @Comment("Enable/disable suppressing player potion particles in first person")
        public boolean suppressPlayerParticles = false;

        @Property
        @Comment("Enable/disable showing of projectile particle trails")
        public boolean showProjectileTrails = false;

    }
}
