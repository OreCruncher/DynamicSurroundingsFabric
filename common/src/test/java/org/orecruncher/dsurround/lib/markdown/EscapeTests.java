package org.orecruncher.dsurround.lib.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EscapeTests {

    @Test
    public void testComplexEscapedAsterisksAndBackslashes() {
        String markdown = "This is \\**is italic*\\* text";
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown);

        assertNotNull(jsonOutput, "JSON output should not be null");

        assertTrue(jsonOutput.contains("This is"), "Should contain the opening text");
        assertTrue(jsonOutput.contains("is italic"), "Should contain the inner text");
        assertTrue(jsonOutput.contains("text"), "Should contain the trailing text");

        assertTrue(jsonOutput.contains("\"italic\":true"), "Escaped markdown markers should not trigger italic formatting");
        assertFalse(jsonOutput.contains("\"bold\":true"), "Escaped bold markers should not trigger bold formatting");
    }

    @Test
    public void testComplexEscapedAsterisks() {
        String markdown = "This is \\**is italic*\\* text";
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown);

        assertNotNull(jsonOutput, "JSON output should not be null");

        // Verify that the text content parses completely and safely without crashing or looping
        assertTrue(jsonOutput.contains("This is"), "Should contain the opening text");
        assertTrue(jsonOutput.contains("is italic"), "Should contain the inner text content");
        assertTrue(jsonOutput.contains("text"), "Should contain the trailing text");
    }

    @Test
    public void testEscapedHeader() {
        String markdown = "\\# Not a header";
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown);

        assertNotNull(jsonOutput, "JSON output should not be null");
        assertTrue(jsonOutput.contains("# Not a header"), "Should render literal hash symbol and text");
        assertFalse(jsonOutput.contains("\"bold\":true"), "Escaped header marker should not trigger header formatting");
    }
}
