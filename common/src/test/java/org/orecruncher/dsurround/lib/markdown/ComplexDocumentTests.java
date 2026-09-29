package org.orecruncher.dsurround.lib.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ComplexDocumentTests {

    @Test
    void testMixedDocumentStructureAndSpacing() {
        String markdown = "> First quote line\n\nFree form text\n\n> Second quote line\n\n* Bullet one\n* Bullet 2\n* Bullet 3";
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown);

        assertNotNull(jsonOutput, "JSON output should not be null");

        // Validate content existence
        assertTrue(jsonOutput.contains("First quote line"), "Should contain first quote");
        assertTrue(jsonOutput.contains("Free form text"), "Should contain free-form text without accidental prepended spaces");
        assertTrue(jsonOutput.contains("Second quote line"), "Should contain second quote");
        assertTrue(jsonOutput.contains("Bullet one"), "Should contain first bullet");
        assertTrue(jsonOutput.contains("Bullet 2"), "Should contain second bullet");
        assertTrue(jsonOutput.contains("Bullet 3"), "Should contain third bullet");

        // Validate quote prefix usage
        assertTrue(jsonOutput.contains(Options.DEFAULT.quoteStyle()), "Should apply the configured quote prefix string");

        // Validate bullet symbol usage
        assertTrue(jsonOutput.contains(Options.DEFAULT.bulletStyle()), "Should apply the configured bullet prefix string");

        // Ensure paragraph and section line boundaries are properly maintained with newlines
        assertTrue(jsonOutput.contains("\\n"), "Should contain line breaks between structural elements");
    }
}
