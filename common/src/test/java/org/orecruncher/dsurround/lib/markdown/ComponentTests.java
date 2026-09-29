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
public class ComponentTests {

    // Need this to bootstrap the Minecraft environment so tests run
    @BeforeAll
    static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testConversionToMinecraftTextComponent() {
        String markdownInput = "# Welcome\n- <color:green>**Status Online**</color>\n- Visit [our site](https://example.com)";

        Optional<Component> minecraftText = MarkdownParser.markdownToComponent(markdownInput);
        assertTrue(minecraftText.isPresent());

        var plainText = minecraftText.get().getString();
        assertTrue(plainText.contains("Welcome"));
        assertTrue(plainText.contains("• Status Online"));
        assertTrue(plainText.contains("• Visit our site"));
    }

    @Test
    void testMinecraftTextComponentSiblingsAndStyling() {
        String markdownInput = "Hello **world**!";

        Optional<Component> minecraftText = MarkdownParser.markdownToComponent(markdownInput);
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

    @Test
    public void testHoverEventUsesTranslationKey() {
        String markdown = "[Link](https://example.com)";
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown);

        assertNotNull(jsonOutput);
        assertTrue(jsonOutput.contains("\"translate\":\"dsurround.text.markdown.hovertext\""), "Should use the specified translation resource key");
        assertTrue(jsonOutput.contains("\"with\":[{\"text\":\"https://example.com\",\"color\":\"%s\"}]".formatted(Options.DEFAULT.linkColor())), "Should pass the URL as a dynamic parameter in 'with'");
        assertFalse(jsonOutput.contains("Click to open:"), "Should ignore the hardcoded template when a translation key is present");
    }
}
