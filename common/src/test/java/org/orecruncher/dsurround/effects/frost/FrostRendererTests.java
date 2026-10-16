package org.orecruncher.dsurround.effects.frost;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how far the frost has grown across a face: in from the window's edges, thickest at its bottom
 */
public class FrostRendererTests {

    /**
     * The middle face of a large window: its edges as far off as are looked for
     */
    private static FrostFace middleOfAWindow(float shown) {
        float reach = FrostFaces.EDGE_REACH;
        var face = new FrostFace(0, 65, 0, Direction.NORTH, 0F, 0F, 1F, 65F, 66F, -reach, 1F + reach, 65F - reach, 66F + reach, 0, 1F, 0F);
        face.show(shown);
        return face;
    }

    /**
     * A window of a single block
     */
    private static FrostFace smallWindow(float shown) {
        var face = new FrostFace(0, 65, 0, Direction.NORTH, 0F, 0F, 1F, 65F, 66F, 0F, 1F, 65F, 66F, 0, 1F, 0F);
        face.show(shown);
        return face;
    }

    @Test
    void noFrostIsNoFrostAnywhere() {
        assertEquals(0F, FrostRenderer.grown(smallWindow(0F), 0F, 65F));
        assertEquals(0F, FrostRenderer.grown(middleOfAWindow(0F), 0.5F, 65.5F));
    }

    @Test
    void fullFrostCoversAWindow() {
        assertEquals(1F, FrostRenderer.grown(middleOfAWindow(1F), 0.5F, 65.5F), 1.0E-5F);
        assertEquals(1F, FrostRenderer.grown(smallWindow(1F), 0.5F, 65.5F), 1.0E-5F);
    }

    @Test
    void lightFrostIsAtTheEdgesOnly() {
        var middle = middleOfAWindow(0.2F);
        assertEquals(0F, FrostRenderer.grown(middle, 0.5F, 65.5F));
        var small = smallWindow(0.2F);
        float edge = FrostRenderer.grown(small, 0F, 65.5F);
        assertTrue(edge > 0.2F);
        assertTrue(FrostRenderer.grown(small, 0.5F, 65.5F) < edge);
    }

    @Test
    void frostGathersAtTheBottom() {
        var face = smallWindow(0.4F);
        assertTrue(FrostRenderer.grown(face, 0.5F, 65.1F) > FrostRenderer.grown(face, 0.5F, 65.9F));
    }
}
