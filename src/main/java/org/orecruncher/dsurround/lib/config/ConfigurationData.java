package org.orecruncher.dsurround.lib.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.IEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public abstract class ConfigurationData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Class<? extends ConfigurationData>, Collection<ConfigElement<?>>> SPECIFICATIONS = new IdentityHashMap<>();
    private static final Map<Class<? extends ConfigurationData>, ConfigurationData> CONFIGS = new IdentityHashMap<>();

    transient final Path configFilePath;

    protected ConfigurationData() {
        this.configFilePath = computePath(this.getClass());
    }

    @SuppressWarnings("unchecked")
    public static <T extends ConfigurationData> @NotNull T getConfig(Class<T> clazz) {
        return (T) Objects.requireNonNull(CONFIGS.computeIfAbsent(clazz, ConfigurationData::computeConfiguration));
    }

    @Nullable
    public static <T extends ConfigurationData> Collection<ConfigElement<?>> getSpecification(Class<T> clazz) {
        var spec = SPECIFICATIONS.get(clazz);
        if (spec == null) {
            var specResult = ConfigProcessor.generateAccessors(clazz);
            if (specResult.isPresent()) {
                spec = specResult.get();
                SPECIFICATIONS.put(clazz, spec);
            }
        }
        return spec;
    }

    private static Path computePath(Class<?> clazz) {
        var configFolderAnnotation = clazz.getAnnotation(ConfigPlacement.class);
        if (configFolderAnnotation == null)
            throw new RuntimeException("Configuration class must have a ConfigFolder annotation");
        return Library.PLATFORM
                .getConfigPath(configFolderAnnotation.folderName())
                .resolve(configFolderAnnotation.fileName() + ".json");
    }

    private static <T extends ConfigurationData> T computeConfiguration(Class<T> clazz) {
        try {
            // Construct the path to the configuration folder
            var configFolderPath = computePath(clazz);

            // We need to construct a new instance to capture the specification. Once that is done, we can load
            // from disk if present.
            var ignored = getSpecification(clazz);

            // Check to see if it exists on the disk, and if so, load it up. Otherwise, save it so the defaults are
            // persisted and the user can edit manually.
            T config = ConfigProcessor.createPrototype(clazz).orElseThrow();
            try {
                if (Files.exists(configFolderPath)) {
                    try (BufferedReader reader = Files.newBufferedReader(configFolderPath)) {
                        config = GSON.fromJson(reader, clazz);
                    }
                }
            } catch (Throwable t) {
                Library.LOGGER.error(t, "Unable to handle configuration");
            }

            if (config == null) {
                var ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                config = ctor.newInstance();
            }

            // Post-load processing
            config.postLoad();

            // Save it out.  Config parameters may have been added, removed, clamped, etc.
            config.save();

            return config;
        } catch (Throwable t) {
            Library.LOGGER.error(t, "Unable to handle configuration");
        }

        return null;
    }

    public Collection<ConfigElement<?>> getSpecification() {
        return SPECIFICATIONS.get(this.getClass());
    }

    /**
     * Saves the state of the config to disk
     */
    public void save() {
        try {
            Files.createDirectories(this.configFilePath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(this.configFilePath)) {
                GSON.toJson(this, writer);
            }
        } catch (Throwable t) {
            Library.LOGGER.error(t, "Unable to save configuration %s", t.getMessage());
        } finally {
            CONFIG_CHANGED.raise().onChange(this);
        }
    }

    /**
     * Hook to provide processing after the configuration is loaded from the disk
     */
    public void postLoad() {
    }

    /**
     * Defines the folder within the config directory that option state will be saved. All configuration
     * instances need this annotation.
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ConfigPlacement {
        String folderName();
        String fileName();
    }

    /**
     * Defines the root of language translation keys
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TranslationRoot {
        String value() default Constants.MOD_ID;
    }

    /**
     * Indicates the field is a property
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Property {
        /**
         * The key segment for formulating a lookup key to generate language resource ids.
         */
        String value() default "";
    }

    /**
     * Value range of an Integer
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IntegerRange {
        int min();

        int max() default Integer.MAX_VALUE;
    }

    /**
     * Value range of a Double
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DoubleRange {
        double min();

        double max() default Double.MAX_VALUE;
    }

    /**
     * Changing the value of this property will require a restart for it to have an effect.
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RestartRequired {
        boolean client() default true;
    }

    /**
     * Changing the value of this property will require the assets to be reloaded to have an effect.
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface AssetReloadRequired {
    }

    /**
     * Changing the value of this property will require the world to be reloaded to have an effect.
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface WorldReloadRequired {
    }

    /**
     * Comment associated with a property, if any. This is used if a translation is not available. Depending on
     * config file format, the comment may be persisted with the data as well.
     */
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Comment {
        String value();
    }

    /**
     * Indicates the preference for a slider in GUI when modifying the integer property
     */
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Slider {

    }

    /**
     * Indicates the property will not show in the GUI
     */
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Hidden {

    }

    /**
     * The class of the Enum in question. Thanks type erasure.
     */
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EnumType {
        Class<? extends Enum<?>> value();
    }

    public static final IEvent<IConfigChangedEvent> CONFIG_CHANGED = EventingFactory.createEvent(callbacks -> config -> {
        for (var callback : callbacks) {
            callback.onChange(config);
        }
    });

    @FunctionalInterface
    public interface IConfigChangedEvent {
        void onChange(ConfigurationData config);
    }
}