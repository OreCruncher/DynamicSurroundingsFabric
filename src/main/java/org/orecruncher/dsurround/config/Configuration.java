package org.orecruncher.dsurround.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.Client;

@me.shedaniel.autoconfig.annotation.Config(name = Client.ModId)
@Environment(EnvType.CLIENT)
public class Configuration implements ConfigData {

    static {
        AutoConfig.register(Configuration.class, MyGsonConfigSerializer::new);
    }

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    @Comment("Configuration options for modifying logging behavior")
    public final Logging logging = new Logging();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    @Comment("Configuration options for modifying Minecraft's Sound System behavior")
    public final SoundSystem soundSystem = new SoundSystem();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    @Comment("Configuration options for enhanced sound processing")
    public final EnhancedSounds enhancedSounds = new EnhancedSounds();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    @Comment("Configuration options for thunder storms")
    public final ThunderStorms thunderStorms = new ThunderStorms();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    @Comment("Configuration options for block effects")
    public final BlockEffects blockEffects = new BlockEffects();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    @Comment("Configuration options for entity effects")
    public final EntityEffects entityEffects = new EntityEffects();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    @Comment("Configuration options for tweaking particle behavior")
    public final ParticleTweaks particleTweaks = new ParticleTweaks();

    public static Configuration getConfig() {
        return AutoConfig.getConfigHolder(Configuration.class).getConfig();
    }

    public void validatePostLoad() {
        Client.LOGGER.setDebug(this.logging.enableDebugLogging);
        Client.LOGGER.setTraceMask(this.logging.traceMask);
    }

    public static class Flags {
        public static final int AUDIO_PLAYER = 0x1;
        public static final int BASIC_SOUND_PLAY = 0x2;
    }

    public static class Logging {

        @ConfigEntry.Gui.Tooltip
        @Comment("Enables/disables debug logging of the mod")
        public boolean enableDebugLogging = false;

        @ConfigEntry.Gui.Tooltip
        @Comment("Bitmask for toggling various debug traces")
        public int traceMask = 0;

        @ConfigEntry.Gui.Tooltip
        @Comment("Enable/disable chat window notification of newer updates available")
        public boolean enableModUpdateChatMessage = true;
    }

    public static class SoundSystem {
        @ConfigEntry.BoundedDiscrete(min = 8, max = 16)
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.RequiresRestart
        @Comment("The number of sound channels to reserve for streaming sounds (music, biome sounds, records, etc.)")
        public int streamingChannels = 12;

        @ConfigEntry.BoundedDiscrete(min = 0, max = 20 * 10)
        @ConfigEntry.Gui.Tooltip
        @Comment("Ticks between culled sound events (0 to disable culling)")
        public int cullInterval = 20;
    }

    public static class EnhancedSounds {
        @ConfigEntry.Gui.Tooltip
        @Comment("Enable/disable enhanced sound processing (reverb, occlusion, etc)")
        public boolean enableEnhancedSounds = true;

        @ConfigEntry.BoundedDiscrete(min = 0, max = 8)
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.RequiresRestart
        @Comment("Number of background threads to use for enhanced sound processing (0 means use internal default)")
        public int backgroundThreadWorkers = 0;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.RequiresRestart
        @Comment("Enable/disable HRTF sound processing if OpenAL feature is available")
        public boolean enableHRTF = true;

        @ConfigEntry.Gui.Tooltip
        @Comment("Enable/disable on the fly conversion of stereo sounds to mono as needed")
        public boolean enableMonoConversion = true;

        @ConfigEntry.Gui.Tooltip
        @Comment("Enable/disable sound occlusion processing (sound muffling behind blocks)")
        public boolean enableOcclusionProcessing = false;
    }

    public static class ThunderStorms {
        @ConfigEntry.Gui.Tooltip
        @Comment("Enables replacement of thunder sounds with Dynamic Surroundings' version")
        public boolean replaceThunderSounds = true;
    }

    public static class BlockEffects {

        @ConfigEntry.BoundedDiscrete(min = 16, max = 64)
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.RequiresRestart
        @Comment("Distance that will be scanned when generating block effects")
        public int blockEffectRange = 24;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.RequiresRestart
        @Comment("Enable/disable steam column effect when liquids hit hot sources")
        public boolean steamColumnEnabled = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.RequiresRestart
        @Comment("Enable/disable flame jets produced over lava, etc.")
        public boolean flameJetEnabled = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.RequiresRestart
        @Comment("Enable/disable bubble columns generated underwater")
        public boolean bubbleColumnEnabled = true;
    }

    public static class EntityEffects {

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.RequiresRestart
        @Comment("Enable/disable bow pull sound effect")
        public boolean enableBowPull = true;

    }

    public static class ParticleTweaks {

        @ConfigEntry.Gui.Tooltip
        @Comment("Enable/disable suppressing player potion particles in first person")
        public boolean suppressPlayerParticles = false;

        @ConfigEntry.Gui.Tooltip
        @Comment("Enable/disable showing of projectile particle trails")
        public boolean showProjectileTrails = false;

    }
}
