package org.orecruncher.dsurround.config.libraries.impl;

import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.util.JsonHelper;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.config.data.SoundMetadataConfig;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.Comparers;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.lib.util.IMinecraftDirectories;
import org.orecruncher.dsurround.sound.SoundMetadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

/**
 * Scans a sounds.json file looking for sounds to register.
 */
public final class SoundLibrary implements ISoundLibrary {

    private static final String FILE_NAME = "sounds.json";
    private static final String SOUND_CONFIG_FILE = "soundconfig.json";
    private static final UnboundedMapCodec<String, SoundMetadataConfig> CODEC = Codec.unboundedMap(Codec.STRING, SoundMetadataConfig.CODEC);
    private static final Codec<List<IndividualSoundConfigEntry>> SOUND_CONFIG_CODEC = Codec.list(IndividualSoundConfigEntry.CODEC);

    private static final Identifier MISSING_RESOURCE = new Identifier(Constants.MOD_ID, "missing_sound");
    private static final SoundEvent MISSING = SoundEvent.of(MISSING_RESOURCE);

    private final IModLog logger;
    private final Path soundConfigPath;

    private final Object2ObjectOpenHashMap<Identifier, SoundEvent> myRegistry = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<Identifier, SoundMetadata> soundMetadata = new Object2ObjectOpenHashMap<>();
    private final Map<Identifier, IndividualSoundConfigEntry> individualSoundConfiguration = new Object2ObjectOpenHashMap<>();
    private final Set<Identifier> blockedSounds = new ObjectOpenHashSet<>();
    private final Set<Identifier> culledSounds = new ObjectOpenHashSet<>();
    private final List<Identifier> startupSounds = new ArrayList<>();
    private List<IndividualSoundConfigEntry> soundConfiguration = new ArrayList<>();

    public SoundLibrary(IModLog logger, IMinecraftDirectories directories) {
        this.logger = logger;
        this.myRegistry.defaultReturnValue(SoundLibrary.MISSING);
        this.soundMetadata.defaultReturnValue(new SoundMetadata());
        this.soundConfigPath = directories.getModConfigDirectory().resolve(SOUND_CONFIG_FILE);

        this.loadSoundConfiguration();
    }

    @Override
    public Stream<String> dump() {
        return this.myRegistry.values().stream()
                .sorted((c1, c2) -> Comparers.IDENTIFIER_NATURAL_COMPARABLE.compare(c1.getId(), c2.getId()))
                .map(e -> e.getId().toString());
    }

    @Override
    public void reload(AssetLibraryEvent.ReloadEvent event) {

        // Forget cached data and reload
        this.myRegistry.clear();
        this.soundMetadata.clear();
        this.loadSoundConfiguration();

        // Initializes the internal sound registry once all the other mods have
        // registered their sounds.
        Registries.SOUND_EVENT.forEach(se -> this.myRegistry.put(se.getId(), se));

        // Gather resource pack sound files and process them to ensure metadata is collected.
        // Resource pack sounds generally replace existing registration, but this allows for new
        // sounds to be added client side.
        ResourceUtils.findSounds(FILE_NAME).forEach(this::registerSoundFile);

        this.logger.info("Number of SoundEvents cached: %d", this.myRegistry.size());
    }

    @Override
    public SoundEvent getSound(final String sound) {
        return getSound(new Identifier(sound));
    }

    @Override
    public SoundEvent getSound(final Identifier sound) {
        Objects.requireNonNull(sound);
        final SoundEvent se = this.myRegistry.get(sound);
        if (se == SoundLibrary.MISSING) {
            this.logger.warn("Unable to locate sound '%s'", sound.toString());
        }
        return se;
    }

    @Override
    public Collection<SoundEvent> getRegisteredSoundEvents() {
        return this.myRegistry.values();
    }

    @Override
    public SoundMetadata getSoundMetadata(final Identifier sound) {
        return this.soundMetadata.get(Objects.requireNonNull(sound));
    }

    @Override
    public boolean isBlocked(final Identifier sound) {
        return this.blockedSounds.contains(Objects.requireNonNull(sound));
    }

    @Override
    public boolean isCulled(final Identifier sound) {
        return this.culledSounds.contains(Objects.requireNonNull(sound));
    }

    @Override
    public float getVolumeScale(final Identifier sound) {
        IndividualSoundConfigEntry entry = this.individualSoundConfiguration.get(Objects.requireNonNull(sound));
        if (entry != null && entry.isNotDefault()) {
            return entry.volumeScale / 100f;
        }

        return 0f;
    }

