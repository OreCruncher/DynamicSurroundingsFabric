package org.orecruncher.dsurround.mixinutils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClothFieldNamesTests {

    private static final Component COLORED_NAME = Component.literal("Auroras").withStyle(ChatFormatting.GOLD);
    private static final Component PLAIN_NAME = Component.literal("Auroras");

    // What Cloth shows for a name that is neither edited nor in error: gray
    private static Component clothGray(Component name) {
        return name.copy().withStyle(ChatFormatting.GRAY);
    }

    @Test
    void putsTheNamesColorBack() {
        var shown = ClothFieldNames.keepColor(clothGray(COLORED_NAME), COLORED_NAME, false, false, true);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), shown.getStyle().getColor());
        assertEquals("Auroras", shown.getString());
    }

    @Test
    void leavesAnUncoloredNameGray() {
        var gray = clothGray(PLAIN_NAME);
        assertSame(gray, ClothFieldNames.keepColor(gray, PLAIN_NAME, false, false, true));
    }

    @Test
    void leavesErrorsEditsAndDisabledNamesAsClothStylesThem() {
        var displayed = clothGray(COLORED_NAME);
        assertSame(displayed, ClothFieldNames.keepColor(displayed, COLORED_NAME, true, false, true), "error");
        assertSame(displayed, ClothFieldNames.keepColor(displayed, COLORED_NAME, false, true, true), "edited");
        assertSame(displayed, ClothFieldNames.keepColor(displayed, COLORED_NAME, false, false, false), "disabled");
    }

    @Test
    void keepsOtherStyling() {
        var displayed = COLORED_NAME.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE);
        var shown = ClothFieldNames.keepColor(displayed, COLORED_NAME, false, false, true);
        assertTrue(shown.getStyle().isUnderlined());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), shown.getStyle().getColor());
    }
}
