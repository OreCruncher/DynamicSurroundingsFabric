// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for finding the lit part of the game's firefly texture, drawn as the firefly in the middle of its halo.
 */
public class FireflyDotTests {

    // An alpha of 255 at the given pixels ("x,y"), 0 elsewhere
    private static FireflyDot.Bounds find(int width, int height, Set<String> lit) {
        return FireflyDot.find(width, height, (x, y) -> lit.contains(x + "," + y) ? 255 : 0);
    }

    @Test
    void theGamesOnePixelIsFound() {
        // As the game's texture: 8 by 8, lit in the corner
        assertEquals(new FireflyDot.Bounds(0F, 0F, 0.125F, 0.125F), find(8, 8, Set.of("0,0")));
    }

    @Test
    void aLongerShapeIsSquaredAboutItsMiddle() {
        // Three wide and one high, so the square is three high, centered on the row
        var bounds = find(8, 8, Set.of("2,4", "3,4", "4,4"));
        assertEquals(new FireflyDot.Bounds(2 / 8F, 3 / 8F, 5 / 8F, 6 / 8F), bounds);
        assertEquals(bounds.u1() - bounds.u0(), bounds.v1() - bounds.v0(), 1e-6F);
    }

    @Test
    void theSquareStaysInsideTheTexture() {
        // Three wide along the top edge: centered, the square would start above it
        var bounds = find(8, 8, Set.of("0,0", "1,0", "2,0"));
        assertEquals(new FireflyDot.Bounds(0F, 0F, 3 / 8F, 3 / 8F), bounds);
    }

    @Test
    void anEmptyTextureIsDrawnWhole() {
        assertSame(FireflyDot.Bounds.WHOLE, find(8, 8, Set.of()));
    }

    @Test
    void theGamesTextureIsStillOneLitPixel() throws Exception {
        // If a new version draws its firefly differently, the size the dot is drawn at may need a look
        try (var stream = FireflyDotTests.class.getResourceAsStream("/assets/minecraft/" + FireflyDot.TEXTURE.getPath())) {
            assertNotNull(stream);
            var image = ImageIO.read(stream);
            var bounds = FireflyDot.find(image.getWidth(), image.getHeight(), (x, y) -> image.getRGB(x, y) >>> 24);
            assertEquals(1F / image.getWidth(), bounds.size(), 1e-6F);
        }
    }
}
