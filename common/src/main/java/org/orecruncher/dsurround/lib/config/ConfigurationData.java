package org.orecruncher.dsurround.lib.config;

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
 * <p>
 * Each property's {@link Comment} is written above it in the file as a {@code //} comment. Comments in the file are
 * allowed when reading, but they are rewritten from the annotations on every save, so hand-written ones are lost.
 */
public abstract class ConfigurationData {

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
        // False if the file couldn't be read or renamed out of the way; writing would then destroy it
        boolean writeBack = true;
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                config = CommentedJson.read(reader, clazz);
            } catch (Exception e) {
                Library.LOGGER.error(e, "Unable to read configuration file %s", path);
                writeBack = backupUnreadableFile(path);
            }
        }

        if (config == null)
            config = ConfigProcessor.createPrototype(clazz);

        config.configFilePath = path;
        ConfigProcessor.repair(specification, config, path.getFileName().toString());
        config.postLoad();

        // Write it back: properties may have been added, removed or corrected
        if (writeBack) {
            try {
                config.write();
            } catch (IOException e) {
                Library.LOGGER.error(e, "Unable to save configuration %s", path);
            }
        }
        return config;
    }

    /**
     * Renames a file that couldn't be read, so writing the defaults doesn't destroy it. The name gets a ".bad"
     * suffix and a timestamp, e.g. "dsurround.json.20261002-142233.bad".
     *
     * @return true if it was renamed
     */
    private static boolean backupUnreadableFile(Path path) {
        var backup = backupPath(path, LocalDateTime.now());
        try {
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
            Library.LOGGER.warn("Configuration file %s could not be read; it has been renamed to %s and the defaults used", path, backup.getFileName());
            return true;
        } catch (IOException e) {
            Library.LOGGER.error(e, "Unable to rename unreadable configuration file %s; the defaults are used, but not saved over it", path);
            return false;
        }
    }

    static Path backupPath(Path path, LocalDateTime time) {
        return path.resolveSibling(path.getFileName() + "." + time.format(BACKUP_SUFFIX) + ".bad");
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
                writer.write(CommentedJson.write(this));
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
     * Comment associated with a property, if any. This is used if a translation is not available. It is also
     * written above the property in the config file.
     */
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Comment {
        String value();

        /**
         * On a type: whether properties of this type that have no comment of their own use this one in the config
         * file. Ignored on fields.
         */
        boolean inherit() default false;
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
     * Value range of an integer property, edited with a slider in the GUI. A slider needs both limits, so neither
     * has a default. This is the property's range, as {@link IntegerRange} would be, so a field can't have both.
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Slider {
        int min();

        int max();
    }

    /**
     * Value range of a double property, edited with a slider in the GUI that moves in steps of {@code step}. As with
     * {@link Slider}, this is the property's range, so a field can't also have {@link DoubleRange}.
     * <p>
     * The step must divide the range evenly, have at most six decimal places, and the default must be one of the
     * positions. The slider shows as many decimal places as the step has (or the minimum, if it has more). Values
     * in the file don't have to be on a step; the slider shows the nearest one.
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DoubleSlider {
        double min();

        double max();

        double step();
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
