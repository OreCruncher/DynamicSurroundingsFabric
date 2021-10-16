package org.orecruncher.dsurround.lib.config;

import joptsimple.internal.Strings;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.lang.reflect.Field;
import java.util.Collection;

public class ConfigProcessor {

    public static Collection<ConfigElement<?>> generateAccessors(ConfigurationData data) {
        var ctx = new GenerationContext(data.getTranslationRoot());
        return ctx.generateLevel(data);
    }

    private static class GenerationContext {

        private String translationRoot;

        GenerationContext(String translationRoot) {
            this.translationRoot = translationRoot;
        }

        public Collection<ConfigElement<?>> generateLevel(Object instance) {

            var elements = new ObjectArray<ConfigElement<?>>();

            var fields = instance.getClass().getFields();

            for (var f : fields) {

                // See if it is marked as a property.  If not continue.
                ConfigurationData.Property property = f.getAnnotation(ConfigurationData.Property.class);
                if (property == null)
                    continue;

                // Only support a narrow set of properties
                var fieldType = f.getType();

                if (!fieldType.isPrimitive()) {
                    elements.add(processClassInstance(property, instance, f));
                } else {
                    ConfigElement<?> element;
                    if (fieldType == Integer.class || fieldType == int.class) {
                        element = processIntegerInstance(property, instance, f);
                    } else if (fieldType == Double.class || fieldType == Float.class || fieldType == double.class || fieldType == float.class) {
                        element = processDoubleInstance(property, instance, f);
                    } else if (fieldType == String.class) {
                        element = processStringInstance(property, instance, f);
                    } else if (fieldType == Boolean.class || fieldType == boolean.class) {
                        element = processBooleanInstance(property, instance, f);
                    } else {
                        // Not a supported type so skip.  Probably should log a warning or some such here.
                        continue;
                    }

                    elements.add(element);
                }
            }

            return elements;
        }

        GenerationContext createChild(String langKey) {
            return new GenerationContext(langKey);
        }

        private ConfigElement<?> processClassInstance(ConfigurationData.Property property, Object instance, Field f) {
            var wrapper = new ConfigValue<>(instance, f);
            var ctx = this.createChild(calculateLangKey(property, f));
            var subElements = ctx.generateLevel(wrapper.get());
            return new ConfigElement.PropertyGroup(instance.getClass(), this.translationRoot, subElements);
        }

        private ConfigElement<?> processStringInstance(ConfigurationData.Property property, Object instance, Field f) {
            var wrapper = new ConfigValue<String>(instance, f);
            return new ConfigElement.StringValue(calculateLangKey(property, f), wrapper);
        }

        private ConfigElement<?> processBooleanInstance(ConfigurationData.Property property, Object instance, Field f) {
            var wrapper = new ConfigValue<Boolean>(instance, f);
            return new ConfigElement.BooleanValue(calculateLangKey(property, f), wrapper);
        }

        private ConfigElement<?> processIntegerInstance(ConfigurationData.Property property, Object instance, Field f) {
            var wrapper = new ConfigValue<Integer>(instance, f);
            var element = new ConfigElement.IntegerValue(calculateLangKey(property, f), wrapper);
            var range = f.getAnnotation(ConfigurationData.IntegerRange.class);
            if (range != null) {
                element.setRange(range.min(), range.max());
            }
            return element;
        }

        private ConfigElement<?> processDoubleInstance(ConfigurationData.Property property, Object instance, Field f) {
            var wrapper = new ConfigValue<Double>(instance, f);
            var element = new ConfigElement.DoubleValue(calculateLangKey(property, f), wrapper);
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
