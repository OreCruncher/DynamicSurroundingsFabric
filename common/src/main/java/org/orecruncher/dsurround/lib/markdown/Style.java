package org.orecruncher.dsurround.lib.markdown;

import java.util.Objects;

class Style {
    String color;
    String font;
    Boolean bold;
    Boolean italic;
    Boolean underline;
    Boolean strikethrough;
    String clickEventUrl;
    String hoverEventText;

    Style copy() {
        Style s = new Style();
        s.color = this.color;
        s.font = this.font;
        s.bold = this.bold;
        s.italic = this.italic;
        s.underline = this.underline;
        s.strikethrough = this.strikethrough;
        s.clickEventUrl = this.clickEventUrl;
        s.hoverEventText = this.hoverEventText;
        return s;
    }

    boolean matches(Style other) {
        return Objects.equals(color, other.color) &&
                Objects.equals(font, other.font) &&
                Objects.equals(bold, other.bold) &&
                Objects.equals(italic, other.italic) &&
                Objects.equals(underline, other.underline) &&
                Objects.equals(strikethrough, other.strikethrough) &&
                Objects.equals(clickEventUrl, other.clickEventUrl) &&
                Objects.equals(hoverEventText, other.hoverEventText);
    }
}