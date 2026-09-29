package org.orecruncher.dsurround.lib.markdown;

import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

class Tokenizer {
    private final List<Token> tokens;
    private int index = 0;
    private final Options options;

    public Tokenizer(List<Token> tokens, Options options) {
        this.tokens = tokens;
        this.options = options;
    }

    public ComponentNode tokenize() {
        Style rootStyle = new Style();
        rootStyle.color = options.textColor();
        rootStyle.font = options.font();
        ComponentNode root = new ComponentNode(rootStyle);

        Stack<Style> styleStack = new Stack<>();
        styleStack.push(rootStyle);

        boolean expectHeaderEnd = false;
        boolean inBlockquote = false;

        while (index < tokens.size()) {
            Token token = tokens.get(index++);

            switch (token.type()) {
                case TEXT -> {
                    Style currentStyle = styleStack.peek();
                    ComponentNode textNode = new ComponentNode(currentStyle);
                    textNode.append(token.source(), token.start(), token.end());
                    root.children.add(textNode);
                }
                case SOFT_BREAK -> {
                    // Soft breaks represent wrapped lines inside a paragraph and act as a single space
                    Style currentStyle = styleStack.peek();
                    ComponentNode spaceNode = new ComponentNode(currentStyle);
                    spaceNode.append(' ');
                    root.children.add(spaceNode);
                }
                case NEWLINE -> {
                    Style currentStyle = styleStack.peek();
                    ComponentNode nlNode = new ComponentNode(currentStyle);
                    nlNode.append('\n');
                    root.children.add(nlNode);

                    if (expectHeaderEnd && styleStack.size() > 1) {
                        styleStack.pop();
                        expectHeaderEnd = false;
                    }

                    // If we were inside a blockquote, pop its style on newline boundary
                    if (inBlockquote && styleStack.size() > 1) {
                        styleStack.pop();
                        inBlockquote = false;
                    }

                    // Handle consecutive blockquote marker on the next line if present
                    if (index < tokens.size() && tokens.get(index).type() == TokenType.BLOCKQUOTE_MARKER) {
                        index++;

                        Style quoteStyle = styleStack.peek().copy();
                        quoteStyle.color = options.quoteColor();
                        quoteStyle.italic = true;
                        styleStack.push(quoteStyle);
                        inBlockquote = true;

                        ComponentNode quoteNode = new ComponentNode(quoteStyle);
                        String quoteStr = options.quoteStyle() != null ? options.quoteStyle() : "";
                        for (int i = 0; i < quoteStr.length(); i++) {
                            quoteNode.append(quoteStr.charAt(i));
                        }
                        root.children.add(quoteNode);
                    }
                }
                case HEADER_MARKER -> {
                    Style headerStyle = styleStack.peek().copy();
                    headerStyle.color = options.headingColor();
                    headerStyle.bold = true;
                    styleStack.push(headerStyle);
                    expectHeaderEnd = true;
                }
                case BULLET_MARKER -> {
                    Style currentStyle = styleStack.peek();
                    ComponentNode bulletNode = new ComponentNode(currentStyle);
                    String bulletStr = options.bulletStyle() != null ? options.bulletStyle() : "• ";
                    for (int i = 0; i < bulletStr.length(); i++) {
                        bulletNode.append(bulletStr.charAt(i));
                    }
                    root.children.add(bulletNode);
                }
                case BLOCKQUOTE_MARKER -> {
                    if (!inBlockquote) {
                        Style quoteStyle = styleStack.peek().copy();
                        quoteStyle.color = options.quoteColor();
                        styleStack.push(quoteStyle);
                        inBlockquote = true;
                    }
                    Style currentStyle = styleStack.peek();
                    ComponentNode quoteNode = new ComponentNode(currentStyle);
                    String quoteStr = options.quoteStyle() != null ? options.quoteStyle() : "";
                    for (int i = 0; i < quoteStr.length(); i++) {
                        quoteNode.append(quoteStr.charAt(i));
                    }
                    root.children.add(quoteNode);
                }
                case BOLD_MARKER -> toggleStyle(styleStack, s -> s.bold = (s.bold == null || !s.bold));
                case ITALIC_MARKER -> toggleStyle(styleStack, s -> s.italic = (s.italic == null || !s.italic));
                case UNDERLINE_MARKER -> toggleStyle(styleStack, s -> s.underline = (s.underline == null || !s.underline));
                case STRIKE_MARKER -> toggleStyle(styleStack, s -> s.strikethrough = (s.strikethrough == null || !s.strikethrough));
                case COLOR_START -> {
                    Style newStyle = styleStack.peek().copy();
                    newStyle.color = token.extraData();
                    styleStack.push(newStyle);
                }
                case COLOR_END -> {
                    if (styleStack.size() > 1) styleStack.pop();
                }
                case LINK_START -> {
                    StringBuilder linkText = new StringBuilder();
                    while (index < tokens.size() && tokens.get(index).type() != TokenType.LINK_MID) {
                        linkText.append(tokens.get(index++).value());
                    }
                    if (index < tokens.size()) index++;

                    StringBuilder urlBuilder = new StringBuilder();
                    while (index < tokens.size() && tokens.get(index).type() != TokenType.LINK_END) {
                        urlBuilder.append(tokens.get(index++).value());
                    }
                    if (index < tokens.size()) index++;

                    String url = urlBuilder.toString().trim();
                    Style linkStyle = styleStack.peek().copy();
                    linkStyle.color = options.linkColor();
                    linkStyle.underline = true;
                    linkStyle.clickEventUrl = url;
                    linkStyle.hoverEventText = String.format(options.linkHoverTemplate(), url);

                    ComponentNode linkNode = new ComponentNode(linkStyle);
                    for (int i = 0; i < linkText.length(); i++) {
                        linkNode.append(linkText.charAt(i));
                    }
                    root.children.add(linkNode);
                }
                default -> {
                    Style currentStyle = styleStack.peek();
                    ComponentNode fallback = new ComponentNode(currentStyle);
                    fallback.append(token.value(), 0, token.value().length());
                    root.children.add(fallback);
                }
            }
        }
        return root;
    }

    private void toggleStyle(Stack<Style> stack, Consumer<Style> modifier) {
        Style newStyle = stack.peek().copy();
        modifier.accept(newStyle);
        stack.push(newStyle);
    }
}