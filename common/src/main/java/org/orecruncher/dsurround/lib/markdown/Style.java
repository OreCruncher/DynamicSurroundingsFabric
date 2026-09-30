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
        return Objects.equals(this.color, other.color) &&
                Objects.equals(this.font, other.font) &&
                Objects.equals(this.bold, other.bold) &&
                Objects.equals(this.italic, other.italic) &&
                Objects.equals(this.underline, other.underline) &&
                Objects.equals(this.strikethrough, other.strikethrough) &&
                Objects.equals(this.clickEventUrl, other.clickEventUrl) &&
                Objects.equals(this.hoverEventText, other.hoverEventText);
    }
}