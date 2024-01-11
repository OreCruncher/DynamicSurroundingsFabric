package org.orecruncher.dsurround.lib.config;

import joptsimple.internal.Strings;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Optional;

public class ConfigProcessor {

    public static <T extends ConfigurationData> Optional<T> createPrototype(Class<T> clazz) {
        try {
            var ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return Optional.of(ctor.newInstance());
        } catch (Exception ex) {
            Library.getLogger().error(ex, "Unable to create prototype for %s", clazz.getName());
        }
        return Optional.empty();
    }

    public static <T extends ConfigurationData> Optional<Collection<ConfigElement<?>>> generateAccessors(Class<T> clazz) {
        try {
            var translationRootAnnotation = clazz.getAnnotation(ConfigurationData.TranslationRoot.class);

            String translationRoot;
            if (translationRootAnnotation != null) {
                translationRoot = translationRootAnnotation.value();
            } else {
                translationRoot = Constants.MOD_ID;
            }

            Optional<T> prototype = createPrototype(clazz);
            return prototype.map(p -> new GenerationContext<T>(clazz, translationRoot).generateLevel(p));
        } catch (Throwable t) {
            Library.getLogger().error(t, "Unable to generate accessors for %s", clazz.getName());
        }

        return Optional.empty();
    }

    private record GenerationContext<T>(Class<?> clazz, String translationRoot) {

        public Collection<ConfigElement<?>> generateLevel(Object prototype) {
            var elements = new ObjectArray<ConfigElement<?>>();

            var fields = this.clazz.getFields();

            for (var f : fields) {

                // See if it is marked as a property.  If not continue.
                ConfigurationData.Property property = f.getAnnotation(ConfigurationData.Property.class);
                if (property == null)
                    continue;

                // Only support a narrow set of properties
                var fieldType = f.getType();

                if (!fieldType.isPrimitive()) {
                    if (fieldType.isEnum())
                        elements.add(processEnumInstance(property, prototype, f));
                    else
                        elements.add(processClassInstance(property, prototype, f));
                } else {
                    ConfigElement<?> element;
                    if (fieldType == Integer.class || fieldType == int.class) {
                        element = processIntegerInstance(property, prototype, f);
                    } else if (fieldType == Double.class || fieldType == Float.class || fieldType == double.class || fieldType == float.class) {
                        element = processDoubleInstance(property, prototype, f);
                    } else if (fieldType == String.class) {
                        element = processStringInstance(property, prototype, f);
                    } else if (fieldType == Boolean.class || fieldType == boolean.class) {
                        element = processBooleanInstance(property, prototype, f);
                    } else {
                        // Not a supported type so skip.  Probably should log a warning or some such here.
                        continue;
                    }

                    elements.add(element);
                }
            }

            return elements;
        }

        GenerationContext<T> createChild(Class<?> clazz, String langKey) {
            return new GenerationContext<>(clazz, langKey);
        }

        private ConfigElement<?> processEnumInstance(ConfigurationData.Property property, Object instance, Field f) {
            var enumType = f.getAnnotation(ConfigurationData.EnumType.class);
            if (enumType == null)
                throw new RuntimeException("Enum field must have an EnumType annotation");
            return new ConfigElement.EnumValue(enumType.value(), instance, calculateLangKey(property, f), f);
        }

        private ConfigElement<?> processClassInstance(ConfigurationData.Property property, Object instance, Field f) {
            var wrapper = new ElementAccessor<>(f);
            var key = calculateLangKey(property, f);
            var ctx = this.createChild(f.getType(), key);
            var subElements = ctx.generateLevel(wrapper.get(instance));
            return new ConfigElement.PropertyGroup(key, subElements, f);
        }

        private ConfigElement<?> processStringInstance(ConfigurationData.Property property, Object instance, Field f) {
            return new ConfigElement.StringValue(instance, calculateLangKey(property, f), f);
        }

        private ConfigElement<?> processBooleanInstance(ConfigurationData.Property property, Object instance, Field f) {
            return new ConfigElement.BooleanValue(instance, calculateLangKey(property, f), f);
        }

        private ConfigElement<?> processIntegerInstance(ConfigurationData.Property property, Object instance, Field f) {
            var element = new ConfigElement.IntegerValue(instance, calculateLangKey(property, f), f);
            var range = f.getAnnotation(ConfigurationData.IntegerRange.class);
            if (range != null) {
                element.setRange(range.min(), range.max());
            }
            return element;
        }

        private ConfigElement<?> processDoubleInstance(ConfigurationData.Property property, Object instance, Field f) {
            var element = new ConfigElement.DoubleValue(instance, calculateLangKey(property, f), f);
            var range = f.getAnnotation(ConfigurationData.DoubleRange.class);
            if (range != null) {
                element.setRange(range.min(), range.max());
            }
            return element;
        }

        private String calculateLangKey(ConfigurationData.Property property, Field f) {
            var segment = f.getName();
            if (!Strings.isNullOrEmpty(property.value()))
                segment = property.value();
            return this.translationRoot + "." + segment;
        }
    }
}