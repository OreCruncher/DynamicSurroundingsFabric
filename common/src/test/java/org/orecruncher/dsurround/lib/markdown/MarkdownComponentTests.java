package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unused")
public class MarkdownComponentTests {

    // Need this to bootstrap the Minecraft environment so tests run
    @BeforeAll
    static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testConversionToMinecraftTextComponent() {
        String markdownInput = "# Welcome\n- <color:green>**Status Online**</color>\n- Visit [our site](https://example.com)";

        Optional<Component> minecraftText = MarkdownHelper.markdownToComponent(markdownInput);
        assertTrue(minecraftText.isPresent());

        var plainText = minecraftText.get().getString();
        assertTrue(plainText.contains("Welcome"));
        assertTrue(plainText.contains("• Status Online"));
        assertTrue(plainText.contains("• Visit our site"));
    }

    @Test
    void testMinecraftTextComponentSiblingsAndStyling() {
        String markdownInput = "Hello **world**!";

        Optional<Component> minecraftText = MarkdownHelper.markdownToComponent(markdownInput);
        assertTrue(minecraftText.isPresent());

        var siblings = minecraftText.get().getSiblings();
        assertFalse(siblings.isEmpty(), "Parsed component should contain child siblings");

        boolean foundBoldComponent = false;
        for (var sibling : siblings) {
            if (sibling.getStyle().isBold()) {
                foundBoldComponent = true;
                assertEquals("world", sibling.getString());
            }
        }
        assertTrue(foundBoldComponent, "Minecraft Text component should preserve bold styling flag");
    }
}
