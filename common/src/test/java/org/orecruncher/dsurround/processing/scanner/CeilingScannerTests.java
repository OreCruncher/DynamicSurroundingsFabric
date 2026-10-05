package org.orecruncher.dsurround.processing.scanner;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how the ceiling scanner turns coverage into "inside".
 */
public class CeilingScannerTests {

    @BeforeAll
    static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private final CeilingScanner scanner = new CeilingScanner(null, null);

    @Test
    void wellCoveredIsInside() {
        this.scanner.setCoverage(0.9F);

        assertTrue(this.scanner.isReallyInside());
        assertEquals(0.9F, this.scanner.getCoverageRatio());
    }

    @Test
    void lightlyCoveredIsOutside() {
        this.scanner.setCoverage(0.5F);

        assertFalse(this.scanner.isReallyInside());
    }

    @Test
    void alwaysOutsideClearsTheCoverage() {
        // Regression: in an always-outside dimension only "inside" was cleared, so the coverage ratio from the last
        // dimension stayed until the player left
        this.scanner.setCoverage(0.9F);

        this.scanner.setCoverage(0F);

        assertFalse(this.scanner.isReallyInside());
        assertEquals(0F, this.scanner.getCoverageRatio());
    }
}
