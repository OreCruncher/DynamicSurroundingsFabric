package org.orecruncher.dsurround.config.libraries.impl;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.data.SoundMetadataConfig;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.sound.SoundMetadata;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Scans a sounds.json file looking for sounds to register.
 */
@Environment(EnvType.CLIENT)
public final class SoundLibrary implements ISoundLibrary {

    private static final String FILE_NAME = "sounds.json";
    private static final UnboundedMapCodec<String, SoundMetadataConfig> CODEC = Codec.unboundedMap(Codec.STRING, SoundMetadataConfig.CODEC);
    private static final Identifier MISSING_RESOURCE = new Identifier(Client.ModId, "missing_sound");
    private static final SoundEvent MISSING = SoundEvent.of(MISSING_RESOURCE);

    private final IModLog logger;
    private final Object2ObjectOpenHashMap<Identifier, SoundEvent> myRegistry = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<Identifier, SoundMetadata> soundMetadata = new Object2ObjectOpenHashMap<>();

    public SoundLibrary(IModLog logger) {
        this.logger = logger;
        this.myRegistry.defaultReturnValue(SoundLibrary.MISSING);
        this.soundMetadata.defaultReturnValue(new SoundMetadata());
    }

    @Override
    public Stream<String> dump() {
        return Stream.of();
    }

    @Override
    public void reload(AssetLibraryEvent.ReloadEvent event) {

        // Clear our registries in case this is a re-load
        this.myRegistry.clear();
        this.soundMetadata.clear();

        // Initializes the internal sound registry once all the other mods have
        // registered their sounds.
        Registries.SOUND_EVENT.forEach(se -> myRegistry.put(se.getId(), se));

        // Gather resource pack sound files and process them to ensure metadata is collected.
        // Resource pack sounds generally replace existing registration, but this allows for new
        // sounds to be added client side.
        final Collection<IResourceAccessor> soundFiles = ResourceUtils.findSounds(FILE_NAME);

        for (final IResourceAccessor file : soundFiles) {
            registerSoundFile(file);
        }

        this.logger.info("Number of SoundEvents cached: %d", myRegistry.size());
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
    public Identifier resolveIdentifier(final String defaultDomain, final String name) {
        Preconditions.checkNotNull(defaultDomain);
        Preconditions.checkNotNull(name);

        Identifier res;
        if (name.charAt(0) == '@') {
            // Sound is in the Minecraft namespace
            res = new Identifier("minecraft", name.substring(1));
        } else if (!name.contains(":")) {
            // It's just a path so assume the specified namespace
            res = new Identifier(defaultDomain, name);
        } else {
            // It's a fully qualified location
            res = new Identifier(name);
        }
        return res;
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
}