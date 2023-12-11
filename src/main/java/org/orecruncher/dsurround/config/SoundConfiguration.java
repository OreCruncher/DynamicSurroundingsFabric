package org.orecruncher.dsurround.config;

import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;

@Environment(EnvType.CLIENT)
public class SoundConfiguration {

    public static final Codec<List<IndividualSoundConfigEntry>> CODEC = Codec.list(IndividualSoundConfigEntry.CODEC);
    private static final Path SOUND_CONFIG_PATH = Client.CONFIG_PATH.resolve("soundconfig.json");

    // Don't serialize
    protected final transient Map<Identifier, IndividualSoundConfigEntry> individualSoundConfiguration = new HashMap<>();
    protected final transient List<Identifier> startupSounds = new ArrayList<>();
    protected List<IndividualSoundConfigEntry> soundConfiguration = new ArrayList<>();

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

    protected SoundConfiguration(List<IndividualSoundConfigEntry> list) {
        this.soundConfiguration = list;
    }

    public static SoundConfiguration getConfig() {
        SoundConfiguration config = null;

        // Check to see if it exists on disk, and if so, load it up.  Otherwise, save it so the defaults are
        // persisted and the user can edit manually.
        try {
            if (Files.exists(SOUND_CONFIG_PATH)) {
                var content = Files.readString(SOUND_CONFIG_PATH);
                var result = CodecExtensions.deserialize(content, CODEC);
                if (result.isPresent()) {
                    config = new SoundConfiguration(result.get());
                } else {
                    Client.LOGGER.warn("Unable to obtain content of soundconfig!");
                }
            }
        } catch (Throwable t) {
            Client.LOGGER.error(t, "Unable to handle configuration");
        }

        if (config == null)
            config = new SoundConfiguration();

        // Post load processing
        config.validatePostLoad();

        // Save it out.  Config parameters may have been added/removed
        config.save();

        return config;
    }

    private void addSoundConfig(final String id, int volumeScale, boolean block, boolean cull, boolean startup) {
        var entry = new IndividualSoundConfigEntry(
                new Identifier(id),
                volumeScale,
                block,
                cull,
                startup);
        this.soundConfiguration.add(entry);
    }

    public boolean isBlocked(final Identifier id) {
        IndividualSoundConfigEntry entry = this.individualSoundConfiguration.get(id);
        return entry != null && entry.block;
    }

    public boolean isCulled(final Identifier id) {
        IndividualSoundConfigEntry entry = this.individualSoundConfiguration.get(id);
        return entry != null && entry.cull;
    }

    public Optional<SoundEvent> getRandomStartupSound() {
        if (this.startupSounds.size() == 0)
            return Optional.empty();

        int idx = 0;
        if (this.startupSounds.size() > 1) {
            idx = XorShiftRandom.current().nextInt(this.startupSounds.size());
        }
        return Optional.of(SoundEvent.of(this.startupSounds.get(idx)));
    }

    public Collection<IndividualSoundConfigEntry> getIndividualSoundConfigs() {
        return this.soundConfiguration;
    }

    public void saveIndividualSoundConfigs(Collection<IndividualSoundConfigEntry> configs) {
        this.soundConfiguration = configs.stream()
                .filter(IndividualSoundConfigEntry::isNotDefault)
                .collect(Collectors.toList());
        this.save();
        this.validatePostLoad();
    }

    private void validatePostLoad() {
        this.individualSoundConfiguration.clear();

        this.soundConfiguration.forEach(entry -> {
            if (entry.isNotDefault()) {
                this.individualSoundConfiguration.put(entry.soundEventId, entry);
                if (entry.startup) {
                    this.startupSounds.add(entry.soundEventId);
                }
            }
        });
    }

    private void save() {
        try {
            var result = CODEC.encode(this.soundConfiguration, JsonOps.INSTANCE, JsonOps.INSTANCE.empty()).result();
            if (result.isPresent()) {
                var content = JsonHelper.asArray(result.get(), "soundconfig");
                var gson = new GsonBuilder()
                        .setPrettyPrinting()
                        .create();
                var output = gson.toJson(content);
                Files.writeString(SOUND_CONFIG_PATH, output, CREATE, WRITE, TRUNCATE_EXISTING);
            }
        } catch (Throwable t) {
            Client.LOGGER.error(t, "Unable to save sound configuration!");
        }
    }
}
