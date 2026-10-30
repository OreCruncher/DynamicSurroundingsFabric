package org.orecruncher.dsurround.effects.aurora;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AuroraCoverageTests {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 100;

    // Straight to clip space, w 1: a quad's x and y are the screen's, -1 to 1
    private static double flat(float left, float bottom, float right, float top) {
        var coverage = new AuroraCoverage();
        coverage.start(new Matrix4f());
        coverage.quad(left, bottom, 0F, right, bottom, 0F, right, top, 0F, left, top, 0F);
        return coverage.pixels(WIDTH, HEIGHT);
    }

    @Test
    void aQuadOverTheScreenCoversIt() {
        assertEquals(WIDTH * HEIGHT, flat(-1F, -1F, 1F, 1F), 0.01);
    }

    @Test
    void whatIsOffTheScreenIsLeftOut() {
        // Half of it off to the left, and reaching past the top and bottom
        assertEquals(WIDTH * HEIGHT / 2.0, flat(-3F, -2F, 0F, 2F), 0.01);
        assertEquals(0.0, flat(1.5F, -1F, 3F, 1F), 0.01);
    }

    @Test
    void aQuadIsCountedAsOftenAsItIsDrawn() {
        var coverage = new AuroraCoverage();
        coverage.start(new Matrix4f());
        for (int i = 0; i < 3; i++)
            coverage.quad(-1F, -1F, 0F, 1F, -1F, 0F, 1F, 1F, 0F, -1F, 1F, 0F);
        assertEquals(3.0 * WIDTH * HEIGHT, coverage.pixels(WIDTH, HEIGHT), 0.01);
    }

    @Test
    void whatIsBehindTheCameraIsLeftOut() {
        var perspective = new Matrix4f().perspective((float) Math.toRadians(90), 1F, 0.05F, 1000F);
        var coverage = new AuroraCoverage();
        coverage.start(perspective);
        // Facing -Z: a quad 10 behind covers nothing
        coverage.quad(-5F, -5F, 10F, 5F, -5F, 10F, 5F, 5F, 10F, -5F, 5F, 10F);
        assertEquals(0.0, coverage.pixels(WIDTH, HEIGHT), 0.01);
        // One 10 ahead, as wide as the 90 degree view there, fills it
        coverage.quad(-10F, -10F, -10F, 10F, -10F, -10F, 10F, 10F, -10F, -10F, 10F, -10F);
        assertEquals(WIDTH * HEIGHT, coverage.pixels(WIDTH, HEIGHT), 0.5);
    }

    @Test
    void aQuadReachingBehindTheCameraCountsWhatIsAhead() {
        var perspective = new Matrix4f().perspective((float) Math.toRadians(90), 1F, 0.05F, 1000F);
        var coverage = new AuroraCoverage();
        coverage.start(perspective);
        // A floor under the camera from far ahead to far behind: below the horizon, half the view, less what is nearer
        // than its far end ahead; and nothing behind may fold back onto the screen
        coverage.quad(-1000F, -1F, -1000F, 1000F, -1F, -1000F, 1000F, -1F, 1000F, -1000F, -1F, 1000F);
        var pixels = coverage.pixels(WIDTH, HEIGHT);
        assertEquals(WIDTH * HEIGHT / 2.0, pixels, WIDTH * HEIGHT * 0.01);
    }
}
