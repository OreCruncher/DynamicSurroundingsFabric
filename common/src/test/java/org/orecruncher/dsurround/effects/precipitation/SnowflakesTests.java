package org.orecruncher.dsurround.effects.precipitation;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for finding the flakes in a snow texture.
 */
public class SnowflakesTests {

    /**
     * A picture drawn as text: '#' is a visible pixel, '.' a clear one
     */
    private static Snowflakes.Flake[] find(String... rows) {
        int width = rows[0].length(), height = rows.length;
        return Snowflakes.find(width, height, (x, y) -> rows[y].charAt(x) == '#' ? 255 : 0).toArray(Snowflakes.Flake[]::new);
    }

    @Test
    void eachGroupOfPixelsIsAFlake() {
        var flakes = find(
                "........",
                "..#.....",
                ".###..#.",
                "..#.....",
                "........",
                "....#.#.",
                ".....#..",
                "....#.#.");
        assertEquals(3, flakes.length, "a cross, a speck, and an X joined at its corners");
        assertEquals(3, flakes[0].size());
        assertEquals(1, flakes[1].size());
        assertEquals(3, flakes[2].size());
    }

    @Test
    void aFlakesCoordinatesFrameIt() {
        // A 3 x 3 cross at (1, 1) in an 8 x 4 picture
        var flake = find(
                "..#.....",
                ".###....",
                "..#.....",
                "........")[0];
        assertEquals(1F / 8F, flake.u0(), 1e-6F);
        assertEquals(4F / 8F, flake.u1(), 1e-6F);
        assertEquals(0F, flake.v0(), 1e-6F);
        assertEquals(3F / 4F, flake.v1(), 1e-6F);
    }

    @Test
    void aFlakeIsFramedSquare() {
        // Three wide and one tall: framed three by three, centred
        var flake = find(
                ".....",
                ".###.",
                ".....")[0];
        assertEquals(3, flake.size());
        assertEquals((flake.u1() - flake.u0()) * 5F, (flake.v1() - flake.v0()) * 3F, 1e-5F);
        assertEquals(0F, flake.v0(), 1e-6F, "one row above the pixels' row");
    }

    @Test
    void largeShapesAreNotFlakes() {
        var row = "#".repeat(Snowflakes.MAX_FLAKE_SIZE + 1) + "..#";
        var flakes = find(row);
        assertEquals(1, flakes.length, "only the speck");
        assertEquals(1, flakes[0].size());
    }

    @Test
    void faintPixelsAreNotPartOfAFlake() {
        var flakes = Snowflakes.find(3, 1, (x, y) -> x == 1 ? Snowflakes.MIN_ALPHA - 1 : 0);
        assertTrue(flakes.isEmpty());
    }

    @Test
    void theGamesSnowHasItsFlakesFound() throws IOException {
        var url = SnowflakesTests.class.getResource("/assets/minecraft/textures/environment/snow.png");
        assertNotNull(url, "the game's snow texture isn't on the test class path");
        var image = ImageIO.read(url);
        var flakes = Snowflakes.find(image.getWidth(), image.getHeight(), (x, y) -> (image.getRGB(x, y) >>> 24) & 0xFF);
        // The game's snow is crosses, rings and Xs three pixels across, and single pixel specks
        assertFalse(flakes.isEmpty());
        assertEquals(Set.of(1, 3), Set.copyOf(flakes.stream().map(Snowflakes.Flake::size).toList()));
        assertEquals(44, flakes.size());
    }
}
