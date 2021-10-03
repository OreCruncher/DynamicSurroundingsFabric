package org.orecruncher.dsurround.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.Client;

@me.shedaniel.autoconfig.annotation.Config(name = Configuration.ConfigPath)
@Environment(EnvType.CLIENT)
public class Configuration implements ConfigData {

    public static final String ConfigPath = Client.ModId + "/" + Client.ModId;

    static {
        AutoConfig.register(Configuration.class, GsonConfigSerializer::new);
    }

    public static Configuration getConfig() {
        return AutoConfig.getConfigHolder(Configuration.class).getConfig();
    }

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    @Comment("Configuration options for modifying Minecraft's Sound System behavior")
    public final SoundSystem soundSystem = new SoundSystem();

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
}
