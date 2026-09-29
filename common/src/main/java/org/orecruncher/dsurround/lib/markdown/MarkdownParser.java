package org.orecruncher.dsurround.lib.markdown;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@VisibleForTesting
class MarkdownParser {

    public static String parseToComponentJson(String markdown) {
        return parseToComponentJson(markdown, ParserOptions.DEFAULT);
    }

    /**
     * Parses a Markdown string into a Minecraft JSON Text Component string using custom options.
     */
    public static String parseToComponentJson(String markdown, ParserOptions options) {
        if (markdown == null)
            markdown = "";

        // CRITICAL FIX: Normalize Windows line endings (\r\n) and strip out any stray
        // carriage returns (\r) that cause Minecraft to render the [CR] glyph box.
        markdown = markdown.replace("\r\n", "\n").replace('\r', '\n');

        JsonObject baseRoot = new JsonObject();
        baseRoot.addProperty("text", "");

        JsonArray siblings = new JsonArray();
        parseBlock(markdown, markdown.length(), siblings, options);

        baseRoot.add("extra", siblings);
        baseRoot.addProperty("text", "");
        return baseRoot.toString();
    }

    private static void parseBlock(String text, int end, JsonArray siblings, ParserOptions options) {
        int i = 0;
        while (i < end) {
            // Skip leading whitespace/newlines for clean block parsing
            while (i < end && text.charAt(i) == '\n') {
                i++;
            }
            if (i >= end) break;

            int lineStart = i;
            int nextNewline = text.indexOf('\n', lineStart);
            if (nextNewline == -1 || nextNewline > end) nextNewline = end;
            int lineEnd = nextNewline;

            // 1. Check Heading (# Heading) at line start
            int hashCount = 0;
            int idx = lineStart;
            while (idx < lineEnd && text.charAt(idx) == '#') {
                hashCount++;
                idx++;
            }
            if (hashCount > 0 && hashCount <= 6 && idx < lineEnd && text.charAt(idx) == ' ') {
                idx++; // skip space
                String headingContent = text.substring(idx, lineEnd);
                String headerColor = options.defaultHeadingColor();

                JsonObject headingObj = createTextElement("", true, false, false, false, headerColor, options);
                JsonArray headingSiblings = new JsonArray();
                parseInline(headingContent, 0, headingContent.length(), headingSiblings, headerColor, options);
                headingObj.add("extra", headingSiblings);
                headingObj.addProperty("text", "");

                siblings.add(headingObj);
                siblings.add(createTextElement("\n\n", false, false, false, false, null, options)); // Hard paragraph break

                i = nextNewline + 1;
                continue;
            }

            // 2. Check List item (- item, * item, 1. item) at line start
            char c = text.charAt(lineStart);
            var b = lineStart + 1 < lineEnd && text.charAt(lineStart + 1) == ' ';
            boolean isBullet = (c == '-' || c == '*') && b;
            boolean isOrdered = false;
            int numEnd = lineStart;
            while (numEnd < lineEnd && Character.isDigit(text.charAt(numEnd))) {
                numEnd++;
            }
            if (numEnd > lineStart && numEnd + 1 < lineEnd && text.charAt(numEnd) == '.' && text.charAt(numEnd + 1) == ' ') {
                isOrdered = true;
            }

            if (isBullet || isOrdered) {
                int contentStart;
                String prefix;
                if (isBullet) {
                    prefix = options.unorderedBullet();
                    contentStart = lineStart + 2;
                } else {
                    int dotIdx = text.indexOf('.', lineStart);
                    prefix = text.substring(lineStart, dotIdx + 2);
                    contentStart = dotIdx + 2;
                }

                // Append bullet/number prefix
                siblings.add(createTextElement(prefix, false, false, false, false, options.defaultBulletColor(), options));

                // Parse the inline content of the list item
                int itemEnd = nextNewline;
                parseInline(text, contentStart, itemEnd, siblings, null, options);

                // Look ahead to see if the next line is ALSO part of a list
                boolean nextIsList = false;
                int nextLineStart = nextNewline + 1;
                if (nextLineStart < end) {
                    char nextC = text.charAt(nextLineStart);
                    boolean nextIsBullet = (nextC == '-' || nextC == '*') && (nextLineStart + 1 < end && text.charAt(nextLineStart + 1) == ' ');
                    boolean nextIsOrdered = false;
                    int nextNumEnd = nextLineStart;
                    while (nextNumEnd < end && Character.isDigit(text.charAt(nextNumEnd))) {
                        nextNumEnd++;
                    }
                    if (nextNumEnd > nextLineStart && nextNumEnd + 1 < end && text.charAt(nextNumEnd) == '.' && text.charAt(nextNumEnd + 1) == ' ') {
                        nextIsOrdered = true;
                    }
                    nextIsList = nextIsBullet || nextIsOrdered;
                }

                String listBreak = nextIsList ? "\n" : "\n\n";
                siblings.add(createTextElement(listBreak, false, false, false, false, null, options));

                i = nextNewline + 1;
                continue;
            }

            // 3. Check Block Quote (> quote) at line start
            boolean isQuote = (text.charAt(lineStart) == '>' && (lineStart + 1 < lineEnd && text.charAt(lineStart + 1) == ' '));

            if (isQuote) {
                int contentStart = lineStart + 2;
                String prefix = options.quotePrefix();

                // Append quote prefix (e.g., "│ ") styled with the quote color
                if (prefix != null && !prefix.isEmpty()) {
                    siblings.add(createTextElement(prefix, false, false, false, false, options.defaultQuoteColor(), options));
                }

                // Check if the content inside the quote is a header (e.g., "> # Heading")
                hashCount = 0;
                idx = contentStart;
                while (idx < lineEnd && text.charAt(idx) == '#') {
                    hashCount++;
                    idx++;
                }

                if (hashCount > 0 && hashCount <= 6 && idx < lineEnd && text.charAt(idx) == ' ') {
                    idx++; // skip space after hashes
                    String headingContent = text.substring(idx, lineEnd);
                    String headerColor = options.defaultHeadingColor();

                    // Create a bold header element styled with the header color
                    JsonObject headingObj = createTextElement("", true, false, false, false, headerColor, options);
                    JsonArray headingSiblings = new JsonArray();
                    parseInline(headingContent, 0, headingContent.length(), headingSiblings, headerColor, options);
                    headingObj.add("extra", headingSiblings);
                    headingObj.addProperty("text", "");

                    siblings.add(headingObj);
                } else {
                    // Standard quote line with regular inline formatting (bold, italics, links)
                    JsonObject quoteObj = createTextElement("", false, false, false, false, options.defaultQuoteColor(), options);
                    JsonArray quoteSiblings = new JsonArray();

                    parseInline(text, contentStart, nextNewline, quoteSiblings, null, options);

                    quoteObj.add("extra", quoteSiblings);
                    quoteObj.addProperty("text", "");

                    siblings.add(quoteObj);
                }

                // Look ahead to see if the next line is ALSO part of a block quote
                boolean nextIsQuote = false;
                int nextLineStart = nextNewline + 1;
                if (nextLineStart < end) {
                    nextIsQuote = (text.charAt(nextLineStart) == '>' && (nextLineStart + 1 < end && text.charAt(nextLineStart + 1) == ' '));
                }

                String quoteBreak = nextIsQuote ? "\n" : "\n\n";
                siblings.add(createTextElement(quoteBreak, false, false, false, false, null, options));

                i = nextNewline + 1;
                continue;
            }

            // 4. Standard Paragraph Block: Gather consecutive lines until a blank line (\n\n) or block element
            int paragraphEnd = lineStart;
            while (paragraphEnd < end) {
                int nextN = text.indexOf('\n', paragraphEnd);
                if (nextN == -1 || nextN >= end) {
                    paragraphEnd = end;
                    break;
                }
                // Check for blank line (double newline)
                if (nextN + 1 < end && text.charAt(nextN + 1) == '\n') {
                    paragraphEnd = nextN;
                    break;
                }
                // Check if the next line starts a heading, list, or quote block
                int potentialLineStart = nextN + 1;
                if (potentialLineStart < end) {
                    char nextC = text.charAt(potentialLineStart);
                    boolean isNextBlock = (nextC == '#' || nextC == '-' || nextC == '*' || nextC == '>' || Character.isDigit(nextC));
                    if (isNextBlock) {
                        paragraphEnd = nextN;
                        break;
                    }
                }
                // Safely advance past the newline to check the next line
                paragraphEnd = nextN + 1;
            }

            // Parse the paragraph text, converting internal single newlines into spaces (soft breaks)
            StringBuilder paragraphBuilder = new StringBuilder();
            for (int pIdx = lineStart; pIdx < paragraphEnd; pIdx++) {
                char ch = text.charAt(pIdx);
                if (ch == '\n') {
                    paragraphBuilder.append(' '); // Soft break becomes space
                } else {
                    paragraphBuilder.append(ch);
                }
            }

            String paragraphText = paragraphBuilder.toString().trim();
            if (!paragraphText.isEmpty()) {
                parseInline(paragraphText, 0, paragraphText.length(), siblings, null, options);
                siblings.add(createTextElement("\n\n", false, false, false, false, null, options));
            }

            i = paragraphEnd;
            while (i < end && text.charAt(i) == '\n') {
                i++;
            }
        }
    }

