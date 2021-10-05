package org.orecruncher.dsurround.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

import java.util.*;

@me.shedaniel.autoconfig.annotation.Config(name = SoundConfiguration.ConfigPath)
@Environment(EnvType.CLIENT)
public class SoundConfiguration implements ConfigData {

    public static final String ConfigPath = Client.ModId + "/soundconfig";

    private static final float MIN_SOUNDFACTOR = 0F;
    private static final float MAX_SOUNDFACTOR = 4F;
    private static final float DEFAULT_SOUNDFACTOR = 1F;

    static {
        AutoConfig.register(SoundConfiguration.class, GsonConfigSerializer::new);
    }

    public static SoundConfiguration getConfig() {
        return AutoConfig.getConfigHolder(SoundConfiguration.class).getConfig();
    }

    protected List<IndividualSoundConfigEntry> soundConfiguration = new ArrayList<>();

    // Don't serialize
    protected final transient Map<String, IndividualSoundConfigEntry> individualSoundConfiguration = new HashMap<>();
    protected final transient List<String> startupSounds = new ArrayList<>();

    protected SoundConfiguration() {
        this.addSoundConfig("minecraft:entity.sheep.ambient", 100, false, true, false);
        this.addSoundConfig("minecraft:entity.chicken.ambient", 100, false, true, false);
        this.addSoundConfig("minecraft:entity.cow.ambient", 100, false, true, false);
        this.addSoundConfig("minecraft:entity.pig.ambient", 100, false, true, false);
        this.addSoundConfig("minecraft:entity.llama.ambient", 100, false, true, false);
        this.addSoundConfig("minecraft:entity.wither.spawn", 10, false, true, false);
        this.addSoundConfig("minecraft:entity.wither.death", 10, false, true, false);
        this.addSoundConfig("minecraft:entity.ender_dragon.death", 10, false, true, false);

        this.addSoundConfig("minecraft:entity.experience_orb.pickup", 100, false, false, true);
        this.addSoundConfig("minecraft:entity.chicken.egg", 100, false, false, true);
        this.addSoundConfig("minecraft:ambient.underwater.exit", 100, false, false, true);
    }

    private void addSoundConfig(final String id, int volumeScale, boolean block, boolean cull, boolean startup) {
        IndividualSoundConfigEntry entry = new IndividualSoundConfigEntry(id);
        entry.volumeScale = volumeScale;
        entry.block = block;
        entry.cull = cull;
        entry.startup = startup;
        this.soundConfiguration.add(entry);
    }

    public float getVolumeScale(final Identifier id) {
        IndividualSoundConfigEntry entry = this.individualSoundConfiguration.get(id.toString());
        return entry != null ? MathHelper.clamp(entry.volumeScale / 100F, MIN_SOUNDFACTOR, MAX_SOUNDFACTOR) : DEFAULT_SOUNDFACTOR;
    }

    public boolean isBlocked(final Identifier id) {
        IndividualSoundConfigEntry entry = this.individualSoundConfiguration.get(id.toString());
        return entry != null && entry.block;
    }

    public boolean isCulled(final Identifier id) {
        IndividualSoundConfigEntry entry = this.individualSoundConfiguration.get(id.toString());
        return entry != null && entry.cull;
    }

    public Optional<SoundEvent> getRandomStartupSound() {
        if (this.startupSounds.size() == 0)
            return Optional.empty();

        int idx = 0;
        if (this.startupSounds.size() > 1) {
            idx = XorShiftRandom.current().nextInt(this.startupSounds.size());
        }
        return Optional.of(new SoundEvent(new Identifier(this.startupSounds.get(idx))));
    }

    public Collection<IndividualSoundConfigEntry> getIndividualSoundConfigs() {
        return this.soundConfiguration;
    }

    public void saveIndividualSoundConfigs(Collection<IndividualSoundConfigEntry> configs) {
        this.soundConfiguration = new ArrayList<>(configs);
        ConfigHolder<SoundConfiguration> holder = AutoConfig.getConfigHolder(SoundConfiguration.class);
        holder.save();
        this.validatePostLoad();
    }

    public void validatePostLoad() {
        this.individualSoundConfiguration.clear();

        this.soundConfiguration.forEach(entry -> {
            if (!entry.isDefault()) {
                this.individualSoundConfiguration.put(entry.id, entry);
                if (entry.startup) {
                    this.startupSounds.add(entry.id);
                }
            }
        });
    }
}
