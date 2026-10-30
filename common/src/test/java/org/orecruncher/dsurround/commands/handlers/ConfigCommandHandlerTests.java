package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.PresetLoaderTests;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for /dsconfig preset's answers that don't save the configuration.
 */
public class ConfigCommandHandlerTests {

    // Configuration's enums use game classes, and the list's hover text the registries
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Configuration config() {
        var config = new Configuration();
        config.setPresets(PresetLoaderTests.modPresets(config));
        return config;
    }

    private static TranslatableContents contentsOf(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents());
    }

    @Test
    void listsEveryPresetByGroup() {
        var message = contentsOf(ConfigCommandHandler.presets(config()));
        assertEquals("dsurround.command.dsconfig.preset.list", message.getKey());

        // No translations in tests: the groups by their names
        var list = ((Component) message.getArgs()[0]).getString();
        assertEquals("features: vanillaPlus, atmosphere, world, rpg, defaults; performance: low, medium, high", list);
    }

    @Test
    void anUnknownPresetChangesNothing() {
        var config = config();
        config.entityEffects.enableDamagePopoffs = false;
        var message = contentsOf(ConfigCommandHandler.preset(config, "nonsense"));

        assertEquals("dsurround.command.dsconfig.preset.unknown", message.getKey());
        assertEquals("nonsense", message.getArgs()[0]);
        assertFalse(config.entityEffects.enableDamagePopoffs);
    }
}