    private static void parseInline(String text, int start, int end, JsonArray siblings, String currentColor, ParserOptions options) {
        int i = start;
        int plainStart = start;

        while (i < end) {
            // **bold**
            if (i + 1 < end && text.startsWith("**", i)) {
                if (i > plainStart) {
                    siblings.add(createTextElement(text.substring(plainStart, i), false, false, false, false, currentColor, options));
                }
                int close = text.indexOf("**", i + 2);
                if (close != -1 && close <= end) {
                    String content = text.substring(i + 2, close);
                    JsonObject obj = createTextElement("", true, false, false, false, currentColor, options);
                    JsonArray inner = new JsonArray();
                    parseInline(content, 0, content.length(), inner, currentColor, options);
                    obj.add("extra", inner);
                    obj.addProperty("text", "");
                    siblings.add(obj);
                    i = close + 2;
                    plainStart = i;
                    continue;
                }
            }
            // ~~strikethrough~~
            if (i + 1 < end && text.startsWith("~~", i)) {
                if (i > plainStart) {
                    siblings.add(createTextElement(text.substring(plainStart, i), false, false, false, false, currentColor, options));
                }
                int close = text.indexOf("~~", i + 2);
                if (close != -1 && close <= end) {
                    String content = text.substring(i + 2, close);
                    JsonObject obj = createTextElement("", false, false, true, false, currentColor, options);
                    JsonArray inner = new JsonArray();
                    parseInline(content, 0, content.length(), inner, currentColor, options);
                    obj.add("extra", inner);
                    obj.addProperty("text", "");
                    siblings.add(obj);
                    i = close + 2;
                    plainStart = i;
                    continue;
                }
            }
            // __underline__
            if (i + 1 < end && text.startsWith("__", i)) {
                if (i > plainStart) {
                    siblings.add(createTextElement(text.substring(plainStart, i), false, false, false, false, currentColor, options));
                }
                int close = text.indexOf("__", i + 2);
                if (close != -1 && close <= end) {
                    String content = text.substring(i + 2, close);
                    JsonObject obj = createTextElement("", false, false, false, true, currentColor, options);
                    JsonArray inner = new JsonArray();
                    parseInline(content, 0, content.length(), inner, currentColor, options);
                    obj.add("extra", inner);
                    obj.addProperty("text", "");
                    siblings.add(obj);
                    i = close + 2;
                    plainStart = i;
                    continue;
                }
            }
            // *italic* (ensuring not **)
            if (text.charAt(i) == '*' && (i + 1 >= end || text.charAt(i + 1) != '*')) {
                if (i > plainStart) {
                    siblings.add(createTextElement(text.substring(plainStart, i), false, false, false, false, currentColor, options));
                }
                int close = text.indexOf('*', i + 1);
                if (close != -1 && close <= end && (close + 1 >= end || text.charAt(close + 1) != '*')) {
                    String content = text.substring(i + 1, close);
                    JsonObject obj = createTextElement("", false, true, false, false, currentColor, options);
                    JsonArray inner = new JsonArray();
                    parseInline(content, 0, content.length(), inner, currentColor, options);
                    obj.add("extra", inner);
                    obj.addProperty("text", "");
                    siblings.add(obj);
                    i = close + 1;
                    plainStart = i;
                    continue;
                }
            }
            // Link [Text](URL)
            if (text.charAt(i) == '[') {
                int closeBracket = text.indexOf(']', i + 1);
                if (closeBracket != -1 && closeBracket + 1 < end && text.charAt(closeBracket + 1) == '(') {
                    int closeParen = text.indexOf(')', closeBracket + 2);
                    if (closeParen != -1 && closeParen <= end) {
                        if (i > plainStart) {
                            siblings.add(createTextElement(text.substring(plainStart, i), false, false, false, false, currentColor, options));
                        }
                        String linkText = text.substring(i + 1, closeBracket);
                        String url = text.substring(closeBracket + 2, closeParen);
                        siblings.add(createLinkElement(linkText, url, options));
                        i = closeParen + 1;
                        plainStart = i;
                        continue;
                    }
                }
            }
            // Color tag <color:VALUE>...</color>
            if (text.startsWith("<color:", i)) {
                int gt = text.indexOf('>', i + 7);
                if (gt != -1 && gt <= end) {
                    String colorVal = text.substring(i + 7, gt);
                    int closeTag = text.indexOf("</color>", gt + 1);
                    if (closeTag != -1 && closeTag <= end) {
                        if (i > plainStart) {
                            siblings.add(createTextElement(text.substring(plainStart, i), false, false, false, false, currentColor, options));
                        }
                        String content = text.substring(gt + 1, closeTag);
                        parseInline(content, 0, content.length(), siblings, colorVal, options);
                        i = closeTag + 8;
                        plainStart = i;
                        continue;
                    }
                }
            }

            i++;
        }

        if (plainStart < end) {
            String plainText = text.substring(plainStart, end);
            if (!plainText.isEmpty()) {
                siblings.add(createTextElement(plainText, false, false, false, false, currentColor, options));
            }
        }
    }