    @Override
    public Optional<SoundEvent> getRandomStartupSound() {
        if (this.startupSounds.isEmpty())
            return Optional.empty();

        int idx = 0;
        if (this.startupSounds.size() > 1) {
            idx = XorShiftRandom.current().nextInt(this.startupSounds.size());
        }
        return Optional.of(SoundEvent.of(this.startupSounds.get(idx)));
    }

    @Override
    public Collection<IndividualSoundConfigEntry> getIndividualSoundConfigs() {
        return this.soundConfiguration;
    }

    @Override
    public void saveIndividualSoundConfigs(Collection<IndividualSoundConfigEntry> configs) {
        this.soundConfiguration = configs.stream()
                .filter(IndividualSoundConfigEntry::isNotDefault)
                .collect(Collectors.toList());
        this.postProcess();
        this.save();
    }

    private void registerSoundFile(final IResourceAccessor soundFile) {
        final Map<String, SoundMetadataConfig> result = soundFile.as(CODEC);
        if (result != null && !result.isEmpty()) {
            Identifier resource = soundFile.location();
            this.logger.info("Processing %s", resource);
            result.forEach((key, value) -> {
                // We want to register the sound regardless of having metadata.
                final Identifier loc = new Identifier(resource.getNamespace(), key);
                if (!this.myRegistry.containsKey(loc)) {
                    this.myRegistry.put(loc, SoundEvent.of(loc));
                }
                if (!value.isDefault()) {
                    final SoundMetadata data = new SoundMetadata(value);
                    this.soundMetadata.put(loc, data);
                }
            });
        } else {
            this.logger.debug("Skipping %s - unable to parse sound file or there are no sounds declared", soundFile.location());
        }
    }

    private void loadSoundConfiguration() {
        this.soundConfiguration.clear();

        // Check to see if it exists on disk, and if so, load it up.  Otherwise, save it so the defaults are
        // persisted and the user can edit manually.
        try {
            if (Files.exists(this.soundConfigPath)) {
                var content = Files.readString(this.soundConfigPath);
                var result = CodecExtensions.deserialize(content, SOUND_CONFIG_CODEC);
                result.ifPresentOrElse(
                        cfgList -> this.soundConfiguration.addAll(cfgList),
                        () -> this.logger.warn("Unable to obtain content of %s!", SOUND_CONFIG_FILE)
                );
            } else {
                this.addSoundConfigDefaults();
            }
        } catch (Throwable t) {
            this.logger.error(t, "Unable to load sound configuration %s! Resetting to defaults.", SOUND_CONFIG_FILE);
            this.addSoundConfigDefaults();
        }

        this.postProcess();

        // Save it out.  Config parameters may have been added/removed
        this.save();
    }

    private void addSoundConfigDefaults() {
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
        var entry = new IndividualSoundConfigEntry(
                new Identifier(id),
                volumeScale,
                block,
                cull,
                startup);
        this.soundConfiguration.add(entry);
    }

    /**
     * This routine assumes that soundConfiguration has been initialized with data.
     */
    private void postProcess() {
        // Purge the list of anything that is a default
        this.soundConfiguration.removeIf(e -> !e.isNotDefault());
        // Sort the list naturally based on identity
        this.soundConfiguration.sort((e1, e2) -> Comparers.IDENTIFIER_NATURAL_COMPARABLE.compare(e1.soundEventId, e2.soundEventId));
        // Insert the entries into our lookup collections
        this.soundConfiguration.forEach(e -> {
            this.individualSoundConfiguration.put(e.soundEventId, e);
            if (e.startup)
                this.startupSounds.add(e.soundEventId);
            if (e.block || e.volumeScale == 0)
                this.blockedSounds.add(e.soundEventId);
            if (e.cull)
                this.culledSounds.add(e.soundEventId);
        });
    }

    private void save() {
        try {
            var result = SOUND_CONFIG_CODEC.encode(this.soundConfiguration, JsonOps.INSTANCE, JsonOps.INSTANCE.empty()).result();
            if (result.isPresent()) {
                var content = JsonHelper.asArray(result.get(), "soundconfig");
                var gson = new GsonBuilder()
                        .setPrettyPrinting()
                        .create();
                var output = gson.toJson(content);
                Files.writeString(this.soundConfigPath, output, CREATE, WRITE, TRUNCATE_EXISTING);
            }
        } catch (Throwable t) {
            this.logger.error(t, "Unable to save sound configuration %s!", SOUND_CONFIG_FILE);
        }
    }
}
