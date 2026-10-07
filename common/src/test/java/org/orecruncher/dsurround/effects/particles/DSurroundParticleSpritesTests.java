package org.orecruncher.dsurround.effects.particles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The particle atlas stitches whatever is in {@code textures/particle}, and a name without an image there draws the
 * missing texture rather than failing. So check the names against the images.
 */
public class DSurroundParticleSpritesTests {

    @Test
    void everySpriteHasATexture() {
        for (var set : DSurroundParticleSprites.ALL) {
            assertFalse(set.ids().isEmpty());
            for (var id : set.ids()) {
                var path = "/assets/%s/textures/particle/%s.png".formatted(id.getNamespace(), id.getPath());
                assertNotNull(DSurroundParticleSpritesTests.class.getResource(path), path);
            }
        }
    }
}
