package org.orecruncher.dsurround.lib.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.eventing.IConfigChangedEvent;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.platform.ModInformation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Base class for a configuration persisted as JSON. Fields marked {@link Property} make up the specification, which
 * drives the config screen and the checks applied when the file is loaded.
 * <p>
 * When loaded, the file is read if it exists, values are checked against the specification (out of range values are
 * clamped and missing ones replaced with their defaults), and the result is written back, so the file always has
 * every current property. A file that can't be read is kept, renamed, rather than overwritten.
 */
public abstract class ConfigurationData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter BACKUP_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Map<Class<? extends ConfigurationData>, Collection<ConfigElement<?>>> SPECIFICATIONS = new IdentityHashMap<>();
    private static final Map<Class<? extends ConfigurationData>, ConfigurationData> CONFIGS = new IdentityHashMap<>();

    // Where the configuration is saved. Set when it is loaded.
    transient Path configFilePath;

    protected ConfigurationData() {
    }

    /**
     * Gets the configuration, loading it from the mod's config folder the first time.
     *
     * @throws IllegalStateException if it can't be created
     */
    @SuppressWarnings("unchecked")
    public static <T extends ConfigurationData> @NotNull T getConfig(Class<T> clazz) {
        synchronized (CONFIGS) {
            var config = CONFIGS.get(clazz);
            if (config == null) {
                config = load(clazz, computePath(clazz));
                CONFIGS.put(clazz, config);
            }
            return (T) config;
        }
    }

    /**
     * Gets the specification for the configuration class, creating it the first time.
     *
     * @throws IllegalStateException if the class can't be processed
     */
    public static <T extends ConfigurationData> @NotNull Collection<ConfigElement<?>> getSpecification(Class<T> clazz) {
        synchronized (SPECIFICATIONS) {
            return SPECIFICATIONS.computeIfAbsent(clazz, ConfigProcessor::generateAccessors);
        }
    }

    private static Path computePath(Class<?> clazz) {
        var placement = clazz.getAnnotation(ConfigPlacement.class);
        if (placement == null)
            throw new IllegalStateException(String.format("Configuration class '%s' must have a @ConfigPlacement annotation", clazz.getName()));
        return ModInformation.getConfigPath(placement.folderName()).resolve(placement.fileName() + ".json");
    }

    /**
     * Loads the configuration from {@code path}, checks it against the specification, and writes it back. Doesn't
     * raise {@link IConfigChangedEvent#EVENT}: nothing has changed from the point of view of the rest of the mod.
     * <p>
     * If the file exists but can't be read, it is renamed (see {@link #backupUnreadableFile}) and the defaults are
     * used, so the user's settings can be recovered by hand.
     *
     * @throws IllegalStateException if the class can't be created or processed
     */
    static <T extends ConfigurationData> T load(Class<T> clazz, Path path) {
        // The specification comes from a freshly constructed instance, so it holds the defaults
        var specification = getSpecification(clazz);

        T config = null;
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                config = GSON.fromJson(reader, clazz);
            } catch (Exception e) {
                Library.LOGGER.error(e, "Unable to read configuration file %s", path);
                backupUnreadableFile(path);
            }
        }

        if (config == null)
            config = ConfigProcessor.createPrototype(clazz);

        config.configFilePath = path;
        ConfigProcessor.repair(specification, config, path.getFileName().toString());
        config.postLoad();

        // Write it back: properties may have been added, removed or corrected
        try {
            config.write();
        } catch (IOException e) {
            Library.LOGGER.error(e, "Unable to save configuration %s", path);
        }
        return config;
    }

    /**
     * Renames a file that couldn't be read, so writing the defaults doesn't destroy it. The name gets a ".bad"
     * suffix and a timestamp, e.g. "dsurround.json.20261002-142233.bad".
     */
    private static void backupUnreadableFile(Path path) {
        var backup = path.resolveSibling(path.getFileName() + "." + LocalDateTime.now().format(BACKUP_SUFFIX) + ".bad");
        try {
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
            Library.LOGGER.warn("Configuration file %s could not be read; it has been renamed to %s and the defaults used", path, backup.getFileName());
        } catch (IOException e) {
            Library.LOGGER.error(e, "Unable to rename unreadable configuration file %s", path);
        }
    }

    public Collection<ConfigElement<?>> getSpecification() {
        return getSpecification(this.getClass());
    }

    /**
     * Saves the configuration to disk and raises {@link IConfigChangedEvent#EVENT}. The event is raised even if
     * writing fails: the values in memory have changed either way.
     */
    public void save() {
        try {
            this.write();
        } catch (IOException e) {
            Library.LOGGER.error(e, "Unable to save configuration %s", this.configFilePath);
        }
        IConfigChangedEvent.EVENT.invoker().onChange(this);
    }

    /**
     * Writes the configuration to its file. Written to a temporary file which then replaces the real one, so a
     * crash part way through leaves the previous file intact.
     */
    void write() throws IOException {
        if (this.configFilePath == null)
            throw new IOException("Configuration " + this.getClass().getName() + " was not loaded from a file");

        var parent = this.configFilePath.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        var temp = Files.createTempFile(parent, this.configFilePath.getFileName().toString(), ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temp)) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(temp, this.configFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, this.configFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Hook to provide processing after the configuration is loaded from the disk and checked against the
     * specification.
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
     * Indicates the field is a property. Supported types are boolean, int, double, String and enums (primitive or
     * boxed), and nested classes of properties, which become property groups.
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
     * Changing the value of this property will require a restart for it to have an effect: of Minecraft if
     * {@code client} is true, otherwise leaving and rejoining the world.
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
     * Comment associated with a property, if any. This is used if a translation is not available. Depending on
     * config file format, the comment may be persisted with the data as well.
     */
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Comment {
        String value();
    }

    /**
     * Style elements to apply when rendering in the configuration display
     */
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TextStyle {
        String color() default "";
        boolean italic() default false;
        boolean bold() default false;
        boolean underlined() default false;
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
     * The class of the enum. Optional: the field's type is used when it is absent.
     */
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EnumType {
        Class<? extends Enum<?>> value();
    }
}
