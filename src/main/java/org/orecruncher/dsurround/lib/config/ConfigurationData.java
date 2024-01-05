package org.orecruncher.dsurround.lib.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
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

public abstract class ConfigurationData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Reference2ObjectOpenHashMap<Class<?>, Collection<ConfigElement>> specifications = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<Class<?>, ConfigurationData> configs = new Reference2ObjectOpenHashMap<>();


    private transient final String translationRoot;
    private transient final Path configFilePath;

    protected ConfigurationData(String translationRoot, Path configFilePath) {
        this.translationRoot = translationRoot;
        this.configFilePath = configFilePath;
    }

    @SuppressWarnings("unchecked")
    public static <T extends ConfigurationData> @NotNull T getConfig(Class<T> clazz) {
        try {
            var config = configs.get(clazz);
            if (config != null)
                return (T) config;

            // We need to construct a new instance to capture the specification.  Once that is done we can load
            // from disk if present.
            var ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            config = ctor.newInstance();

            var spec = ConfigProcessor.generateAccessors(config);
            specifications.put(clazz, spec);

            // Check to see if it exists on disk, and if so, load it up.  Otherwise, save it so the defaults are
            // persisted and the user can edit manually.
            try {
                if (Files.exists(config.configFilePath)) {
                    try (BufferedReader reader = Files.newBufferedReader(config.configFilePath)) {
                        config = GSON.fromJson(reader, clazz);
                    }
                }
            } catch (Throwable t) {
                Library.getLogger().error(t, "Unable to handle configuration");
            }

            // Post-load processing
            config.postLoad();

            // Save it out.  Config parameters may have been added/removed
            config.save();

            // Now save the config for future queries
            configs.put(clazz, config);

            return (T) config;
        } catch (Throwable t) {
            Library.getLogger().error(t, "Unable to handle configuration");
        }

        return null;
    }

    public Collection<ConfigElement> getSpecification() {
        return specifications.get(this.getClass());
    }

    public String getTranslationRoot() {
        return this.translationRoot;
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
            Library.getLogger().error(t, "Unable to save configuration %s", t.getMessage());
        } finally {
            CONFIG_CHANGED.raise(new ConfigChangedEvent(this));
        }
    }

    /**
     * Hook to provide processing after the configuration is loaded from disk
     */
    public void postLoad() {
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
     * Indicates the default value should be displayed in the tooltip.
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DefaultValue {
    }

    /**
     * Comment associated with a property, if any.  This is used if a translation is not available.  Depending on
     * config file format the comment may be persisted with the data as well.
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
     * The class of the Enum in question.  Thanks type erasure.
     */
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EnumType {
        Class<? extends Enum<?>> value();
    }

    public static final IEvent<ConfigChangedEvent> CONFIG_CHANGED = EventingFactory.createEvent();

    public record ConfigChangedEvent(ConfigurationData config){
    };
}
