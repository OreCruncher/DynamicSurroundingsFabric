package org.orecruncher.dsurround.config.libraries.impl;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.config.SoundMapping;
import org.orecruncher.dsurround.config.data.SoundMappingConfigRule;
import org.orecruncher.dsurround.config.data.SoundMetadataConfig;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.gui.sound.ConfigSoundInstance;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.Comparers;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.resources.DiscoveredResource;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.lib.platform.IMinecraftDirectories;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
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

    private static final String SOUNDS_JSON = "sounds.json";
    private static final String FACTORY_JSON = "sound_factories.json";
    private static final String SOUND_CONFIG_FILE = "soundconfig.json";
    private static final String SOUND_MAPPING_JSON = "sound_mappings.json";
    private static final UnboundedMapCodec<String, SoundMetadataConfig> SOUND_FILE_CODEC = Codec.unboundedMap(Codec.STRING, SoundMetadataConfig.CODEC);
    private static final Codec<List<SoundFactory>> FACTORY_FILE_CODEC = Codec.list(SoundFactory.CODEC);
    private static final Codec<List<IndividualSoundConfigEntry>> SOUND_CONFIG_CODEC = Codec.list(IndividualSoundConfigEntry.CODEC);
    private static final Codec<List<SoundMappingConfigRule>> SOUND_MAPPING_CODEC = Codec.list(SoundMappingConfigRule.CODEC);

    private static final ResourceLocation MISSING_RESOURCE = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "missing_sound");
    private static final SoundEvent MISSING = SoundEvent.createVariableRangeEvent(MISSING_RESOURCE);

    private static final ResourceLocation THUNDER_SOUND = SoundEvents.LIGHTNING_BOLT_THUNDER.getLocation();
    private static final Set<String> SOUND_REMAP_BLOCKED_MOBS = ImmutableSet.of("creeper");
    private static final BlockPos.MutableBlockPos MUTABLE_BLOCK_POS = new BlockPos.MutableBlockPos();

    private final IModLog logger;
    private final Configuration config;
    private final Path soundConfigPath;

    private final Object2ObjectOpenHashMap<ResourceLocation, SoundEvent> myRegistry = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<ResourceLocation, SoundMetadata> soundMetadata = new Object2ObjectOpenHashMap<>();
    private final Map<ResourceLocation, IndividualSoundConfigEntry> individualSoundConfiguration = new Object2ObjectOpenHashMap<>();
    private final Map<ResourceLocation, ISoundFactory> soundFactories = new Object2ObjectOpenHashMap<>();
    private final Set<ResourceLocation> blockedSounds = new ObjectOpenHashSet<>();
    private final Set<ResourceLocation> culledSounds = new ObjectOpenHashSet<>();
    private final List<ResourceLocation> startupSounds = new ArrayList<>();
    private final Map<ResourceLocation, SoundMapping> soundRemappings = new Object2ObjectOpenHashMap<>();
    private List<IndividualSoundConfigEntry> soundConfiguration = new ArrayList<>();

    public SoundLibrary(Configuration config, IModLog logger, IMinecraftDirectories directories) {
        this.logger = ModLog.createChild(logger, "SoundLibrary");
        this.config = config;
        this.myRegistry.defaultReturnValue(SoundLibrary.MISSING);
        this.soundMetadata.defaultReturnValue(new SoundMetadata());
        this.soundConfigPath = directories.getModConfigDirectory().resolve(SOUND_CONFIG_FILE);

        this.loadSoundConfiguration();
    }

    @Override
    public Stream<String> dump() {
        return this.myRegistry.values().stream()
                .sorted((c1, c2) -> Comparers.IDENTIFIER_NATURAL_COMPARABLE.compare(c1.getLocation(), c2.getLocation()))
                .map(Object::toString);
    }

    @Override
    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        if (scope == IReloadEvent.Scope.TAGS)
            return;

        // Forget cached data and reload
        this.myRegistry.clear();
        this.soundMetadata.clear();
        this.soundFactories.clear();
        this.soundRemappings.clear();
        this.loadSoundConfiguration();

        // Initializes the internal sound registry once all the other mods have
        // registered their sounds.
        BuiltInRegistries.SOUND_EVENT.forEach(se -> this.myRegistry.put(se.getLocation(), se));

        // Gather resource pack sound files and process them to ensure metadata is collected.
        // Resource pack sounds generally replace existing registration, but this allows for new
        // sounds to be added client side.
        var soundFiles = resourceUtilities.findResources(SOUND_FILE_CODEC, SOUNDS_JSON);
        soundFiles.forEach(this::registerSoundFile);

        // Gather the sound factory definitions. We scan the local data directory as well.
        var findResults = resourceUtilities.findModResources(FACTORY_FILE_CODEC, FACTORY_JSON);
        findResults.forEach(this::registerSoundFactories);


        var soundMappings = resourceUtilities.findModResources(SOUND_MAPPING_CODEC, SOUND_MAPPING_JSON);
        soundMappings.forEach(this::registerSoundRemappings);

        this.logger.info("Number of SoundEvents cached: %d", this.myRegistry.size());
        this.logger.info("Number of factories cached: %d", this.soundFactories.size());
        this.logger.info("Number of sound remappings cached: %d", this.soundRemappings.size());
    }

    @Override
    public SoundEvent getSound(final String sound) {
        return getSound(ResourceLocation.parse(sound));
    }

    @Override
    public SoundEvent getSound(final ResourceLocation sound) {
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
    public SoundMetadata getSoundMetadata(final ResourceLocation sound) {
        var result = this.soundMetadata.get(Objects.requireNonNull(sound));
        return result.isDefault() ? new SoundMetadata(sound) : result;
    }

    @Override
    public Optional<ISoundFactory> getSoundFactory(ResourceLocation factoryLocation) {
        return Optional.ofNullable(this.soundFactories.get(factoryLocation));
    }

    @Override
    public ISoundFactory getSoundFactoryOrDefault(ResourceLocation factoryLocation) {
        return this.soundFactories.computeIfAbsent(factoryLocation, loc -> SoundFactoryBuilder.create(loc).build());
    }

    @Override
    public ISoundFactory getSoundFactoryForMusic(Music music) {
        return this.soundFactories.computeIfAbsent(music.getEvent().value().getLocation(), loc -> SoundFactoryBuilder.create(music).build());
    }

    @Override
    public boolean isBlocked(final ResourceLocation sound) {
        return this.blockedSounds.contains(Objects.requireNonNull(sound));
    }

    @Override
    public boolean isCulled(final ResourceLocation sound) {
        return this.culledSounds.contains(Objects.requireNonNull(sound));
    }

    @Override
    public float getVolumeScale(SoundSource category, ResourceLocation sound) {
        // Assume scaling of 1F. Basically, it would leave the volume alone.
        var scale = 1F;

        IndividualSoundConfigEntry entry = this.individualSoundConfiguration.get(Objects.requireNonNull(sound));

        // If there is an override from the sound config information, use that.
        if (entry != null && entry.isNotDefault()) {
            scale = entry.volumeScale / 100F;
        }

        // For ambient sounds from the mod, it is further scaled by user configuration.
        if (category == SoundSource.AMBIENT && sound.getNamespace().equals(Library.MOD_ID))
            scale *= this.config.soundOptions.ambientVolumeScaling / 100F;

        // All done!
        return scale;
    }

    @Override
    public Optional<SoundEvent> getRandomStartupSound() {
        if (this.startupSounds.isEmpty())
            return Optional.empty();

        int idx = 0;
        if (this.startupSounds.size() > 1) {
            idx = Randomizer.current().nextInt(this.startupSounds.size());
        }
        return Optional.of(SoundEvent.createVariableRangeEvent(this.startupSounds.get(idx)));
    }

    @Override
    public Collection<IndividualSoundConfigEntry> getIndividualSoundConfigs() {
        return this.soundConfiguration;
    }

    @Override
    public void saveIndividualSoundConfigs(Collection<IndividualSoundConfigEntry> configs) {
        this.blockedSounds.clear();
        this.soundConfiguration = configs.stream()
                .filter(IndividualSoundConfigEntry::isNotDefault)
                .collect(Collectors.toList());
        this.postProcess();
        this.save();
    }

    @Override
    public Optional<SoundInstance> remapSound(SoundInstance soundInstance) {

        // Sounds played from the sound config menu are not remapped
        if (soundInstance instanceof ConfigSoundInstance)
            return Optional.empty();

        var soundLocation = soundInstance.getLocation();

        // If it is a thunder sound, and replacement is not enabled, don't do anything
        if (THUNDER_SOUND.equals(soundLocation)) {
            if (!this.config.soundOptions.replaceThunderSounds)
                return Optional.empty();
        } else if (!this.config.soundOptions.remapSounds) {
            // If it is not a thunder sound and remap is disabled
            return Optional.empty();
        }

        var mappingRule = this.soundRemappings.get(soundLocation);

        if (mappingRule != null) {
            // Get the BlockState of the block below the sound location if needed
            @Nullable
            BlockState blockState = null;
            if (mappingRule.isBlockStateNeeded()) {
                var level = GameUtils.getWorld().orElseThrow();
                var pos = BlockPos.containing(soundInstance.getX(), soundInstance.getY() + 0.25D, soundInstance.getZ()).below();
                blockState = level.getBlockState(pos);

                // If the BlockState is air or not solid, it means we are hanging at a block edge. Scan around looking for
                // something that is solid. It's not perfect, but it's 90% down the center.
                if (blockState.isAir() || !blockState.isSolid()) {
                    for (var dir : Direction.Plane.HORIZONTAL) {
                        blockState = level.getBlockState(MUTABLE_BLOCK_POS.setWithOffset(pos, dir));
                        if (!blockState.isAir() && blockState.isSolid())
                            break;
                    }
                }
            }

            var soundFactory = mappingRule
                    .findMatch(blockState)
                    .map(this::getSoundFactoryOrDefault);

            if (soundFactory.isPresent()) {
                // Need to force a sound to be selected so we can get the volume
                soundInstance.resolve(GameUtils.getSoundManager());
                var volumeScale = soundInstance.getVolume();
                return Optional.of(soundFactory.get().createAtLocation(soundInstance.getX(), soundInstance.getY(), soundInstance.getZ(), volumeScale));
            }
        }

        return Optional.empty();
    }

    /**
     * Examines the sound location information to determine if it is a mob step sound, and then remaps to a block
     * sound similar to what happens with the player.
     */
    @Nullable
    private ResourceLocation remapMobStepSound(SoundInstance soundInstance) {
        var soundLocation = soundInstance.getLocation();
        var path = soundLocation.getPath();
        if (path.startsWith("entity.") && path.endsWith("step")) {
            // Get the mob this sound is for. We do not want to convert mobs like creepers.
            var mobType = path.substring(7, path.indexOf('.', 7));
            if (!SOUND_REMAP_BLOCKED_MOBS.contains(mobType)) {
                var level = GameUtils.getWorld().orElseThrow();
                var pos = BlockPos.containing(soundInstance.getX(), soundInstance.getY(), soundInstance.getZ()).below();
                soundLocation = level.getBlockState(pos).getSoundType().getStepSound().getLocation();
                this.logger.debug("Mob sound remapping from %s to %s", soundInstance.getLocation(), soundLocation);
                return soundLocation;
            }
        }

        return null;
    }

    private void registerSoundFile(DiscoveredResource<Map<String, SoundMetadataConfig>> soundFile) {
        var result = soundFile.resourceContent();
        result.forEach((key, value) -> {
            // We want to register the sound regardless of having metadata.
            final ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(soundFile.namespace(), key);
            if (!this.myRegistry.containsKey(loc)) {
                this.myRegistry.put(loc, SoundEvent.createVariableRangeEvent(loc));
            }
            if (!value.isDefault()) {
                final SoundMetadata data = new SoundMetadata(loc, value);
                this.soundMetadata.put(loc, data);
            }
        });
        this.logger.debug("Registered %d sounds for namespace %s", soundFile.resourceContent().size(), soundFile.namespace());
    }

    private void registerSoundRemappings(DiscoveredResource<List<SoundMappingConfigRule>> mappings) {
        for (var mapping : mappings.resourceContent()) {
            if (!this.soundRemappings.containsKey(mapping.soundEvent())) {
                this.soundRemappings.put(mapping.soundEvent(), SoundMapping.of(mapping));
            } else {
                // Need to merge rules
                var existingMapping = this.soundRemappings.get(mapping.soundEvent());
                existingMapping.merge(mapping);
            }
        }
        this.logger.debug("Registered %d sound remappings for namespace %s", mappings.resourceContent().size(), mappings.namespace());
    }

    private void registerSoundFactories(DiscoveredResource<List<SoundFactory>> factories) {
        factories.resourceContent().forEach(factory -> this.soundFactories.put(factory.getLocation(), factory));
        this.logger.debug("Registered %d sound factories for namespace %s", factories.resourceContent().size(), factories.namespace());
    }

    private void loadSoundConfiguration() {
        this.soundConfiguration.clear();
        this.blockedSounds.clear();

        // Check to see if it exists on the disk, and if so, load it up. Otherwise, save it so the defaults are
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

        // Since we have waterfall sounds
        this.addSoundConfig("minecraft:block.water.ambient", 100, true, false, false);
    }

    private void addSoundConfig(final String id, int volumeScale, boolean block, boolean cull, boolean startup) {
        var entry = new IndividualSoundConfigEntry(
                ResourceLocation.parse(id),
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
            var result = CodecExtensions.serialize(SOUND_CONFIG_CODEC, this.soundConfiguration);
            if (result.isPresent()) {
                Files.writeString(this.soundConfigPath, result.get(), CREATE, WRITE, TRUNCATE_EXISTING);
            }
        } catch (Throwable t) {
            this.logger.error(t, "Unable to save sound configuration %s!", SOUND_CONFIG_FILE);
        }
    }
}
