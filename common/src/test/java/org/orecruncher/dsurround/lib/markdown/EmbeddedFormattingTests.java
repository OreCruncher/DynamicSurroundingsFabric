package org.orecruncher.dsurround.lib.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EmbeddedFormattingTests {
    @Test
    public void testBoldInsideItalic() {
        String markdown = "*Italic text with **bold** word*";
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown);

        assertNotNull(jsonOutput, "JSON output should not be null");

        // Verify text content
        assertTrue(jsonOutput.contains("Italic text with "), "Should contain outer italic text");
        assertTrue(jsonOutput.contains("bold"), "Should contain inner bold text");

        // Verify nested style attributes
        assertTrue(jsonOutput.contains("\"italic\":true"), "Should apply italic formatting");
        assertTrue(jsonOutput.contains("\"bold\":true"), "Should apply nested bold formatting");
    }

    @Test
    public void testColorInsideBold() {
        String markdown = "**<color:#FF0000>Red Bold Text</color>**";
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown);

        assertNotNull(jsonOutput, "JSON output should not be null");

        // Verify text content
        assertTrue(jsonOutput.contains("Red Bold Text"), "Should contain the colored bold text");

        // Verify combined styles
        assertTrue(jsonOutput.contains("\"bold\":true"), "Should preserve bold styling");
        assertTrue(jsonOutput.contains("\"color\":\"#FF0000\""), "Should apply the nested custom color");
    }}
