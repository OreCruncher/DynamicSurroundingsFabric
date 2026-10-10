package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.version.VersionCheckException;
import org.orecruncher.dsurround.lib.version.VersionResult;

import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the chat message /dsversion posts when its background check finishes.
 */
public class VersionCommandHandlerTests {

    @BeforeAll
    static void beforeAll() {
        // VersionResult's chat text uses HoverEvent, which needs the registries
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String keyOf(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents()).getKey();
    }

    @Test
    void failureSaysWhy() {
        var message = VersionCommandHandler.describe(null, new CompletionException(new VersionCheckException("no network")));
        var contents = assertInstanceOf(TranslatableContents.class, message.getContents());

        assertEquals("dsurround.command.dsversion.failure", contents.getKey());
        assertArrayEquals(new Object[]{"no network"}, contents.getArgs());
    }

    @Test
    void noRecommendationIsNotAFailure() {
        // Regression (at startup): a failed check was reported the same way as having no recommendation
        assertEquals("dsurround.command.dsversion.unavailable", keyOf(VersionCommandHandler.describe(Optional.empty(), null)));
    }

    @Test
    void resultIsShown() {
        var result = new VersionResult("0.4.6", "dsurround", "Dynamic Surroundings", "c", "m", null, "d", false);

        assertEquals(result.getChatText(), VersionCommandHandler.describe(Optional.of(result), null));
    }
}
