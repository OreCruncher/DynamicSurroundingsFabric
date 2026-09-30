package org.orecruncher.dsurround.lib.markdown;

class JsonExporter {
    static String serialize(ComponentNode root, Options options) {
        StringBuilder json = new StringBuilder();
        serializeNode(root, json, true, options);
        return json.toString();
    }

    private static void serializeNode(ComponentNode node, StringBuilder json, boolean isRoot, Options options) {
        json.append("{");
        json.append("\"text\":\"").append(escapeJson(node.getText())).append("\"");

        Style s = node.style();
        if (s.color != null) {
            json.append(",\"color\":\"").append(s.color).append("\"");
        }
        if (s.font != null && !s.font.isEmpty()) {
            json.append(",\"font\":\"").append(s.font).append("\"");
        }
        if (s.bold != null) {
            json.append(",\"bold\":").append(s.bold);
        }
        if (s.italic != null) {
            json.append(",\"italic\":").append(s.italic);
        }
        if (s.underline != null) {
            json.append(",\"underlined\":").append(s.underline);
        }
        if (s.strikethrough != null) {
            json.append(",\"strikethrough\":").append(s.strikethrough);
        }

        if (s.clickEventUrl != null) {
            json.append(",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"").append(escapeJson(s.clickEventUrl)).append("\"}");
        }
        if (s.hoverEventText != null) {
            json.append(",\"hoverEvent\":{\"action\":\"show_text\",\"contents\":{");

            if (options.linkHoverTranslationKey() != null && !options.linkHoverTranslationKey().isEmpty()) {
                json.append("\"translate\":\"").append(escapeJson(options.linkHoverTranslationKey())).append("\"");
                json.append(",\"with\":[{\"text\":\"").append(escapeJson(s.clickEventUrl)).append("\"");
                if (s.color != null) {
                    json.append(",\"color\":\"").append(s.color).append("\"");
                }
                if (s.font != null && !s.font.isEmpty()) {
                    json.append(",\"font\":\"").append(s.font).append("\"");
                }
                json.append("}]");
            } else {
                json.append("\"text\":\"").append(escapeJson(s.hoverEventText)).append("\"");
                if (s.color != null) {
                    json.append(",\"color\":\"").append(s.color).append("\"");
                }
            }

            if (s.font != null && !s.font.isEmpty()) {
                json.append(",\"font\":\"").append(s.font).append("\"");
            }
            json.append("}}");
        }

        if (!node.children().isEmpty()) {
            json.append(",\"extra\":[");
            for (int i = 0; i < node.children().size(); i++) {
                if (i > 0) {
                    json.append(",");
                }
                serializeNode(node.children().get(i), json, false, options);
            }
            json.append("]");
        }

        json.append("}");
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}