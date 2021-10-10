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
    @Comment("Configuration options for thunder storms")
    public final ThunderStorms thunderStorms = new ThunderStorms();

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

    }

    public static class SoundSystem {

        @ConfigEntry.BoundedDiscrete(min = 8, max = 16)
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.RequiresRestart
        @Comment("The number of sound channels to reserve for streaming sounds (music, biome sounds, records, etc.)")
        public int streamingChannels = 10;

        @ConfigEntry.BoundedDiscrete(min = 0, max = 20 * 10)
        @ConfigEntry.Gui.Tooltip
        @Comment("Ticks between culled sound events (0 to disable culling)")
        public int cullInterval = 20;
    }

    public static class ThunderStorms {
        @ConfigEntry.Gui.Tooltip
        @Comment("Enables replacement of thunder sounds with Dynamic Surroundings' version")
        public boolean replaceThunderSounds = true;
    }
}