    private static JsonObject createTextElement(String text, boolean bold, boolean italic, boolean strikethrough, boolean underline, String color, ParserOptions options) {
        JsonObject obj = new JsonObject();
        obj.addProperty("text", text);
        if (bold)
            obj.addProperty("bold", true);
        if (italic)
            obj.addProperty("italic", true);
        if (strikethrough)
            obj.addProperty("strikethrough", true);
        if (underline)
            obj.addProperty("underline", true);
        if (color != null && !color.isBlank()) {
            obj.addProperty("color", color);
        } else if (options.defaultTextColor() != null) {
            obj.addProperty("color", options.defaultTextColor());
        }
        if (options.font() != null) {
            obj.addProperty("font", options.font());
        }
        return obj;
    }

    private static JsonObject createLinkElement(String text, String url, ParserOptions options) {
        JsonObject obj = new JsonObject();
        obj.addProperty("text", text);
        obj.addProperty("underlined", true);

        if (options.defaultLinkColor() != null) {
            obj.addProperty("color", options.defaultLinkColor());
        }

        if (options.font() != null) {
            obj.addProperty("font", options.font());
        }

        JsonObject clickEvent = new JsonObject();
        clickEvent.addProperty("action", "open_url");
        clickEvent.addProperty("value", url);
        obj.add("clickEvent", clickEvent);

        JsonObject hoverContent = new JsonObject();
        hoverContent.addProperty("text", url);
        if (options.font() != null) {
            hoverContent.addProperty("font", options.font());
        }

        JsonObject hoverEvent = new JsonObject();
        hoverEvent.addProperty("action", "show_text");
        hoverEvent.add("contents", hoverContent);
        obj.add("hoverEvent", hoverEvent);

        return obj;
    }
}