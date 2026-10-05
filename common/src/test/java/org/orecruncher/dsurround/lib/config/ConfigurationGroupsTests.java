package org.orecruncher.dsurround.lib.config;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.di.internal.DependencyContainer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurationGroupsTests {

    /**
     * The mod's groups of settings, found independently of getGroups(): its public property fields whose type is one
     * of its own nested classes.
     */
    private static List<Field> groupFields() {
        var fields = new ArrayList<Field>();
        for (var field : Configuration.class.getFields()) {
            if (!field.isAnnotationPresent(ConfigurationData.Property.class) || Modifier.isStatic(field.getModifiers()))
                continue;
            if (field.getType().getDeclaringClass() == Configuration.class)
                fields.add(field);
        }
        return fields;
    }

    @Test
    void findsEveryGroup() throws IllegalAccessException {
        var config = new Configuration();
        var fields = groupFields();
        var groups = config.getGroups();

        assertFalse(fields.isEmpty());
        assertEquals(fields.size(), groups.size());
        for (var field : fields) {
            var group = field.get(config);
            assertTrue(groups.stream().anyMatch(g -> g == group), "missing group " + field.getName());
        }
    }

    @Test
    void includesTheAuroraOptions() {
        // The group that was left out when the groups were registered from a hand-kept list
        var config = new Configuration();
        assertTrue(config.getGroups().stream().anyMatch(g -> g == config.auroraOptions));
    }

    @Test
    void registersTheConfigurationAndEachGroup() throws IllegalAccessException {
        var config = new Configuration();
        var container = new DependencyContainer("test");
        config.registerWith(container);

        assertSame(config, container.resolve(Configuration.class));
        for (var field : groupFields())
            assertSame(field.get(config), container.resolve(field.getType()), "group " + field.getName() + " not registered");
        assertSame(config.auroraOptions, container.resolve(Configuration.AuroraOptions.class));
    }
}
