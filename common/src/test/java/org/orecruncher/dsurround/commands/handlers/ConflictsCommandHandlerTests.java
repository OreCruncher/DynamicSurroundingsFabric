// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.commands.handlers;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.compat.ShaderPackFeature;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for what {@code /dsconflicts} says about a shader pack: the effects it keeps off, of those switched on.
 */
public class ConflictsCommandHandlerTests {

    // The configuration's defaults name game classes
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String key(Component message) {
        return ((TranslatableContents) message.getContents()).getKey();
    }

    private static String argumentKey(Component message) {
        var argument = ((TranslatableContents) message.getContents()).getArgs()[0];
        return key((Component) argument);
    }

    @Test
    void nothingIsSaidWithoutAShaderPack() {
        assertEquals(List.of(), ConflictsCommandHandler.shaderPackMessages(false, new Configuration()));
    }

    @Test
    void withAShaderPackEveryEffectSwitchedOnIsListedAfterAHeading() {
        var messages = ConflictsCommandHandler.shaderPackMessages(true, new Configuration());

        assertEquals("dsurround.command.dsconflicts.shader_pack", key(messages.getFirst()));
        // Everything is switched on by default
        var listed = messages.subList(1, messages.size()).stream().map(ConflictsCommandHandlerTests::argumentKey).toList();
        assertEquals(List.of(ShaderPackFeature.values()).stream().map(ShaderPackFeature::translationKey).toList(), listed);
    }

    @Test
    void effectsSwitchedOffAreLeftOut() {
        var config = new Configuration();
        config.auroraOptions.enableAuroras = false;
        config.fireflyOptions.enableLight = false;
        // Flame jets still give light, so the lights are still listed
        var listed = ShaderPackFeature.switchedOn(config);

        assertFalse(listed.contains(ShaderPackFeature.AURORAS));
        assertTrue(listed.contains(ShaderPackFeature.LIGHTS));
        config.flameJetOptions.enableLight = false;
        assertFalse(ShaderPackFeature.switchedOn(config).contains(ShaderPackFeature.LIGHTS));
    }

    @Test
    void everyMessageAndEffectIsTranslated() throws Exception {
        try (var stream = ConflictsCommandHandlerTests.class.getResourceAsStream("/assets/dsurround/lang/en_us.json")) {
            assertNotNull(stream);
            var lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var feature : ShaderPackFeature.values())
                assertTrue(lang.has(feature.translationKey()), feature::name);
            for (var key : List.of("shader_pack", "shader_pack_entry"))
                assertTrue(lang.has("dsurround.command.dsconflicts." + key), key);
        }
    }
}
