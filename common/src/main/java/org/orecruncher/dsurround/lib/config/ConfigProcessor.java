package org.orecruncher.dsurround.lib.config;

import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Objects;

/**
 * Builds the specification of a configuration class from its {@link ConfigurationData.Property} fields, and checks
 * loaded values against it.
 */
public final class ConfigProcessor {

    private ConfigProcessor() {
    }

    /**
     * Creates an instance through the class's no-argument constructor, which may be private.
     *
     * @throws IllegalStateException if it can't be created
     */
    public static <T> T createPrototype(Class<T> clazz) {
        try {
            var ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(String.format("Constructor of '%s' threw %s", clazz.getName(), e.getCause()), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(String.format("Unable to create '%s'; it needs a no-argument constructor", clazz.getName()), e);
        }
    }

    /**
     * Builds the specification for the class. Default values are taken from a newly created instance.
     *
     * @throws IllegalStateException if the class can't be created or a property is not valid
     */
    public static <T extends ConfigurationData> Collection<ConfigElement<?>> generateAccessors(Class<T> clazz) {
        return new GenerationContext(clazz, ConfigurationData.translationRootOf(clazz)).generateLevel(createPrototype(clazz));
    }

    /**
     * Checks the values in {@code instance} against the specification, logging each correction: a missing (null)
     * value is replaced with its default (for a property group, a new instance with default values), and a value
     * out of range is clamped.
     *
     * @param source names what was loaded, for the log
     * @return the number of values corrected
     */
    public static int repair(Collection<ConfigElement<?>> specification, Object instance, String source) {
        int corrections = 0;
        for (var element : specification) {
            if (element instanceof ConfigElement.PropertyGroup group) {
                var groupInstance = group.getInstance(instance);
                if (groupInstance == null) {
                    groupInstance = createPrototype(group.getType());
                    group.setInstance(instance, groupInstance);
                    Library.LOGGER.warn("%s: '%s' was missing; using defaults", source, group.getLanguageKey());
                    corrections++;
                }
                corrections += repair(group.getChildren(), groupInstance, source);
            } else if (element instanceof ConfigElement.PropertyValue<?> value) {
                if (repairValue(value, instance, source))
                    corrections++;
            }
        }
        return corrections;
    }

    private static <T> boolean repairValue(ConfigElement.PropertyValue<T> property, Object instance, String source) {
        var current = property.getValue(instance);
        if (current == null) {
            property.setValue(instance, property.defaultValue());
            Library.LOGGER.warn("%s: '%s' was missing or not valid; using the default %s", source, property.getLanguageKey(), property.defaultValue());
            return true;
        }
        var clamped = property.clamp(current);
        if (!Objects.equals(current, clamped)) {
            property.setValue(instance, clamped);
            Library.LOGGER.warn("%s: '%s' value %s is out of range or not valid; using %s", source, property.getLanguageKey(), current, clamped);
            return true;
        }
        return false;
    }

    private record GenerationContext(Class<?> clazz, String translationRoot) {

        Collection<ConfigElement<?>> generateLevel(Object prototype) {
            var elements = new ObjectArray<ConfigElement<?>>();

            for (var f : this.clazz.getFields()) {
                // See if it is marked as a property.  If not continue.
                var property = f.getAnnotation(ConfigurationData.Property.class);
                if (property == null || Modifier.isStatic(f.getModifiers()))
                    continue;

                var element = this.process(property, prototype, f);
                if (element != null)
                    elements.add(element);
            }

            return elements;
        }

        private ConfigElement<?> process(ConfigurationData.Property property, Object prototype, Field f) {
            var type = f.getType();
            var key = this.calculateLangKey(property, f);

            if (f.isAnnotationPresent(ConfigurationData.Slider.class) && type != int.class && type != Integer.class)
                throw new IllegalStateException(String.format("Configuration property '%s' in %s has @Slider, which is only supported on int properties; use @DoubleSlider for a double", f.getName(), this.clazz.getName()));
            if (f.isAnnotationPresent(ConfigurationData.DoubleSlider.class) && type != double.class && type != Double.class)
                throw new IllegalStateException(String.format("Configuration property '%s' in %s has @DoubleSlider, which is only supported on double properties", f.getName(), this.clazz.getName()));

            if (type == boolean.class || type == Boolean.class)
                return new ConfigElement.BooleanValue(prototype, key, f);
            if (type == int.class || type == Integer.class)
                return this.processInteger(prototype, key, f);
            if (type == double.class || type == Double.class)
                return this.processDouble(prototype, key, f);
            if (type == String.class)
                return new ConfigElement.StringValue(prototype, key, f);
            if (type.isEnum())
                return new ConfigElement.EnumValue(prototype, key, f);
            if (!type.isPrimitive() && !type.isArray() && !type.getName().startsWith("java."))
                return this.processGroup(prototype, key, f);

            // float, long, arrays, collections and the like aren't supported by the config screen
            Library.LOGGER.warn("Configuration property '%s' in %s has unsupported type %s; it is ignored", f.getName(), this.clazz.getName(), type.getName());
            return null;
        }

        private ConfigElement<?> processGroup(Object prototype, String key, Field f) {
            var group = new ConfigElement.PropertyGroup(key, f);
            var groupPrototype = group.getInstance(prototype);
            if (groupPrototype == null)
                throw new IllegalStateException(String.format("Property group '%s' in %s must be initialized", f.getName(), this.clazz.getName()));
            group.setChildren(new GenerationContext(f.getType(), key).generateLevel(groupPrototype));
            return group;
        }

        private ConfigElement<?> processInteger(Object prototype, String key, Field f) {
            var element = new ConfigElement.IntegerValue(prototype, key, f);
            var range = f.getAnnotation(ConfigurationData.IntegerRange.class);
            var slider = f.getAnnotation(ConfigurationData.Slider.class);
            if (range != null && slider != null)
                throw new IllegalStateException(String.format("Configuration property '%s' in %s has both @IntegerRange and @Slider; @Slider holds the range", f.getName(), this.clazz.getName()));
            if (range != null)
                element.setRange(range.min(), range.max());
            if (slider != null) {
                if (slider.min() >= slider.max())
                    throw new IllegalStateException(String.format("Configuration property '%s' in %s has a @Slider minimum (%d) that isn't below its maximum (%d)", f.getName(), this.clazz.getName(), slider.min(), slider.max()));
                element.setRange(slider.min(), slider.max());
            }
            return element;
        }

        private ConfigElement<?> processDouble(Object prototype, String key, Field f) {
            var element = new ConfigElement.DoubleValue(prototype, key, f);
            var range = f.getAnnotation(ConfigurationData.DoubleRange.class);
            var slider = f.getAnnotation(ConfigurationData.DoubleSlider.class);
            if (range != null && slider != null)
                throw new IllegalStateException(String.format("Configuration property '%s' in %s has both @DoubleRange and @DoubleSlider; @DoubleSlider holds the range", f.getName(), this.clazz.getName()));
            if (range != null)
                element.setRange(range.min(), range.max());
            if (slider != null) {
                DoubleSliderScale scale;
                try {
                    scale = new DoubleSliderScale(slider.min(), slider.max(), slider.step());
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException(String.format("Configuration property '%s' in %s has a @DoubleSlider that isn't valid: %s", f.getName(), this.clazz.getName(), e.getMessage()), e);
                }
                // Otherwise the slider's reset button would set a nearby value rather than the default
                if (!scale.isPosition(element.defaultValue()))
                    throw new IllegalStateException(String.format("Configuration property '%s' in %s has the default %s, which isn't one of its @DoubleSlider positions", f.getName(), this.clazz.getName(), element.defaultValue()));
                element.setSlider(slider.min(), slider.max(), scale);
            }
            return element;
        }

        private String calculateLangKey(ConfigurationData.Property property, Field f) {
            var segment = property.value().isEmpty() ? f.getName() : property.value();
            return this.translationRoot + "." + segment;
        }
    }
}
