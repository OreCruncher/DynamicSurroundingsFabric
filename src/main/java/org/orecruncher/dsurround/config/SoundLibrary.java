package org.orecruncher.dsurround.config;

import com.google.common.base.Preconditions;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.data.SoundMetadataConfig;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.lib.validation.MapValidator;
import org.orecruncher.dsurround.lib.validation.Validators;
import org.orecruncher.dsurround.sound.SoundMetadata;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Scans a sounds.json file looking for sounds to register.
 */
@Environment(EnvType.CLIENT)
public final class SoundLibrary {

    private static final IModLog LOGGER = Client.LOGGER.createChild(SoundLibrary.class);
    private static final Identifier MISSING_RESOURCE = new Identifier(Client.ModId, "missing_sound");
    private static final Object2ObjectOpenHashMap<Identifier, SoundEvent> myRegistry = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<Identifier, SoundMetadata> soundMetadata = new Object2ObjectOpenHashMap<>();
    private static final Type SOUND_FILE_TYPE = TypeToken.getParameterized(Map.class, String.class, SoundMetadataConfig.class).getType();

    public static final SoundEvent MISSING = new SoundEvent(MISSING_RESOURCE);

    static {
        myRegistry.defaultReturnValue(SoundLibrary.MISSING);
        soundMetadata.defaultReturnValue(new SoundMetadata());

        Validators.registerValidator(SOUND_FILE_TYPE, new MapValidator<String, SoundMetadataConfig>());
    }

    public static void load() {

        // Clear our registries in case this is a re-load
        myRegistry.clear();
        soundMetadata.clear();

        // Initializes the internal sound registry once all the other mods have
        // registered their sounds.
        Registry.SOUND_EVENT.forEach(se -> myRegistry.put(se.getId(), se));

        // Gather resource pack sound files and process them to ensure metadata is collected.
        // Resource pack sounds generally replace existing registration, but this allows for new
        // sounds to be added client side.
        final Collection<IResourceAccessor> soundFiles = ResourceUtils.findSounds();

        for (final IResourceAccessor file : soundFiles) {
            registerSoundFile(file);
        }

        LOGGER.info("Number of SoundEvents cached: %d", myRegistry.size());
    }

    private static void registerSoundFile(final IResourceAccessor soundFile) {
        final Map<String, SoundMetadataConfig> result = soundFile.as(SOUND_FILE_TYPE);
        if (result != null && result.size() > 0) {
            Identifier resource = soundFile.location();
            LOGGER.info("Processing %s", resource);
            result.forEach((key, value) -> {
                // We want to register the sound regardless of having metadata.
                final Identifier loc = new Identifier(resource.getNamespace(), key);
                if (!myRegistry.containsKey(loc)) {
                    myRegistry.put(loc, new SoundEvent(loc));
                }
                if (!value.isDefault()) {
                    final SoundMetadata data = new SoundMetadata(value);
                    soundMetadata.put(loc, data);
                }
            });
        } else {
            LOGGER.debug("Skipping %s - unable to parse sound file or there are no sounds declared", soundFile.location());
        }
    }

    public static SoundEvent getSound(final Identifier sound) {
        Objects.requireNonNull(sound);
        final SoundEvent se = myRegistry.get(sound);
        if (se == MISSING) {
            LOGGER.warn("Unable to locate sound '%s'", sound.toString());
        }
        return se;
    }

    public static Collection<SoundEvent> getRegisteredSoundEvents() {
        return myRegistry.values();
    }

    @SuppressWarnings("unused")
    public static SoundMetadata getSoundMetadata(final Identifier sound) {
        return soundMetadata.get(Objects.requireNonNull(sound));
    }

    public static Identifier resolveIdentifier(final String defaultDomain, final String name) {
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
}